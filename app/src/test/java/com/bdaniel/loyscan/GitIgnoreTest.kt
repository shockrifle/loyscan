package com.bdaniel.loyscan

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * The repo will go public, so signing material and release outputs must never be committable (#74). This asks git
 * itself (`git check-ignore`) about made-up paths, so it tests what `git add` would do, not the text of `.gitignore`.
 */
class GitIgnoreTest {

    @Test
    fun signingMaterialAndReleaseOutputs_areIgnored() {
        val secretOrBuilt = listOf(
            "upload.jks",
            "app/upload-keystore.jks",
            "release.keystore",
            "app/release.keystore",
            "keystore.properties",
            "app/keystore.properties",
            "play-service-account.json",
            // A Google Cloud key file keeps its downloaded name (<project>-<key id>.json): kept under secrets/.
            "secrets/loyscan-123456-0a1b2c3d4e5f.json",
            "app-release.aab",
            "app/release/app-release.aab",
            "app-release.apk",
            "app-release.apk.idsig",
            "mapping.txt",
            "app/release/mapping.txt",
            ".env",
        )

        secretOrBuilt.forEach { path ->
            assertWithMessage("git ignores $path").that(isIgnored(path)).isTrue()
        }
    }

    @Test
    fun theProjectsOwnFiles_areNotIgnored() {
        val tracked = listOf(
            "app/build.gradle.kts",
            "gradle.properties",
            "app/proguard-rules.pro",
            "app/src/main/assets/catalogue/stores.json",
            "app/schemas/com.bdaniel.loyscan.data.local.db.AppDatabase/2.json",
            ".github/workflows/tests.yml",
        )

        tracked.forEach { path ->
            assertWithMessage("git doesn't ignore $path").that(isIgnored(path)).isFalse()
        }
    }

    /** `git check-ignore` exits 0 for an ignored path and 1 for one that isn't; `--no-index` ignores what's tracked. */
    private fun isIgnored(path: String): Boolean {
        val process = ProcessBuilder("git", "check-ignore", "--quiet", "--no-index", path)
            .directory(File("..").canonicalFile)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "git check-ignore timed out" }
        return when (val code = process.exitValue()) {
            0 -> true
            1 -> false
            else -> error("git check-ignore $path exited $code: $output")
        }
    }
}
