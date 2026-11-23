package com.bdaniel.loyscan

import com.bdaniel.loyscan.testing.licenceProblems
import com.bdaniel.loyscan.testing.publicSafeProblems
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * The repository is public (#200). Every file git tracks is checked for what the public-safe rule forbids that a
 * pattern can find: a local path naming a user, an email address. Every adapted third-party file names its licence,
 * and the project's own licence is the GNU GPL v3, or any later version. The rules run on snippets first, then on the
 * files `git ls-files` lists. The snippets are built from parts (`"C:" + SEP + …`) so this file's own text holds no
 * match. wiki/decisions/2026-11-23-public-repository.md
 */
class PublicSafeRepoTest {

    // ---- The rules, on snippets ----

    @Test
    fun aWindowsPathUnderAUsersFolder_isAProblem() {
        val path = "C:" + BACKSLASH + "Users" + BACKSLASH + "alice" + BACKSLASH + "loyscan"

        assertThat(publicSafeProblems("first line\nbuilt in $path today")).containsExactly(
            "line 2: a local path naming a user (alice)",
        )
    }

    @Test
    fun forwardSlashAndGitBashAndUnixHomes_areProblemsToo() {
        val text = listOf(
            "C:" + "/Users/" + "bob/x",
            "/c" + "/Users/" + "carol/x",
            "/Users" + "/dave/x",
            "/home" + "/erin/x",
            "/mnt/c" + "/Users/" + "frank/x",
        ).joinToString("\n")

        assertThat(publicSafeProblems(text)).containsExactly(
            "line 1: a local path naming a user (bob)",
            "line 2: a local path naming a user (carol)",
            "line 3: a local path naming a user (dave)",
            "line 4: a local path naming a user (erin)",
            "line 5: a local path naming a user (frank)",
        ).inOrder()
    }

    @Test
    fun aPlaceholderInPlaceOfTheUser_isFine() {
        val text = listOf(
            "C:" + "/Users/" + "…",
            "/c" + "/Users/" + "<name>/x",
            "/home" + "/\$USER/x",
            "~/.gradle/gradle.properties",
            "https://github.com" + "/users/" + "someone",
        ).joinToString("\n")

        assertThat(publicSafeProblems(text)).isEmpty()
    }

    @Test
    fun anEmailAddress_isAProblem() {
        assertThat(publicSafeProblems("write to jane.doe" + "@" + "mail.test.org please")).containsExactly(
            "line 1: an email address (jane.doe" + "@" + "mail.test.org)",
        )
    }

    @Test
    fun exampleNoreplyAndGitRemoteAddresses_areFine() {
        val text = listOf(
            "someone" + "@" + "example.com",
            "someone" + "@" + "example.hu",
            "12345+bot" + "@" + "users.noreply.github.com",
            "git" + "@" + "github.com:owner/repo.git",
            "@Composable fun A()",
            "implementation(\"group:name@aar\")",
        ).joinToString("\n")

        assertThat(publicSafeProblems(text)).isEmpty()
    }

    @Test
    fun anAdaptedFile_namesALicenceThatExists() {
        val files = mapOf(
            "a/SKILL.md" to "Adapted from X (MIT; see `LICENSE-x`).",
            "b/SKILL.md" to "Adapted from Y (MIT; see `LICENSE-y`).",
            "c/SKILL.md" to "Our own skill.",
            "d/SKILL.md" to "Written here.",
        )

        assertThat(licenceProblems(files, licences = setOf("LICENSE-x"), ownWork = setOf("d/SKILL.md"))).containsExactly(
            "b/SKILL.md names LICENSE-y, which doesn't exist",
            "c/SKILL.md names no licence and isn't listed as own work",
        )
    }

    // ---- The real tree ----

    @Test
    fun theTrackedFiles_holdNoLocalUserPathOrEmailAddress() {
        val problems = TRACKED.filter { it.isText() }.flatMap { path ->
            publicSafeProblems(File(ROOT, path).readText()).map { "$path $it" }
        }

        assertWithMessage("tracked files holding personal details (wiki/CLAUDE.md, Public-safe)").that(problems).isEmpty()
    }

    @Test
    fun theFileList_isGitsAndComplete() {
        listOf("app/build.gradle.kts", "LICENSE", "wiki/CLAUDE.md").forEach {
            assertWithMessage("git ls-files lists $it").that(it in TRACKED).isTrue()
        }
        assertWithMessage("git ls-files lists the ignored local.properties").that("local.properties" in TRACKED).isFalse()
    }

    @Test
    fun everyAdaptedSkillFile_namesItsLicence() {
        // The skills under .claude/skills and the wizards the /wizard skill generates carry its adapted library.
        val adapted = TRACKED
            .filter { it.startsWith(".claude/skills/") || it.isText() && WIZARD_MARK in File(ROOT, it).readText() }
            .filterNot { File(it).name.startsWith("LICENSE") }
            .associateWith { File(ROOT, it).readText() }
        val licences = TRACKED.map { File(it).name }.filter { it.startsWith("LICENSE-") }.toSet()

        assertThat(adapted).isNotEmpty()
        assertThat(licenceProblems(adapted, licences, OWN_WORK)).isEmpty()
    }

    @Test
    fun theLicence_isTheGnuGplVersion3_orAnyLaterVersion() {
        val licence = File(ROOT, "LICENSE").readText().replace("\r\n", "\n")
        val sha256 = MessageDigest.getInstance("SHA-256").digest(licence.toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertWithMessage("SHA-256 of LICENSE against gnu.org's gpl-3.0.txt").that(sha256).isEqualTo(GPL_3_0_SHA256)
        // README's notice, its lines joined by spaces (a Windows checkout may have CRLF).
        assertThat(File(ROOT, "README.md").readLines().joinToString(" ")).contains(
            "either version 3 of the License, or (at your option) any later version",
        )
    }

    private fun String.isText(): Boolean = File(ROOT, this).readBytes().none { it == 0.toByte() }

    private companion object {
        val ROOT: File = File("..").canonicalFile

        val TRACKED: List<String> by lazy {
            val process = ProcessBuilder("git", "ls-files", "-z").directory(ROOT).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "git ls-files failed" }
            output.split('\u0000').filter { it.isNotEmpty() && File(ROOT, it).isFile }
        }

        const val BACKSLASH = "\\"
        /** In parts, so this file doesn't count as a generated wizard. */
        const val WIZARD_MARK = "Generated by the " + "/wizard skill"

        /** SHA-256 of https://www.gnu.org/licenses/gpl-3.0.txt with LF line ends. */
        const val GPL_3_0_SHA256 = "3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986"

        /** Files under .claude/skills written here, not adapted: none yet. */
        val OWN_WORK = emptySet<String>()
    }
}
