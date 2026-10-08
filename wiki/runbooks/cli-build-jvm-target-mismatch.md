# CLI build fails: "Inconsistent JVM Target Compatibility"

**Use when** `./gradlew …` from a terminal fails at `:app:compileDebugKotlin` with:

```
Inconsistent JVM-target compatibility detected for tasks 'compileDebugJavaWithJavac' (21) and 'compileDebugKotlin' (19).
```

while the same project builds fine in Android Studio. Found 2026-10-08.

## Cause

`app/build.gradle.kts` sets Java `sourceCompatibility/targetCompatibility = 21` but sets no Kotlin `jvmTarget` or
`jvmToolchain`. The `kotlinOptions { jvmTarget = "11" }` block was removed in `d0d171a`. Kotlin therefore targets
the JDK that runs Gradle. Android Studio runs Gradle on its bundled JBR 21 (`.idea/gradle.xml` →
`#GRADLE_LOCAL_JAVA_HOME`), so the two targets match there. A terminal uses `JAVA_HOME`/`PATH` — on the main dev
machine that's JDK 19.0.1 — so they don't.

## Steps

```sh
# Git Bash: run Gradle on Android Studio's JDK 21
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug        # verified passing 2026-10-08
```

PowerShell: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug`

## Permanent fix (not applied)

Add `kotlin { jvmToolchain(21) }` to `app/build.gradle.kts` (Gradle can auto-provision the JDK), or set
`org.gradle.java.home` in a machine-local `gradle.properties`. If applied, update this page and
[[build-and-run]].
