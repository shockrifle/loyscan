package com.bdaniel.loyscan

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * The release workflow runs with the upload key's secrets (#74). Nothing tests it on a pull request, which has no
 * secrets, so its rules are checked here, on its text: secrets reach a step only through `env:`, nothing dumps a value
 * into the log, and it runs only for a version tag. The build it runs, `release-bundle.sh`, is exercised on every CI
 * run by `check-release-build.sh`. wiki/workflows/release.md
 */
class ReleaseWorkflowTest {

    private val workflow = File("../.github/workflows/release.yml")
    private val releaseScripts = listOf(workflow, File("../.github/scripts/release-bundle.sh"))

    @Test
    fun aSecret_reachesAStepOnlyAsAnEnvironmentVariable() {
        val secretLines = workflow.readLines().withIndex().filter { "secrets." in it.value }

        assertThat(secretLines).isNotEmpty()
        secretLines.forEach { (index, line) ->
            assertWithMessage("%s", "release.yml:${index + 1} uses a secret outside an env: entry: $line")
                .that(line.matches(Regex("""\s+[A-Z][A-Z0-9_]*: \$\{\{ secrets\.[A-Z][A-Z0-9_]* }}\s*""")))
                .isTrue()
        }
    }

    @Test
    fun nothingPrintsValuesOrTracesCommands() {
        val dumps = Regex("""\becho\b|\bprintenv\b|\bset\s+-[a-wyz]*x|\bxtrace\b|\bbash\s+-x|--info\b|--debug\b|gradlew\b[^|;&)]*\s-[di]\b""")

        releaseScripts.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                assertWithMessage("%s", "${file.name}:${index + 1} could print a value or trace a command: $line")
                    .that(dumps.containsMatchIn(line.substringBefore(" #").takeUnless { it.trimStart().startsWith("#") } ?: ""))
                    .isFalse()
            }
        }
    }

    @Test
    fun itRunsOnlyForAVersionTag_andOnlyForATagOnMaster() {
        val text = workflow.readText()
        val triggers = text.substringAfter("\non:").substringBefore("\npermissions:")

        assertThat(triggers.trim()).isEqualTo("push:\n    tags: ['v*']")
        assertThat(text).contains("git merge-base --is-ancestor \"\$GITHUB_SHA\" origin/master")
        assertThat(text).contains("permissions:\n  contents: write\n")
    }

    @Test
    fun theReleaseStaysADraft() {
        val creates = workflow.readLines().filter { "gh release" in it }

        assertThat(creates).isNotEmpty()
        creates.forEach { assertThat(it).contains("--draft") }
        assertThat(workflow.readText()).doesNotContain("gh release edit")
    }
}
