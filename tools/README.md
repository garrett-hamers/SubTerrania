# tools/

CLI utilities for shipping SubTerrania.

## `upload-to-play-console.py`

Pushes the signed AAB, listing copy, icon, feature graphic, and screenshots to Google Play Console via the Play Developer API. Use this **for releases 2+**. The first release must go through the Play Console UI — see [`store-assets/PLAY_CONSOLE_UPLOAD_GUIDE.md`](../store-assets/PLAY_CONSOLE_UPLOAD_GUIDE.md).

### One-time setup (~15 minutes)

You only do this once per developer account.

#### 1. Create a Google Cloud project and enable the Play Developer API

1. Open `https://console.cloud.google.com/projectcreate`.
2. Project name: `SubTerrania-Releases`. Click **Create**.
3. Once selected, open `https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com?project=subterrania-releases` (the `?project=` slug will match what you named it).
4. Click **Enable**.

#### 2. Create a service account

1. Open `https://console.cloud.google.com/iam-admin/serviceaccounts?project=subterrania-releases`.
2. Click **Create service account**.
3. Name: `play-console-uploader`. Click **Create and continue**.
4. Skip the optional role grant (we'll grant Play Console access separately). Click **Done**.
5. Click the new service account's email to open its detail page.
6. Tab **Keys** → **Add key** → **Create new key** → **JSON** → **Create**.
7. A JSON file downloads. Move it to `Q:\Repos\SubTerrania\tools\play-service-account.json`. **Do not commit this file** — it's already gitignored.

#### 3. Grant the service account access in Play Console

1. Open `https://play.google.com/console/u/0/developers/{your-account}/users-and-permissions`.
2. Click **Invite new users**.
3. Email: paste the service account email (looks like `play-console-uploader@subterrania-releases.iam.gserviceaccount.com`). It's also visible in the JSON file under the `client_email` key.
4. Account permissions: tick **Admin (all permissions)** for simplicity, or tick the minimum:
   - **View app information and download bulk reports**
   - **View financial data, orders, and cancellation survey responses** (no — skip)
   - **Manage testing tracks and edit tester lists**
   - **Edit and delete draft apps**
   - **Release to production, exclude devices, and use Play App Signing**
   - **Manage store presence**
5. Either grant on **All apps** or restrict to just **SubTerrania**.
6. Click **Invite user**. There's no email confirmation needed for service accounts.

#### 4. Install Python deps

```powershell
pip install google-api-python-client google-auth
```

#### 5. Verify

```powershell
python Q:\Repos\SubTerrania\tools\upload-to-play-console.py upload-aab `
    --aab Q:\Repos\SubTerrania\app\build\outputs\bundle\release\app-release.aab `
    --track internal `
    --dry-run
```

A successful dry-run prints `Uploaded versionCode=X`, then `[dry-run] Would commit edit … deleting instead.` If you see `Service account JSON not found`, recheck step 2.7. If you see a 403 about insufficient permissions, recheck step 3.

### Day-to-day usage

#### Push everything (most common)

After bumping `versionCode` in `app/build.gradle.kts`, rebuilding the AAB, and updating any of `store-assets/listing.md`, the icon, feature graphic, or screenshots:

```powershell
python tools/upload-to-play-console.py release `
    --track internal `
    --release-notes "1.0.2: Crashlytics added, Nightmare snowball softened."
```

This opens one edit, uploads everything, assigns the new versionCode to the Internal Testing track, and commits.

#### Just the AAB (hotfix flow)

```powershell
python tools/upload-to-play-console.py upload-aab `
    --track internal `
    --release-notes "1.0.3: hotfix for resume-state crash."
```

#### Just the listing copy

After editing `store-assets/listing.md`:

```powershell
python tools/upload-to-play-console.py update-listing
```

#### Just graphics

```powershell
python tools/upload-to-play-console.py upload-graphics
```

### Promoting Internal → Production

The script defaults to the `internal` track. To promote a release that's already passed pre-launch report and your smoke-test:

```powershell
python tools/upload-to-play-console.py upload-aab `
    --track production `
    --release-notes "1.0.2: see CHANGELOG."
```

If you uploaded the same AAB to internal first, this **re-uploads** it to production. (Play accepts re-uploads of the same artifact.) Alternatively, use the Play Console UI's "Promote release" button on the Internal track — it's a single click and avoids a second upload.

### Troubleshooting

| Symptom | Fix |
|--------|-----|
| `403: The caller does not have permission` | Re-check step 3 — the service account email must be invited as a Play Console user with at least "Manage testing tracks" permission for SubTerrania. |
| `400: APK specifies a version code that has already been used.` | Bump `versionCode` in `app/build.gradle.kts` and rebuild the AAB. Each upload needs a fresh `versionCode`. |
| `400: APK signature verification failed` | The AAB was signed with a different upload key than Play Console expects. Use the keystore at `Q:\Repos\SubTerrania\keystore` (alias `subterranea`), creds in `files/NEW_KEYSTORE_CREDENTIALS.txt`. |
| `Service account JSON not found at …\play-service-account.json` | Move the downloaded key from `~\Downloads\` to `Q:\Repos\SubTerrania\tools\play-service-account.json`. |
| `ImportError: googleapiclient` | `pip install google-api-python-client google-auth`. |
| `RuntimeError: Could not find ## App name (max 30 chars)` | Don't reformat the headings in `store-assets/listing.md`; the parser keys off them. |

### Security notes

- `tools/play-service-account.json` is **gitignored**; never commit it.
- The service account has narrow scope (only Play Console). Even so, treat the JSON as you would the keystore: encrypted backup, no email/Slack/Discord transit.
- If the JSON ever leaks, rotate immediately: revoke the key in GCP IAM → Service accounts → Keys, then create a fresh one and replace `tools/play-service-account.json`.
