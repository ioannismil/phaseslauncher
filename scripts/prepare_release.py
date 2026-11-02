#!/usr/bin/env python3
"""Turns what's under [Unreleased] in CHANGELOG.md into a release.

    python3 scripts/prepare_release.py patch|minor|major|1.2.3 [--date YYYY-MM-DD]

- gradle.properties: versionName and versionCode (major * 10000 + minor * 100 + patch).
- CHANGELOG.md: the [Unreleased] entries become "## [1.2.3] - date" under a fresh, empty
  [Unreleased], and the comparison links at the bottom follow.
- fastlane/metadata/android/en-US/changelogs/<versionCode>.txt: the same entries as plain
  text for the store listings (500 characters at most).

Prints the new version. The Release workflow runs this, then commits, tags and publishes.
"""
import argparse
import datetime
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PROPERTIES = ROOT / "gradle.properties"
CHANGELOG = ROOT / "CHANGELOG.md"
STORE_CHANGELOGS = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "changelogs"
STORE_LIMIT = 500


def read(path):
    # Keep whatever line endings the file has (a Windows checkout uses CRLF).
    text = path.read_bytes().decode("utf-8")
    newline = "\r\n" if "\r\n" in text else "\n"
    return text.replace("\r\n", "\n"), newline


def write(path, text, newline="\n"):
    path.write_bytes(text.replace("\n", newline).encode("utf-8"))


def next_version(current, bump):
    if re.fullmatch(r"\d+\.\d+\.\d+", bump):
        return bump
    major, minor, patch = map(int, current.split("."))
    if bump == "major":
        return f"{major + 1}.0.0"
    if bump == "minor":
        return f"{major}.{minor + 1}.0"
    if bump == "patch":
        return f"{major}.{minor}.{patch + 1}"
    sys.exit(f"Unknown bump '{bump}': use patch, minor, major or a version like 1.2.3")


def version_code(version):
    major, minor, patch = map(int, version.split("."))
    return major * 10000 + minor * 100 + patch


def store_text(entries):
    """The changelog entries as plain text: no headings or emphasis, bullets as •."""
    lines = []
    for line in entries.splitlines():
        if line.startswith("#"):
            continue
        line = re.sub(r"\*\*(.+?)\*\*", r"\1", line)
        line = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"\1", line)
        line = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", line)
        line = re.sub(r"^\s*[-*] ", "• ", line)
        if line.strip() or (lines and lines[-1]):
            lines.append(line.rstrip())
    text = "\n".join(lines).strip()
    if len(text) <= STORE_LIMIT:
        return text
    # Cut at the last whole line that fits.
    cut = text[: STORE_LIMIT - 1].rsplit("\n", 1)[0]
    return cut.rstrip() + "\n…"


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("bump", help="patch, minor, major, or an exact version like 1.2.3")
    parser.add_argument("--date", default=datetime.date.today().isoformat())
    args = parser.parse_args()

    properties, properties_newline = read(PROPERTIES)
    current = re.search(r"^versionName=(\S+)$", properties, re.M).group(1)
    version = next_version(current, args.bump)
    code = version_code(version)
    if code <= version_code(current):
        sys.exit(f"{version} isn't after the current version {current}")

    changelog, changelog_newline = read(CHANGELOG)
    match = re.search(r"^## \[Unreleased\]\n(.*?)(?=^## \[|^\[Unreleased\]:|\Z)", changelog, re.M | re.S)
    if not match:
        sys.exit("CHANGELOG.md has no '## [Unreleased]' section")
    entries = match.group(1).strip()
    if not entries:
        sys.exit("Nothing under [Unreleased] in CHANGELOG.md: add what changed first")

    released = f"## [Unreleased]\n\n## [{version}] - {args.date}\n\n{entries}\n\n"
    changelog = changelog[: match.start()] + released + changelog[match.end():]
    unreleased_link = re.search(r"^\[Unreleased\]: (\S+)/compare/v\S+\.\.\.HEAD$", changelog, re.M)
    if unreleased_link:
        base = unreleased_link.group(1)
        changelog = (
            changelog[: unreleased_link.start()]
            + f"[Unreleased]: {base}/compare/v{version}...HEAD\n[{version}]: {base}/compare/v{current}...v{version}"
            + changelog[unreleased_link.end():]
        )

    properties = re.sub(r"^versionName=.*$", f"versionName={version}", properties, flags=re.M)
    properties = re.sub(r"^versionCode=.*$", f"versionCode={code}", properties, flags=re.M)

    write(PROPERTIES, properties, properties_newline)
    write(CHANGELOG, changelog, changelog_newline)
    STORE_CHANGELOGS.mkdir(parents=True, exist_ok=True)
    write(STORE_CHANGELOGS / f"{code}.txt", store_text(entries) + "\n")
    print(version)


if __name__ == "__main__":
    main()
