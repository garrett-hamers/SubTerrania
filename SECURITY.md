# Security & Release Signing

## Keystore policy

**The release keystore must NEVER be committed to this repository.**

The repository's `.gitignore` excludes `keystore`, `*.keystore`, `*.jks`, and
`signing.properties`. Earlier history of this repository contains a `keystore`
file from before this policy was enforced — it has been removed from `HEAD`
but **may still exist in older commits**. See "History remediation" below.

## How signing works

`app/build.gradle.kts` declares the `release` signing config and reads
all sensitive values from environment variables:

| Variable | Purpose | Default |
| --- | --- | --- |
| `KEYSTORE_PATH` | Absolute or relative path to the keystore file | `../keystore` |
| `KEYSTORE_PASSWORD` | Keystore password | _(empty — build will be unsigned)_ |
| `KEY_ALIAS` | Key alias inside the keystore | `key0` |
| `KEY_PASSWORD` | Per-key password | _(empty)_ |

If the env vars are missing, Gradle silently falls back to empty strings;
`bundleRelease` will still run but the produced AAB will not be signed (you'll
see a warning at the end of the build).

## Building a signed release locally

1. Place your keystore file outside the repo (e.g. `~/.android/subterranea-upload.keystore`).
2. Set env vars in the shell that runs Gradle:
   ```
   $env:KEYSTORE_PATH = "C:\Users\you\.android\subterranea-upload.keystore"
   $env:KEYSTORE_PASSWORD = "..."
   $env:KEY_ALIAS = "..."
   $env:KEY_PASSWORD = "..."
   ./gradlew bundleRelease
   ```
3. Verify the AAB at `app/build/outputs/bundle/release/app-release.aab` is signed:
   ```
   jarsigner -verify -verbose -certs app-release.aab
   ```

## History remediation (if a keystore was previously committed)

If a real keystore was committed to this repository at any point, treat it as a
**security incident**:

1. **Rotate the upload key in the Play Console.** Generate a new keystore.
   For Play App Signing, request a key reset:
   <https://support.google.com/googleplay/android-developer/answer/9842756>
2. **Rewrite git history** to remove the file from every commit:
   ```
   pip install git-filter-repo
   git filter-repo --invert-paths --path keystore
   ```
   Then force-push:
   ```
   git push --force-with-lease origin master
   ```
3. **Notify collaborators** to delete and re-clone the repository — the
   force-push will leave their local clones in an inconsistent state.
4. **Revoke and reset the old upload key** with Google.
