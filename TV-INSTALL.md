# TV Player TV 0.9.0

## Fire TV Stick 4K / Android TV

Install `TV-Player-FireTV-0.9.0.apk` from the release assets. This is a complete APK, not a source archive. It supports Fire OS 6+ (Android 7.1+) including the original 4K Stick and later Fire OS 4K models. It does not run on Vega OS / Fire TV Stick 4K Select.

Enable developer options and installation from your chosen installer on the Fire TV. Open the direct APK URL in an installer such as Downloader, then install and launch TV Player from Apps. Alternatively, with Android platform-tools installed:

```sh
adb connect FIRE_TV_IP:5555
adb install -r TV-Player-FireTV-0.9.0.apk
```

Use arrows and OK to select. Menu or holding OK opens EPG/favorite actions for the selected channel. Back returns to the previous screen. The player stays in landscape and accepts media keys. TV is fetched first; movies and series load when selected. The TV app has its own package and encrypted library, separate from the phone app.

## Samsung Tizen (including 2024 models)

Samsung Tizen cannot install the Fire TV APK. The separate app is in `samsung/`; it uses Samsung AVPlay for TS/HLS/VOD and the TV remote, and supports Xtream/M3U, provider bouquets, sorting, search, favorites, seasons and API EPG. Samsung 2024 is the requested target; widget minimum is Tizen 4.0. Physical TV media/codec compatibility still needs device verification.

`TV-Player-Samsung-0.9.0-unsigned.wgt` is an **unsigned development widget, not ready for installation**. No Samsung signing credentials or TV DUID were available when building it. A Samsung author/distributor certificate profile that permits the target TV is required. Do not rename an APK or source ZIP to WGT.

1. Install Tizen Studio with Samsung TV Extension and Samsung Certificate Extension on a computer.
2. Enable Developer Mode on the TV (Apps panel, enter 12345), set the computer IP and restart the TV as requested by Samsung.
3. Connect the TV in Device Manager. Create a **Samsung TV certificate profile** using your Samsung developer account and add this TV's DUID. Permit the device to install applications.
4. Connect with `sdb connect TV_IP:26101`, then run `sdb devices` to find the device/target name.
5. From this repository, run:

```sh
bash scripts/install-samsung.sh YOUR_SAMSUNG_PROFILE TV_TARGET_NAME
```

The script packages and signs the supplied source with the local profile, verifies the author/distributor signatures and installs it using Tizen CLI. The signed copy is saved as `dist/TV-Player-Samsung-0.9.0-signed.wgt`. Private certificates and their passwords stay on your computer. These steps do not publish the app to Samsung's store.

If using Tizen Studio UI instead, import the `samsung` directory, select the Samsung certificate profile and run it as a Tizen Web Application on the connected TV.

### Samsung behavior and validation limits

- Native playback uses Samsung AVPlay; browser tests use an AVPlay API stub to check lifecycle, remote actions and source URLs. They do not validate actual video decoding on a Samsung TV.
- EPG is requested from the provider API. The Samsung web app cannot observe the MPEG-TS bytes being consumed inside AVPlay; the Android/Fire TV EIT observer is not claimed as available on Samsung.
- Source parsing and large-library JSON work run in workers, with a small paginated DOM. Tests cover a streamed M3U over 30 MiB / 60,000 entries.
- Remembered credentials, library and favorites are AES-GCM encrypted in IndexedDB using a non-extractable WebCrypto key. If the TV cannot provide that storage, the source works for the current session and the app reports that it could not persist it.
- No playlists or channels are included. Playback, EPG and available codecs depend on the supplied source and TV capabilities.

Samsung references: [TV connection](https://developer.samsung.com/smarttv/develop/getting-started/using-sdk/tv-device.html), [certificates](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/creating-certificates.html), [Tizen CLI](https://developer.samsung.com/smarttv/develop/getting-started/using-sdk/command-line-interface.html), [AVPlay](https://developer.samsung.com/smarttv/develop/guides/multimedia/media-playback/using-avplay.html).

## Developer verification

```sh
gradle :app:testDebugUnitTest :tv:lintDebug :tv:assembleDebug :tv:assembleDebugAndroidTest
node samsung/tests/core.test.cjs
node samsung/tests/browser.cjs
python3 scripts/package-samsung.py
```

The browser test requires Playwright and Chromium. The TV workflow verifies the APK signature and runs installation/remote tests on Android API 25; physical Fire TV and Samsung tests require the devices.
