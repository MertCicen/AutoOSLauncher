package com.autoos.launcher

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.os.Handler
import android.os.HandlerThread
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class SpeedLimitMatcher(
    private val context: Context,
    private val onMatch: (Match) -> Unit
) {
    data class Match(
        val roadName: String,
        val speed: Int,
        val estimated: Boolean,
        val distanceMeters: Double
    )

    private val workerThread = HandlerThread("SpeedLimitMatcher").apply { start() }
    private val worker = Handler(workerThread.looper)
    private val main = Handler(context.mainLooper)
    private var database: SQLiteDatabase? = null
    private var databaseKey: String? = null
    private var lastLookupAt = 0L
    private var pending = false

    fun lookup(location: Location) {
        val now = System.currentTimeMillis()
        if (pending || now - lastLookupAt < LOOKUP_INTERVAL_MS) return

        val lat = location.latitude
        val lon = location.longitude
        pending = true
        lastLookupAt = now
        DebugLogStore.add("SPEED", "lookup lat=${"%.5f".format(lat)} lon=${"%.5f".format(lon)}")

        worker.post {
            val match = try {
                findMatch(lat, lon)
            } catch (e: Exception) {
                DebugLogStore.add("SPEED", "lookup error ${e.javaClass.simpleName}: ${e.message}")
                null
            } finally {
                pending = false
            }
            if (match != null) {
                DebugLogStore.add(
                    "SPEED",
                    "match ${match.speed} ${match.roadName} est=${match.estimated} dist=${"%.1f".format(match.distanceMeters)}m"
                )
                main.post { onMatch(match) }
            }
        }
    }

    fun close() {
        worker.post {
            database?.close()
            database = null
            workerThread.quitSafely()
        }
    }

    private fun findMatch(lat: Double, lon: Double): Match? {
        val db = openDatabase() ?: return null
        val radiusDeg = SEARCH_RADIUS_METERS / 111_320.0
        var candidates = 0
        val cursor = db.rawQuery(
            """
            SELECT name, display_speed, estimated, geometry
            FROM roads
            WHERE min_lat <= ? AND max_lat >= ?
              AND min_lon <= ? AND max_lon >= ?
              AND geometry IS NOT NULL
            LIMIT 140
            """.trimIndent(),
            arrayOf(
                (lat + radiusDeg).toString(),
                (lat - radiusDeg).toString(),
                (lon + radiusDeg).toString(),
                (lon - radiusDeg).toString()
            )
        )

        cursor.use {
            var best: Match? = null
            while (it.moveToNext()) {
                candidates++
                val name = it.getString(0).orEmpty()
                val speed = it.getInt(1)
                val estimated = it.getInt(2) == 1
                val geometry = it.getBlob(3) ?: continue
                val distance = minDistanceToGeometry(lat, lon, decodeGeometry(geometry))
                if (distance <= MAX_ACCEPT_DISTANCE_METERS &&
                    (best == null || distance < best.distanceMeters)
                ) {
                    best = Match(name, speed, estimated, distance)
                }
            }
            if (best == null) {
                DebugLogStore.add("SPEED", "no match candidates=$candidates")
            }
            return best
        }
    }

    private fun openDatabase(): SQLiteDatabase? {
        val key = selectedDatabaseKey()
        if (database != null && databaseKey == key) return database

        database?.close()
        database = null
        databaseKey = key

        val dbFile = resolveDatabaseFile(key) ?: return null
        if (!dbFile.exists() || dbFile.length() <= 0L) {
            DebugLogStore.add("SPEED", "db missing $key")
            return null
        }

        database = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        DebugLogStore.add("SPEED", "db open ${dbFile.absolutePath} ${dbFile.length() / 1024 / 1024}MB")
        return database
    }

    private fun selectedDatabaseKey(): String {
        return DEFAULT_DB_PATH
    }

    private fun resolveDatabaseFile(key: String): File? {
        val preferred = File(key)
        if (preferred.exists()) return preferred

        DOWNLOAD_DB_FALLBACKS.firstOrNull { it.exists() }?.let { fallback ->
            DebugLogStore.add("SPEED", "db fallback ${fallback.absolutePath}")
            return fallback
        }
        return preferred
    }

    private fun decodeGeometry(bytes: ByteArray): List<Pair<Double, Double>> {
        val reader = VarintReader(bytes)
        val count = reader.readUnsigned().toInt()
        val points = ArrayList<Pair<Double, Double>>(count)
        var lat = 0L
        var lon = 0L
        repeat(count) {
            lat += reader.readSigned()
            lon += reader.readSigned()
            points.add(lat / 1_000_000.0 to lon / 1_000_000.0)
        }
        return points
    }

    private fun minDistanceToGeometry(lat: Double, lon: Double, points: List<Pair<Double, Double>>): Double {
        if (points.isEmpty()) return Double.MAX_VALUE
        if (points.size == 1) return distanceMeters(lat, lon, points[0].first, points[0].second)

        var best = Double.MAX_VALUE
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            best = min(best, distanceToSegmentMeters(lat, lon, a.first, a.second, b.first, b.second))
        }
        return best
    }

    private fun distanceToSegmentMeters(
        lat: Double,
        lon: Double,
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val metersPerLon = 111_320.0 * cos(Math.toRadians(lat))
        val px = lon * metersPerLon
        val py = lat * 111_320.0
        val ax = lon1 * metersPerLon
        val ay = lat1 * 111_320.0
        val bx = lon2 * metersPerLon
        val by = lat2 * 111_320.0
        val dx = bx - ax
        val dy = by - ay
        val length2 = dx * dx + dy * dy
        if (length2 <= 0.0001) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))

        val t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length2))
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val metersPerLon = 111_320.0 * cos(Math.toRadians((lat1 + lat2) / 2.0))
        val dx = (lon2 - lon1) * metersPerLon
        val dy = (lat2 - lat1) * 111_320.0
        return sqrt(dx * dx + dy * dy)
    }

    private class VarintReader(private val bytes: ByteArray) {
        private var index = 0

        fun readUnsigned(): Long {
            var shift = 0
            var result = 0L
            while (index < bytes.size) {
                val b = bytes[index++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
            }
            return result
        }

        fun readSigned(): Long {
            val value = readUnsigned()
            return (value ushr 1) xor -(value and 1L)
        }
    }

    companion object {
        private const val LOOKUP_INTERVAL_MS = 2_000L
        private const val SEARCH_RADIUS_METERS = 120.0
        private const val MAX_ACCEPT_DISTANCE_METERS = 65.0
        private const val DEFAULT_DB_PATH = "/storage/emulated/0/Download/speed_limits.db"
        private val DOWNLOAD_DB_FALLBACKS = listOf(
            File("/storage/emulated/0/download/speed_limits.db"),
            File("/sdcard/Download/speed_limits.db"),
            File("/sdcard/download/speed_limits.db")
        )
    }
}
