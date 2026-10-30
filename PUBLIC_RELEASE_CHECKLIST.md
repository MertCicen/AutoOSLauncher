# Public release checklist

**Current status: public release under the MIT License.** The project owner stated that they wrote the older source ZIP and created the retained blue-wave wallpaper and menu icons with ChatGPT. The owner allows others to download, modify, and redistribute the project under the license terms.

The source builds in debug and release modes. The signed release APK was installed on an Android 6 emulator and the Home, Apps, and Launcher Options screens opened. Complete the remaining items before making the repository public.

- [x] The project owner confirmed authorship of the older launcher source ZIP.
- [x] The owner confirmed that the retained blue-wave wallpaper and menu icons were created with ChatGPT. Unknown-license font binaries and unused old launcher images were removed.
- [x] The owner chose the permissive MIT License, allowing use, modification, and redistribution; see `LICENSE`.
- [ ] Review the screenshots and README for accuracy on a real head unit.
- [ ] Test installation, app shortcuts, Launcher Options, GPS, media, and EQ on a real device.
- [x] A private release signing key was created outside the Desktop/GitHub package.
- [ ] Back up the private signing keystore and its password file securely; losing them prevents ordinary APK updates. Their local paths are listed in the adjacent `BASLA-BURADAN.md` file, which is not part of this Git repository.
- [x] Built, signed, verified, and installed the release APK on an Android 6 emulator.
- [x] Created the public GitHub repository, pushed the source, and attached the signed APK to the `v1.27` GitHub Release.

Package ID: `com.autoos.launcher`. The previous package ID will not be upgraded in place.

