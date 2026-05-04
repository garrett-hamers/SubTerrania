#!/usr/bin/env python3
"""
upload-to-play-console.py — Push SubTerrania artifacts to Google Play via the Developer API.

This is the Path 2 automation for *subsequent* releases. For the *first* release you must
follow store-assets/PLAY_CONSOLE_UPLOAD_GUIDE.md (Play Console UI is required for app
creation, content rating, data safety form, and other one-time setup).

Prerequisites (one-time, see tools/README.md for the full walkthrough):
  1. Google Play Developer account already registered and the SubTerrania app already
     created via Play Console UI.
  2. A Google Cloud project linked to the Play Console.
  3. A service account with the "Service Account User" role and Play Console
     "Release manager" permission for the SubTerrania app.
  4. Service account JSON key downloaded to tools/play-service-account.json.
  5. pip install google-api-python-client google-auth

Usage:
    python tools/upload-to-play-console.py upload-aab \
        --aab app/build/outputs/bundle/release/app-release.aab \
        --track internal \
        --release-notes "Fixed XYZ; balanced Z."

    python tools/upload-to-play-console.py update-listing \
        --listing-md store-assets/listing.md

    python tools/upload-to-play-console.py upload-graphics \
        --icon store-assets/icon-512.png \
        --feature store-assets/feature-graphic-1024x500.png \
        --screenshots store-assets/screenshots

    python tools/upload-to-play-console.py release \
        --aab app/build/outputs/bundle/release/app-release.aab \
        --listing-md store-assets/listing.md \
        --track internal

Options that apply to every subcommand:
    --package-name   default: com.atlyn.subterranea
    --service-account default: tools/play-service-account.json
    --dry-run        prepare the edit but do not commit
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PACKAGE = "com.atlyn.subterranea"
DEFAULT_SERVICE_ACCOUNT = REPO_ROOT / "tools" / "play-service-account.json"
DEFAULT_AAB = REPO_ROOT / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"
DEFAULT_LISTING = REPO_ROOT / "store-assets" / "listing.md"
DEFAULT_ICON = REPO_ROOT / "store-assets" / "icon-512.png"
DEFAULT_FEATURE = REPO_ROOT / "store-assets" / "feature-graphic-1024x500.png"
DEFAULT_SCREENSHOTS = REPO_ROOT / "store-assets" / "screenshots"
DEFAULT_LANG = "en-US"
VALID_TRACKS = ("internal", "alpha", "beta", "production")


def _load_client(service_account_path: Path):
    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
    except ImportError:
        sys.exit(
            "Missing dependencies. Run:\n"
            "  pip install google-api-python-client google-auth"
        )
    if not service_account_path.exists():
        sys.exit(
            f"Service account JSON not found at {service_account_path}.\n"
            "See tools/README.md for setup instructions."
        )
    creds = service_account.Credentials.from_service_account_file(
        str(service_account_path),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def _open_edit(service, package_name: str) -> str:
    edit = service.edits().insert(packageName=package_name, body={}).execute()
    return edit["id"]


def _commit_edit(service, package_name: str, edit_id: str, dry_run: bool) -> None:
    if dry_run:
        print(f"[dry-run] Would commit edit {edit_id}; deleting instead.")
        service.edits().delete(packageName=package_name, editId=edit_id).execute()
        return
    service.edits().commit(packageName=package_name, editId=edit_id).execute()
    print(f"Committed edit {edit_id}.")


def _parse_listing_md(path: Path) -> dict[str, str]:
    """Extract title / short / full description from store-assets/listing.md."""
    text = path.read_text(encoding="utf-8")

    def grab_first_code_block_after(header: str) -> str:
        anchor = re.search(rf"^##\s+{re.escape(header)}.*$", text, re.MULTILINE)
        if not anchor:
            raise ValueError(f"Could not find ## {header} in {path}")
        rest = text[anchor.end():]
        m = re.search(r"```[a-zA-Z0-9]*\n(.*?)```", rest, re.DOTALL)
        if not m:
            raise ValueError(f"No code block under ## {header} in {path}")
        return m.group(1).strip()

    return {
        "title": grab_first_code_block_after("App name (max 30 chars)"),
        "shortDescription": grab_first_code_block_after("Short description (max 80 chars)"),
        "fullDescription": grab_first_code_block_after("Full description (max 4000 chars)"),
    }


# --------------------------------------------------------------------------- #
# Subcommands                                                                 #
# --------------------------------------------------------------------------- #

def cmd_upload_aab(args) -> None:
    aab_path = Path(args.aab)
    if not aab_path.exists():
        sys.exit(f"AAB not found: {aab_path}")
    if args.track not in VALID_TRACKS:
        sys.exit(f"--track must be one of {VALID_TRACKS}")

    service = _load_client(Path(args.service_account))
    edit_id = _open_edit(service, args.package_name)
    print(f"Opened edit {edit_id} on {args.package_name}.")

    print(f"Uploading {aab_path} ({aab_path.stat().st_size / 1024 / 1024:.2f} MB)...")
    bundle = service.edits().bundles().upload(
        packageName=args.package_name,
        editId=edit_id,
        media_body=str(aab_path),
        media_mime_type="application/octet-stream",
    ).execute()
    version_code = bundle["versionCode"]
    print(f"Uploaded versionCode={version_code}, sha1={bundle.get('sha1','?')}")

    release_body = {
        "name": args.release_name or f"v{version_code}",
        "versionCodes": [str(version_code)],
        "status": "completed",
    }
    if args.release_notes:
        release_body["releaseNotes"] = [
            {"language": DEFAULT_LANG, "text": args.release_notes}
        ]

    service.edits().tracks().update(
        packageName=args.package_name,
        editId=edit_id,
        track=args.track,
        body={"track": args.track, "releases": [release_body]},
    ).execute()
    print(f"Assigned versionCode={version_code} to {args.track} track.")

    _commit_edit(service, args.package_name, edit_id, args.dry_run)


def cmd_update_listing(args) -> None:
    listing_path = Path(args.listing_md)
    if not listing_path.exists():
        sys.exit(f"listing.md not found: {listing_path}")
    listing = _parse_listing_md(listing_path)

    service = _load_client(Path(args.service_account))
    edit_id = _open_edit(service, args.package_name)
    print(f"Opened edit {edit_id} on {args.package_name}.")

    service.edits().listings().update(
        packageName=args.package_name,
        editId=edit_id,
        language=args.language,
        body=listing,
    ).execute()
    print(f"Updated {args.language} listing: title={listing['title']!r}, "
          f"short={len(listing['shortDescription'])} chars, "
          f"full={len(listing['fullDescription'])} chars.")

    _commit_edit(service, args.package_name, edit_id, args.dry_run)


def cmd_upload_graphics(args) -> None:
    icon = Path(args.icon)
    feature = Path(args.feature)
    screenshots_dir = Path(args.screenshots)
    for p in (icon, feature):
        if not p.exists():
            sys.exit(f"Asset not found: {p}")
    screenshot_files = sorted(screenshots_dir.glob("*.png")) if screenshots_dir.exists() else []
    if not screenshot_files:
        sys.exit(f"No screenshots found under {screenshots_dir}")

    service = _load_client(Path(args.service_account))
    edit_id = _open_edit(service, args.package_name)
    print(f"Opened edit {edit_id} on {args.package_name}.")

    def upload_image(image_path: Path, image_type: str) -> None:
        print(f"  -> uploading {image_path.name} as {image_type}")
        service.edits().images().upload(
            packageName=args.package_name,
            editId=edit_id,
            language=args.language,
            imageType=image_type,
            media_body=str(image_path),
            media_mime_type="image/png",
        ).execute()

    # Wipe existing assets before uploading new ones, so old artwork doesn't linger.
    for image_type in ("icon", "featureGraphic", "phoneScreenshots"):
        try:
            service.edits().images().deleteall(
                packageName=args.package_name,
                editId=edit_id,
                language=args.language,
                imageType=image_type,
            ).execute()
        except Exception as exc:  # pragma: no cover - tolerated
            print(f"  (could not clear {image_type}: {exc})")

    upload_image(icon, "icon")
    upload_image(feature, "featureGraphic")
    for s in screenshot_files:
        upload_image(s, "phoneScreenshots")

    _commit_edit(service, args.package_name, edit_id, args.dry_run)


def cmd_release(args) -> None:
    """Full release: upload AAB, push listing text, push graphics, assign track, commit once."""
    aab_path = Path(args.aab)
    listing_path = Path(args.listing_md)
    icon = Path(args.icon)
    feature = Path(args.feature)
    screenshots_dir = Path(args.screenshots)
    for p in (aab_path, listing_path, icon, feature):
        if not p.exists():
            sys.exit(f"Required asset missing: {p}")
    screenshot_files = sorted(screenshots_dir.glob("*.png"))
    if not screenshot_files:
        sys.exit(f"No screenshots in {screenshots_dir}")
    if args.track not in VALID_TRACKS:
        sys.exit(f"--track must be one of {VALID_TRACKS}")

    listing = _parse_listing_md(listing_path)

    service = _load_client(Path(args.service_account))
    edit_id = _open_edit(service, args.package_name)
    print(f"Opened edit {edit_id} on {args.package_name}.")

    print(f"Uploading AAB {aab_path}...")
    bundle = service.edits().bundles().upload(
        packageName=args.package_name,
        editId=edit_id,
        media_body=str(aab_path),
        media_mime_type="application/octet-stream",
    ).execute()
    version_code = bundle["versionCode"]
    print(f"  versionCode={version_code}")

    print(f"Updating {args.language} listing text...")
    service.edits().listings().update(
        packageName=args.package_name,
        editId=edit_id,
        language=args.language,
        body=listing,
    ).execute()

    print("Refreshing graphics...")
    for image_type in ("icon", "featureGraphic", "phoneScreenshots"):
        try:
            service.edits().images().deleteall(
                packageName=args.package_name,
                editId=edit_id,
                language=args.language,
                imageType=image_type,
            ).execute()
        except Exception as exc:
            print(f"  (could not clear {image_type}: {exc})")
    for path, image_type in [(icon, "icon"), (feature, "featureGraphic")]:
        print(f"  -> {path.name} ({image_type})")
        service.edits().images().upload(
            packageName=args.package_name, editId=edit_id, language=args.language,
            imageType=image_type, media_body=str(path), media_mime_type="image/png",
        ).execute()
    for s in screenshot_files:
        print(f"  -> {s.name} (phoneScreenshots)")
        service.edits().images().upload(
            packageName=args.package_name, editId=edit_id, language=args.language,
            imageType="phoneScreenshots", media_body=str(s), media_mime_type="image/png",
        ).execute()

    release_body = {
        "name": args.release_name or f"v{version_code}",
        "versionCodes": [str(version_code)],
        "status": "completed",
    }
    if args.release_notes:
        release_body["releaseNotes"] = [
            {"language": args.language, "text": args.release_notes}
        ]
    print(f"Assigning versionCode={version_code} to {args.track} track...")
    service.edits().tracks().update(
        packageName=args.package_name,
        editId=edit_id,
        track=args.track,
        body={"track": args.track, "releases": [release_body]},
    ).execute()

    _commit_edit(service, args.package_name, edit_id, args.dry_run)


# --------------------------------------------------------------------------- #
# CLI                                                                         #
# --------------------------------------------------------------------------- #

def _common(p: argparse.ArgumentParser) -> None:
    p.add_argument("--package-name", default=DEFAULT_PACKAGE)
    p.add_argument("--service-account", default=str(DEFAULT_SERVICE_ACCOUNT))
    p.add_argument("--language", default=DEFAULT_LANG)
    p.add_argument("--dry-run", action="store_true",
                   help="prepare the edit but do not commit")


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("upload-aab", help="Upload a single AAB and assign it to a track")
    _common(p)
    p.add_argument("--aab", default=str(DEFAULT_AAB))
    p.add_argument("--track", default="internal", choices=VALID_TRACKS)
    p.add_argument("--release-name", default=None)
    p.add_argument("--release-notes", default=None)
    p.set_defaults(func=cmd_upload_aab)

    p = sub.add_parser("update-listing", help="Push title / short / full description from listing.md")
    _common(p)
    p.add_argument("--listing-md", default=str(DEFAULT_LISTING))
    p.set_defaults(func=cmd_update_listing)

    p = sub.add_parser("upload-graphics", help="Upload icon, feature graphic, and screenshots")
    _common(p)
    p.add_argument("--icon", default=str(DEFAULT_ICON))
    p.add_argument("--feature", default=str(DEFAULT_FEATURE))
    p.add_argument("--screenshots", default=str(DEFAULT_SCREENSHOTS))
    p.set_defaults(func=cmd_upload_graphics)

    p = sub.add_parser("release", help="Full pipeline: AAB + listing + graphics + track in one edit")
    _common(p)
    p.add_argument("--aab", default=str(DEFAULT_AAB))
    p.add_argument("--listing-md", default=str(DEFAULT_LISTING))
    p.add_argument("--icon", default=str(DEFAULT_ICON))
    p.add_argument("--feature", default=str(DEFAULT_FEATURE))
    p.add_argument("--screenshots", default=str(DEFAULT_SCREENSHOTS))
    p.add_argument("--track", default="internal", choices=VALID_TRACKS)
    p.add_argument("--release-name", default=None)
    p.add_argument("--release-notes", default=None)
    p.set_defaults(func=cmd_release)

    args = parser.parse_args(argv)
    args.func(args)


if __name__ == "__main__":
    main()
