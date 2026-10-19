# USB Image Writer — Permanent Signing (v0.2.1+)

## Why previous APKs cannot update in place
Previous GitHub Actions builds used automatically generated Android Debug signing keys. Different ephemeral CI runners can sign with different keys. Android requires the SAME application ID and signing certificate to install an update. Bumping versionCode does NOT bypass a signer mismatch. The old Debug key is not available for reuse, so **one uninstall is required** to transition to the permanent signed release. Thereafter, never regenerate or replace the release key.

## Signing design
- A single RSA 3072-bit PKCS#12 release keystore was generated once.
- The public repo contains ONLY an AES-256-CBC/PBKDF2-encrypted copy, at \`scripts/usb-release-keystore.enc.b64\`. The encrypted copy alone is not sufficient to sign.
- GitHub Actions decrypts it using the repository secret \`USB_SIGNING_PASSPHRASE\` and signs the Release APK with alias \`usb-image-writer\`. The secret value is also the PKCS#12 password.
- No private plaintext keystore or signing passwords should be committed to GitHub.
- A backup of the keystore and passphrase must be kept in a secure location by the repo owner.

## One-time setup (GitHub mobile browser)
1. Open \`https://github.com/P-copi/USB-Image-Writer/settings/secrets/actions\` while signed in as repo owner.
2. Tap **New repository secret**.
3. Name: \`USB_SIGNING_PASSPHRASE\`.
4. Value: copy the **one password line** from the private \`secret.txt\` distributed separately with the signing kit. Do not paste the explanatory lines, spaces, quotes or line breaks.
5. Save secret. Do not share its value in screenshots, messages or issues.
6. Open GitHub repo **Actions → Build APK → Run workflow** on \`main\`.
7. Wait for **Build stable signed release APK** and **Upload stable signed APK** to succeed.
8. Download artifact \`USB-Image-Writer-v0.2.1-SIGNED\` and extract \`app-release.apk\`.
9. Since prior debug versions used different signing keys, uninstall the previous app **once** before installing the stable signed version. Back up any app data needed first. The raw .img files saved in storage outside the app are not part of the app package.
10. Subsequent v0.2.x+ builds will update in place only if signed by the SAME key, package remains \`com.pcopi.usbimager\`, and versionCode increases.

## If Release signing fails
- Secret missing: workflow produces only a Debug artifact for development. This is NOT upgrade-safe.
- "bad decrypt" / invalid keystore: check that the entire passphrase was copied correctly.
- Never generate a replacement keystore casually: a new certificate would force uninstall again.
- Keep the backup PKCS#12 file and password in secure separate storage; losing both makes it impossible to continue this signing identity.
