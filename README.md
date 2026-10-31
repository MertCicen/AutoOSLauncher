# AutoOS Launcher

> **License:** MIT. You may use, modify, and redistribute this project under the terms of [LICENSE](LICENSE).

AutoOS is a landscape Android launcher designed for car head units. It provides a customizable home screen, an app menu, app shortcuts, GPS speed and trip time, media controls, and a software equalizer for compatible Android audio sessions. All features in this version are free.

![AutoOS home screen](screenshots/home.png)

## Requirements

- Android 6.0 (API 23) or newer
- Landscape display
- Android Studio with Android SDK 36 and JDK 21

The application ID is `com.autoos.launcher`. Changing from the earlier package ID makes this a separate installation; settings from older installs are not migrated automatically.

## Build

Open this folder in Android Studio and run the `app` configuration, or build from the command line:

```text
gradlew.bat :app:assembleDebug
```

On macOS or Linux, use `./gradlew :app:assembleDebug`. Android Studio creates `local.properties` with your local SDK path. This file and build outputs are ignored by Git.

The release build is intentionally unsigned. To distribute an installable release APK, use **Build → Generate Signed Bundle / APK** in Android Studio with a private signing key that you keep outside this repository.

A signed APK for this snapshot is in the package's adjacent `release-apk` folder. Its signing key is kept outside the GitHub repository. See [PRIVACY.md](PRIVACY.md) for current data-handling notes.

## Features and limits

- The four home screen shortcut slots and the Apps menu entries can be assigned to installed apps.
- Launcher Options controls language, wallpaper, logo, vehicle image, home text, and accent color.
- GPS speed requires location permission and a location fix.
- The EQ acts on compatible Android audio sessions. It does not control every head unit's radio, Bluetooth call audio, or proprietary hardware DSP.
- Phone and Device Link behavior depends on apps and capabilities available on the device.

![Apps menu](screenshots/apps.png)
![Apps menu extra shortcuts](screenshots/apps-extra.png)
![Launcher Options](screenshots/launcher-options.png)

## License and asset origin

Source project generated the retained blue-wave wallpaper and menu icons with ChatGPT. Unknown-license font binaries were removed. See [ASSET_ORIGIN.md](ASSET_ORIGIN.md) and [PUBLIC_RELEASE_CHECKLIST.md](PUBLIC_RELEASE_CHECKLIST.md).

This project is released under the [MIT License](LICENSE), which allows reuse, modification, and redistribution. Keep the copyright and license notice with copies or substantial portions. Android and Gradle dependencies retain their own licenses.


## ❤️ Support AutoOS Launcher

AutoOS Launcher is **100% free and open source**, with no ads, subscriptions, or paid features.

If you find this project useful, you can support its development with an optional cryptocurrency donation.

**Accepted tokens:** USDT, USDC, ETH

**Supported networks:**
- Ethereum (ERC-20)
- Polygon
- Arbitrum One
- Base
- BNB Smart Chain

**Donation wallet (EVM):**

`0x98840c954D43F8EFD0D1382C20e2D2aFf75A1acD`

**Important:** Please select the correct network and verify that the token is supported before sending. Do not send BTC, SOL, or TRX using their native networks to this address.

Donations are completely optional. AutoOS Launcher will remain free for everyone.

Thank you for supporting the project! ❤️
