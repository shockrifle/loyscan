package com.bdaniel.loyscan

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Dependabot's update PRs each cost a CI run, and a Gradle one a cold run (#199). They come monthly, and Dependabot
 * doesn't rebase them on every change to master: `/dependency-pr` rebases the one it is about to merge. Kotlin and KSP
 * stay one PR, since their versions are coupled. wiki/workflows/ci.md -> Dependabot
 */
class DependabotConfigTest {

    private val config = File("../.github/dependabot.yml").readText().replace("\r\n", "\n")
    private val updates = ("\n" + config.substringAfter("\nupdates:\n")).split("\n  - ").drop(1)

    @Test
    fun bothEcosystems_areUpdated() {
        assertThat(updates.map { it.lineSequence().first() })
            .containsExactly("package-ecosystem: gradle", "package-ecosystem: github-actions")
    }

    @Test
    fun everyEcosystem_isUpdatedMonthly_andNeverRebasedByDependabotOnItsOwn() {
        updates.forEach { update ->
            val ecosystem = update.lineSequence().first()
            assertWithMessage(ecosystem).that(update).contains("\n    schedule:\n      interval: monthly\n")
            assertWithMessage(ecosystem).that(update).contains("\n    rebase-strategy: disabled\n")
        }
    }

    @Test
    fun kotlinAndKsp_comeInOnePullRequest() {
        val kotlinGroup = config.substringAfter("\n      kotlin:\n        patterns:\n").substringBefore("\n      androidx:")

        assertThat(kotlinGroup.lines().map { it.trim() }).containsAtLeast(
            "- \"org.jetbrains.kotlin*\"",
            "- \"com.google.devtools.ksp*\"",
        )
    }
}
