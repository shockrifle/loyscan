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
            // PKCS12 keystores often carry these extensions (#74 review).
            "upload.p12",
            "app/upload.pfx",
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
            ".env.local",
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

    @Test
    fun androidStudiosProjectFiles_areIgnored() {
        // Machine-local IDE state, rewritten by every Android Studio update (#174). A shared IDE file added later needs
        // its own `!` exception in .gitignore, and moves from here to SHARED_IDE_FILES (wiki/workflows/build-and-run.md).
        val ideState = listOf(
            ".idea/misc.xml",
            ".idea/compiler.xml",
            ".idea/gradle.xml",
            ".idea/deploymentTargetSelector.xml",
            ".idea/inspectionProfiles/Project_Default.xml",
            ".idea/codeStyles/Project.xml",
            ".idea/runConfigurations.xml",
        )

        ideState.forEach { path ->
            assertWithMessage("git ignores $path").that(isIgnored(path)).isTrue()
        }
    }

    @Test
    fun gitTracksNoIdeFile_butTheSharedOnes() {
        // .gitignore doesn't untrack a file that is already committed, nor stop `git add -f`.
        val tracked = git("ls-files", "--", ".idea").lines().filter { it.isNotBlank() }

        assertWithMessage(
            "files under .idea/ that git tracks; a shared one needs a `!` exception in .gitignore and an entry in " +
                "SHARED_IDE_FILES (wiki/workflows/build-and-run.md → IDE settings)",
        ).that(tracked - SHARED_IDE_FILES).isEmpty()
    }

    /** `git check-ignore` exits 0 for an ignored path and 1 for one that isn't; `--no-index` ignores what's tracked. */
    private fun isIgnored(path: String): Boolean {
        val (code, output) = run("check-ignore", "--quiet", "--no-index", path)
        return when (code) {
            0 -> true
            1 -> false
            else -> error("git check-ignore $path exited $code: $output")
        }
    }

    private fun git(vararg args: String): String {
        val (code, output) = run(*args)
        check(code == 0) { "git ${args.joinToString(" ")} exited $code: $output" }
        return output
    }

    private fun run(vararg args: String): Pair<Int, String> {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(File("..").canonicalFile)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "git ${args.first()} timed out" }
        return process.exitValue() to output
    }

    private companion object {
        /** IDE files meant to be shared (none yet); each also has a `!/.idea/...` exception in .gitignore. */
        val SHARED_IDE_FILES = emptySet<String>()
    }
}
