# Contributing to Lobbykin

Use JDK 17 with the bundled Gradle 8.10 wrapper. Plugin bytecode targets Java 11.
The default RuneLite version is 1.13.4, matching official Plugin Hub
`runelite.version` checked on 2026-10-30. Check that version again before submission.

```sh
./gradlew --no-daemon clean test jar
./gradlew run
```

Windows: use `gradlew.bat`; `START_LOBBYKIN.bat` is a development helper only.
Override the dependency with `-PruneLiteVersion=latest.release` when testing updates.
Plugin Hub replaces this local build under `build=standard`. There are no added
main-runtime dependencies beyond RuneLite's transitive dependencies. Tests use
JUnit and the RuneLite development client. The JAR is not a standalone program.

See [SUBMISSION.md](SUBMISSION.md) for submission steps and validation limits,
and [VALIDATION.md](VALIDATION.md) for the actual checks completed here.
