package com.bdaniel.loyscan

import com.bdaniel.loyscan.testing.lineCitations
import com.bdaniel.loyscan.testing.repoPaths
import com.bdaniel.loyscan.testing.testMembers
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * The wiki names code that moves on without it (#222). No wiki page cites a line number, every repo path a page names
 * must be a tracked file or folder, and every test method or property it names must be declared by that test class. The rules run on snippets first, then
 * on the wiki pages `git ls-files` lists, except the append-only `_log.md` and the raw `assets/`. A wiki-only PR runs
 * this test on CI too. wiki/CLAUDE.md, "How to write pages"
 */
class WikiReferencesTest {

    // ---- What a page names, on snippets ----

    @Test
    fun aRepoPath_isNamed_withItsLine() {
        val text = "first line\nsee `app/src/main/java/com/bdaniel/loyscan/MainActivity.kt` and wiki/index.md."

        assertThat(repoPaths(text)).containsExactly(
            "line 2: app/src/main/java/com/bdaniel/loyscan/MainActivity.kt",
            "line 2: wiki/index.md",
        ).inOrder()
    }

    @Test
    fun everyTopFolder_counts() {
        val text = "gradle/libs.versions.toml play/listing.md .github/workflows/tests.yml .claude/commands/x.md"

        assertThat(repoPaths(text)).containsExactly(
            "line 1: gradle/libs.versions.toml",
            "line 1: play/listing.md",
            "line 1: .github/workflows/tests.yml",
            "line 1: .claude/commands/x.md",
        ).inOrder()
    }

    @Test
    fun pathsInsideOtherPaths_placeholders_prefixesAndBuildOutputs_areNotNamed() {
        val text = listOf(
            "`~/.gradle/gradle.properties` and ~/.gradle/wrapper/dists",
            "uses: gradle/actions/setup-gradle@v4, see github.com/gradle/gradle",
            "app/schemas/com.example.AppDatabase/<version>.json",
            "a `https://github.com/shockrifle/loyscan/blob/master/wiki/...` URL, wiki/… and app/src/*/res",
            "app/src/main/res/drawable/ic_launcher_",
            "app/build/reports/kover/htmlDebug/index.html",
        ).joinToString("\n")

        assertThat(repoPaths(text)).isEmpty()
    }

    @Test
    fun aFolder_isNamedWithoutItsTrailingPunctuation() {
        assertThat(repoPaths("Under app/schemas/, then wiki/decisions.")).containsExactly(
            "line 1: app/schemas/",
            "line 1: wiki/decisions",
        ).inOrder()
    }

    @Test
    fun aTestMember_isNamed_inBackticks() {
        val text = "first\n`DetailScreenTest.emptyStoreColor_doesNotCrash` and `ListScreenTest.grid()` pass"

        assertThat(testMembers(text)).containsExactly(
            "line 2: DetailScreenTest.emptyStoreColor_doesNotCrash",
            "line 2: ListScreenTest.grid",
        ).inOrder()
    }

    @Test
    fun aTestClassAlone_orANonTestClass_isNotATestMember() {
        val text = "`DetailScreenTest` runs `ListViewModel.search` and `emptyStoreColor_doesNotCrash`"

        assertThat(testMembers(text)).isEmpty()
    }

    @Test
    fun aFileWithALineOrARange_isALineCitation() {
        val text = listOf(
            "see `app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:90-99`",
            "(`Navigation.kt:40-41`) and data/local/db/dao/LoyaltyCardDao.kt:16-22, 80-88",
            "app/build.gradle.kts:3, proguard-rules.pro:12 and .github/workflows/tests.yml:88",
        ).joinToString("\n")

        assertThat(lineCitations(text)).containsExactly(
            "line 1: app/src/main/java/com/bdaniel/loyscan/MainActivity.kt:90-99",
            "line 2: Navigation.kt:40-41",
            "line 2: data/local/db/dao/LoyaltyCardDao.kt:16-22",
            "line 3: app/build.gradle.kts:3",
            "line 3: proguard-rules.pro:12",
            "line 3: .github/workflows/tests.yml:88",
        ).inOrder()
    }

    @Test
    fun timesShasIssuesPortsAndSymbols_areNotLineCitations() {
        val text = listOf(
            "at 12:30, commit `45a33e1`, #222, a `]L0` prefix, https://example.com:8080/x and github.com/o/r#L5",
            "`AppNavigation` in `app/src/main/java/com/bdaniel/loyscan/MainActivity.kt`, `Detail.cardId`",
            "`git show 9fc953d:app/src/main/java/com/bdaniel/loyscan/DetailViewModel.kt`",
        ).joinToString("\n")

        assertThat(lineCitations(text)).isEmpty()
    }

    // ---- The rules, on the wiki ----

    @Test
    fun noWikiPage_citesALineNumber() {
        val problems = WIKI_PAGES.flatMap { page -> lineCitations(File(ROOT, page).readText()).map { "$page $it" } }

        assertWithMessage("line numbers the wiki cites; cite the path and the symbol (wiki/CLAUDE.md, provenance)")
            .that(problems).isEmpty()
    }

    @Test
    fun everyRepoPathTheWikiNames_isTracked() {
        val problems = WIKI_PAGES.flatMap { page ->
            repoPaths(File(ROOT, page).readText())
                .filterNot { it.substringAfter(": ").removeSuffix("/").isTracked() }
                .filterNot { it.substringAfter(": ") in REMOVED || it.substringAfter(": ") in NOT_PATHS }
                .map { "$page $it" }
        }

        assertWithMessage("paths the wiki names that git doesn't track (wiki/CLAUDE.md, provenance)")
            .that(problems).isEmpty()
    }

    @Test
    fun everyTestMemberTheWikiNames_isDeclared() {
        val problems = WIKI_PAGES.flatMap { page ->
            testMembers(File(ROOT, page).readText())
                .filterNot { it.substringAfter(": ").isDeclaredTest() }
                .map { "$page $it" }
        }

        assertWithMessage("test members the wiki names that no test declares (wiki/CLAUDE.md, provenance)")
            .that(problems).isEmpty()
    }

    @Test
    fun theCheckedPages_areTheWikisAndComplete() {
        assertThat(WIKI_PAGES).containsAtLeast("wiki/CLAUDE.md", "wiki/index.md", "wiki/architecture.md")
        assertThat(WIKI_PAGES.filter { it == "wiki/_log.md" || it.startsWith("wiki/assets/") }).isEmpty()
    }

    @Test
    fun theRepoRules_seeTheRepo() {
        assertThat("app/src/main/java/com/bdaniel/loyscan/MainActivity.kt".isTracked()).isTrue()
        assertThat("app/schemas".isTracked()).isTrue()
        assertThat("app/src/main/java/com/bdaniel/loyscan/Gone.kt".isTracked()).isFalse()
        assertThat("PublicSafeRepoTest.theFileList_isGitsAndComplete".isDeclaredTest()).isTrue()
        assertThat("PublicSafeRepoTest.OWN_WORK".isDeclaredTest()).isTrue()
        assertThat("PublicSafeRepoTest.noSuchTest".isDeclaredTest()).isFalse()
        assertThat("NoSuchTest.anything".isDeclaredTest()).isFalse()
    }

    private fun String.isTracked(): Boolean = this in TRACKED_SET || TRACKED.any { it.startsWith("$this/") }

    private fun String.isDeclaredTest(): Boolean {
        val (testClass, member) = split('.', limit = 2)
        return TRACKED
            .filter { it.startsWith("app/src/") && File(it).name == "$testClass.kt" }
            .map { File(ROOT, it).readText() }
            .any { text -> listOf("fun $member(", "fun `$member`(", "val $member ", "var $member ").any { it in text } }
    }

    private companion object {
        val ROOT: File = File("..").canonicalFile

        val TRACKED: List<String> by lazy {
            val process = ProcessBuilder("git", "ls-files", "-z").directory(ROOT).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "git ls-files failed" }
            output.split('\u0000').filter { it.isNotEmpty() && File(ROOT, it).isFile }
        }
        val TRACKED_SET: Set<String> by lazy { TRACKED.toSet() }

        val WIKI_PAGES: List<String> by lazy {
            TRACKED.filter { it.startsWith("wiki/") && it.endsWith(".md") }
                .filterNot { it == "wiki/_log.md" || it.startsWith("wiki/assets/") }
        }

        /** Names shaped like repo paths that aren't: the `gradle/actions/setup-gradle` GitHub action. */
        val NOT_PATHS = setOf("gradle/actions/setup-gradle")

        /** Files the wiki names as removed: the pages that tell their history (seed-data, the SQL seed). */
        val REMOVED = setOf(
            "app/src/main/assets/database/loyscan.sql",
            "app/src/main/java/com/bdaniel/loyscan/data/local/db/SeedDatabaseCallback.kt",
        )
    }
}
