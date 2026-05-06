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

The canonical signing material lives in **Azure Key Vault**. The recommended path
is the Key Vault loader script (next section). The raw env-var path below is kept
as a fallback for emergencies (e.g. when Azure CLI is unavailable).

### Recommended: Loading signing material from Azure Key Vault

The release keystore and its passwords are stored as four secrets in a
purge-protected, RBAC-enabled Azure Key Vault. There is **no plaintext copy on
the build machine**; the keystore file is materialized to `$env:TEMP` for the
duration of a single PowerShell session and deleted on shell exit.

**Vault contents** (4 secrets — names are stable, values rotate):

| Secret name | Contents |
| --- | --- |
| `keystore-base64` | Base64-encoded PKCS12 keystore bytes |
| `keystore-password` | Keystore password |
| `key-password` | Key entry password (PKCS12: same as keystore password) |
| `key-alias` | Key alias inside the keystore |

**Prerequisites** (one-time per machine):

1. Install the Azure CLI: <https://learn.microsoft.com/cli/azure/install-azure-cli>
2. `az login` (browser-based; complete MFA challenge if prompted).
3. Confirm you have the **Key Vault Secrets User** role (or higher, e.g.
   **Key Vault Administrator**) on the SubTerrania vault. The vault uses Azure
   RBAC for the data plane, not legacy access policies.
4. Either set `$env:SUBTERRANEA_VAULT` to the vault name in your PowerShell
   profile, or pass `-VaultName` each time.

**Per-build flow**:

```powershell
# Once per shell:
az login
$env:SUBTERRANEA_VAULT = "kv-subterranea-XXXX"     # fill in your vault name (or set this in your PS profile)
. .\tools\load-signing-from-akv.ps1                # NOTE: dot-sourced

# Build as usual:
.\gradlew :app:bundleRelease

# Optional: explicit cleanup (also runs automatically on shell exit)
Unregister-SubterraneaSigning
```

The dot-source is critical — without it the env vars set inside the script are
discarded when the script's child scope unwinds. The script:

- Reads the four secrets via `az keyvault secret show`.
- Writes a temp PKCS12 to `$env:TEMP\subterranea-<rand>.keystore`.
- Tightens the file's ACL with `icacls` (read access for the current user only).
- Sets `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS` in the
  current PowerShell session.
- Registers `PowerShell.Exiting` to delete the temp file when you close the shell.

**Verify the produced AAB**:

```powershell
keytool -printcert -jarfile app\build\outputs\bundle\release\app-release.aab
```

Expected SHA-1 fingerprint:
`AD:9C:92:46:15:E0:96:27:37:81:37:76:94:5A:39:E4:57:61:29:D8`

**Rotating signing material**:

Because secrets live only in AKV, rotation is in-place — no commits required:

```powershell
# Replace keystore bytes
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("path\to\new.keystore"))
az keyvault secret set --vault-name $env:SUBTERRANEA_VAULT --name keystore-base64 `
    --value $b64 --content-type "application/x-pkcs12;base64"

# Replace passwords
az keyvault secret set --vault-name $env:SUBTERRANEA_VAULT --name keystore-password --value '<new-pwd>'
az keyvault secret set --vault-name $env:SUBTERRANEA_VAULT --name key-password     --value '<new-pwd>'
az keyvault secret set --vault-name $env:SUBTERRANEA_VAULT --name key-alias        --value '<new-alias>'
```

The vault has **purge protection enabled** with a **90-day soft-delete retention**.
A deleted or overwritten secret can be recovered for 90 days; after that, prior
versions are unrecoverable. Always keep an offline encrypted backup of the
keystore bytes before rotating.

### Fallback: raw env-var signing

If Azure CLI is unavailable, you can still sign by exporting the four env vars
directly. Place a keystore file outside the repo (e.g.
`~/.android/subterranea-upload.keystore`) and:

```powershell
$env:KEYSTORE_PATH = "C:\Users\you\.android\subterranea-upload.keystore"
$env:KEYSTORE_PASSWORD = "..."
$env:KEY_ALIAS = "..."
$env:KEY_PASSWORD = "..."
.\gradlew bundleRelease
```

Verify the AAB at `app/build/outputs/bundle/release/app-release.aab` is signed:

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
