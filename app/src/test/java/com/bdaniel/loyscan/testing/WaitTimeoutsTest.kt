package com.bdaniel.loyscan.testing

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** Every wait in the tests passes [WAIT_TIMEOUT_MS], not the 1 s default (#59). */
class WaitTimeoutsTest {

    @Test
    fun aWaitWithNoTimeout_isFound() {
        assertThat(waitsOnTheDefaultTimeout("val a = 1\ncomposeRule.waitUntil { done }\n")).containsExactly(2)
    }

    @Test
    fun aWaitPassingTheConstant_isNotFound() {
        assertThat(waitsOnTheDefaultTimeout("composeRule.waitUntil(WAIT_TIMEOUT_MS) { done }")).isEmpty()
        assertThat(waitsOnTheDefaultTimeout("composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { done }")).isEmpty()
    }

    @Test
    fun aWaitPassingAnotherTimeout_isFound() {
        assertThat(waitsOnTheDefaultTimeout("composeRule.waitUntil(timeoutMillis = 10_000) { done }")).containsExactly(1)
    }

    @Test
    fun aWaitWhoseArgumentsSpanLines_isJudgedByAllOfThem() {
        val source = """
            |composeRule.waitUntil(
            |    WAIT_TIMEOUT_MS,
            |) { done }
            |composeRule.waitUntilAtLeastOneExists(
            |    hasText("a"),
            |)
        """.trimMargin()

        assertThat(waitsOnTheDefaultTimeout(source)).containsExactly(4)
    }

    @Test
    fun theOtherWaits_areFound() {
        val source = """
            |composeRule.waitUntilAtLeastOneExists(hasText("a"))
            |composeRule.waitUntilExactlyOneExists(hasText("a"), WAIT_TIMEOUT_MS)
            |composeRule.waitUntilDoesNotExist(hasText("a"))
            |composeRule.waitUntilNodeCount(hasText("a"), 2)
        """.trimMargin()

        assertThat(waitsOnTheDefaultTimeout(source)).containsExactly(1, 3, 4)
    }

    @Test
    fun aWaitInACommentStringOrImport_isNotACall() {
        val source = """
            |import androidx.compose.ui.test.waitUntilAtLeastOneExists
            |// waitUntil's 1 s default timed out
            |/* composeRule.waitUntil { done } */
            |val s = "waitUntil { done }"
            |val t = ${"\"\"\""}waitUntil { done }${"\"\"\""}
            |composeRule.waitUntil(WAIT_TIMEOUT_MS) { done } // the waitUntil default is too short
        """.trimMargin()

        assertThat(waitsOnTheDefaultTimeout(source)).isEmpty()
    }

    @Test
    fun declaringAWait_isNotACall() {
        assertThat(waitsOnTheDefaultTimeout("private fun waitUntilLoaded(tag: String) = done")).isEmpty()
    }

    @Test
    fun anEscapedQuote_doesNotEndTheString() {
        assertThat(waitsOnTheDefaultTimeout("""val s = "say \"waitUntil { done }\" twice"""")).isEmpty()
    }

    @Test
    fun aQuoteInACharLiteral_doesNotOpenAString() {
        assertThat(waitsOnTheDefaultTimeout("""val q = '"'; composeRule.waitUntil { done }""")).containsExactly(1)
    }

    @Test
    fun aStringInsideATemplate_doesNotEndTheOuterString() {
        val source = """
            |val s = "${'$'}{name("waitUntil { done }")} waitUntil { done }"
            |val t = "${'$'}{if (a) "}" else "{"} waitUntil { done }"
            |label = "${'$'}{name("x")}"; composeRule.waitUntil { done }
        """.trimMargin()

        assertThat(waitsOnTheDefaultTimeout(source)).containsExactly(3)
    }

    @Test
    fun everyWaitInTheTests_passesTheSharedTimeout() {
        val files = File("src/test/java").walk().filter { it.isFile && it.extension == "kt" }.toList()
        assertThat(files.filter { "waitUntil" in it.readText() }).isNotEmpty() // the walk found the tests

        val waits = files.flatMap { file -> waitsOnTheDefaultTimeout(file.readText()).map { "${file.name}:$it" } }

        assertWithMessage("waits on the 1 s default; pass WAIT_TIMEOUT_MS").that(waits).isEmpty()
    }
}
