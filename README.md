# Pocket Pilots Android Notifier

Android app that pairs with the notification API, registers an FCM device token, and displays flight progress notifications.

## Related Repositories

- [Notification API](https://github.com/null-noop/pilots-notifier-server)
- [Chrome extension](https://github.com/null-noop/pilots-notifier-extension)

## Compatibility

Release versions in the `vX.X.0` format are compatible when the API, extension, and Android app share the same version.

## Firebase Setup

Place the Firebase project's Android config at `app/google-services.json`. The Android app ID is `com.nullnoop.pocket_pilots_notif`. For actual push delivery, the notification API also needs a Firebase Admin service-account key from the same Firebase project.

## Run Locally

With the required Firebase files in place and an emulator or device connected, start the API and install the debug app from the server repository:

```bash
../pilots-notifier-server/run-local.sh
```

The runner forwards the API to the Android device at `127.0.0.1:8000`. Configure the extension to use `http://127.0.0.1:8000` and enter the pairing code shown in the app.

## Release Build

Copy `.env.example` to `.env`, then fill in the signing values used by the GitHub Actions release workflow. The local env file is ignored by Git.

```bash
./build-release-local.sh
```

The script runs `assembleRelease`. `KEYSTORE_BASE64` and `GOOGLE_SERVICES_BASE64` can provide the encoded files; alternatively, set `KEYSTORE_PATH` and place `app/google-services.json` directly. Never commit signing secrets or Firebase credentials.
