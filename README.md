# Taala – native Android keystore studio

Java and XML only (no web code). Generates RSA keystores on the device and gives you the same
outputs as the Taala web app: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD,
SHA-1 and SHA-256, plus keystore.jks, keystore.bks, keystore.p12, certificate.pem,
private-key.pem, key.txt and a full zip.

## Build the APK on GitHub

1. Create a new GitHub repository and push the contents of this folder to the `main` branch.
2. Open the **Actions** tab. The workflow **Build Taala APK** runs on every push
   (or press **Run workflow**).
3. When it finishes, open the run and download the artifact **Taala-debug-apk**.
   Unzip it and install `app-debug.apk` on your phone.

### Optional: signed release APK
Add these repository secrets (Taala shows all four for any keystore it makes):
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
The workflow then also uploads **Taala-release-apk**, signed with your keystore.

## Project facts
- minSdk 26 (Android 8.0), targetSdk 34, Java 17, Gradle 8.7, Android Gradle Plugin 8.5.2
- Only dependency: BouncyCastle (`bcprov`, `bcpkix`) for certificates, PKCS12 and BKS
- Data is stored in a private SQLite database on the device. Backups use the same JSON format as the web app.
- Android has no JKS support, so use `keystore.bks` or `keystore.p12` inside Android apps.
  `keystore.jks` is for Gradle, Android Studio and keytool on a computer.

## Licences
BouncyCastle (MIT). Fonts in `app/src/main/assets/fonts`: Fraunces, Bricolage Grotesque and
JetBrains Mono, all under the SIL Open Font License 1.1.
