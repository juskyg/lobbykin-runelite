#!/usr/bin/env python3
"""Offline packaging checks only; not a compiler or approval/security audit."""
from pathlib import Path
import re
import struct

root = Path(__file__).resolve().parents[1]
required = ["LICENSE", "LICENSE-Gradle.txt", "NOTICE.md", "README.md", "PRIVACY.md",
            "SECURITY.md", "SUBMISSION.md", "VALIDATION.md", "gradlew", "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
            "icon.png", ".github/workflows/build.yml"]
for name in required:
    assert (root / name).is_file(), f"Missing: {name}"
props = dict(line.split("=", 1) for line in (root / "runelite-plugin.properties").read_text().splitlines()
             if "=" in line and not line.startswith("#"))
assert props["displayName"] == "Lobbykin"
assert props["build"] == "standard"
assert props["plugins"] == "com.lobbykin.LobbykinPlugin"
version = re.search(r"^version = '([^']+)'", (root / "build.gradle").read_text(), re.M)[1]
assert version == props["version"], "Build/manifest version mismatch"
for name in ["BrowserPairing.java", "SessionClient.java"]:
    text = (root / "src/main/java/com/lobbykin" / name).read_text()
    assert f"Lobbykin-RuneLite/{version}" in text, f"Stale user agent: {name}"
image = (root / "icon.png").read_bytes()
assert image[:8] == b"\x89PNG\r\n\x1a\n"
width, height = struct.unpack(">II", image[16:24])
assert 0 < width <= 48 and 0 < height <= 72, "Catalog icon too large"
assert image == (root / "src/main/resources/com/lobbykin/icon.png").read_bytes()
for path in root.rglob("*.jar"):
    if any(part in {".gradle", "build", ".git"} for part in path.relative_to(root).parts):
        continue
    assert path.relative_to(root).as_posix() == "gradle/wrapper/gradle-wrapper.jar", f"Prebuilt artifact: {path}"
sources = list((root / "src/main/java").rglob("*.java"))
destinations = set()
for path in sources:
    text = path.read_text()
    # Literal production endpoints only; localhost test overrides are not scanned.
    destinations.update(re.findall(r'https://[^"\s]+', text))
    assert not re.search(r'lk_rl_[0-9a-f]{64}', text), f"Hardcoded credential: {path}"
assert all(url.startswith("https://lobbykin.com/") for url in destinations), destinations
print(f"PASS: {version}; {len(sources)} main Java files; {width}x{height} catalog icon")
print("PASS: required files, version consistency, source-only packaging, literal endpoint/token checks")
print("NOT CHECKED: full compilation, runtime security, production API or live game behavior")
