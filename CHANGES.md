# 0.9.4-rc1 — Plugin Hub preparation

Based on 0.9.3. Adds public-repository documentation, BSD-2-Clause license,
Gradle license notice, root catalog icon, CI and offline packaging checks.
Synchronizes metadata and API User-Agent version strings. Removes the old
prebuilt release JAR. No new raid or world-hop behavior is introduced.

This source candidate still needs a full build and live acceptance before
submission; it is not an approved Plugin Hub release.

## Plugin Hub review compatibility fix

The official packager rejected direct Gson construction in production code.
Use RuneLite's injected Gson for browser pairing and session response parsing.
Tests provide their parser explicitly; offline preflight rejects direct Gson
construction in main sources. No raid detection or authorization rules change.
