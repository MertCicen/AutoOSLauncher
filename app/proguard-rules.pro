# =============================================
# AutoOS Launcher — ProGuard / R8 Kuralları
# =============================================

# Mapsforge (eğer native harita kullanıyorsanız açın)
# -keep class org.mapsforge.** { *; }

# WebView ile kullanılan JavaScript arayüzleri (şu an yok, ileride eklenirse):
# -keepclassmembers class com.autoos.launcher.** {
#     @android.webkit.JavascriptInterface <methods>;
# }

# Standart Android kütüphaneleri
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# R8 full mode — daha agresif optimizasyon (isteğe bağlı)
# -assumevalues class android.os.Build$VERSION {
#     int SDK_INT return 23..36;
# }
