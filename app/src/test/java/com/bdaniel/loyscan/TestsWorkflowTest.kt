package com.bdaniel.loyscan

import com.bdaniel.loyscan.testing.jobConditions
import com.bdaniel.loyscan.testing.ungatedSteps
import com.bdaniel.loyscan.testing.workflowSteps
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * The Tests workflow decides first whether this run needs the gate (#199): `.github/scripts/ci-gate.sh` writes
 * `run=true|false`, and every later step runs only when it is true. The `test` job itself always runs, so the required
 * check reports green on a skip instead of "skipped". What the script decides is checked by
 * `.github/scripts/check-ci-gate.sh`; this test checks the workflow wires it up. wiki/workflows/ci.md
 */
class TestsWorkflowTest {

    private val workflow = File("../.github/workflows/tests.yml").readText().replace("\r\n", "\n")
    private val steps = workflowSteps(workflow, "test")

    @Test
    fun aStepWithoutTheGate_orWithAnOrAroundIt_isFound() {
        val snippet = """
            |jobs:
            |  test:
            |    runs-on: ubuntu-latest
            |    steps:
            |      - uses: actions/checkout@v7
            |
            |      - name: Decide
            |        id: gate
            |        run: .github/scripts/ci-gate.sh
            |
            |      # A comment line before a step
            |      - name: Gated
            |        if: steps.gate.outputs.run == 'true'
            |        run: ./gradlew test
            |
            |      - name: Gated with another term
            |        if: always() && steps.gate.outputs.run == 'true'
            |        run: ./gradlew lint
            |
            |      - name: No condition
            |        run: ./gradlew assemble
            |
            |      - name: Or around it
            |        if: always() || steps.gate.outputs.run == 'true'
            |        run: ./gradlew check
            |
            |      - name: Another output
            |        if: steps.gate.outputs.run == 'false'
            |        run: true
            |  other:
            |    if: false
            |    steps:
            |      - run: true
        """.trimMargin()

        val snippetSteps = workflowSteps(snippet, "test")

        assertThat(snippetSteps.map { it.label })
            .containsExactly("actions/checkout@v7", "Decide", "Gated", "Gated with another term", "No condition", "Or around it", "Another output")
            .inOrder()
        assertThat(ungatedSteps(snippetSteps, from = 2, gate = GATE)).containsExactly(
            "No condition: no if:",
            "Or around it: its if: has ||",
            "Another output: its if: doesn't require $GATE",
        ).inOrder()
        assertThat(jobConditions(snippet, "test")).isEmpty()
        assertThat(jobConditions(snippet, "other")).containsExactly("    if: false")
    }

    @Test
    fun itRunsOnEveryPushToMaster_everyPullRequestIntoIt_andByHand() {
        val triggers = workflow.substringAfter("\non:\n").substringBefore("\n\n")

        assertThat(triggers).isEqualTo("  push:\n    branches: [master]\n  pull_request:\n    branches: [master]\n  workflow_dispatch:")
    }

    @Test
    fun theTestJob_alwaysRuns_soTheRequiredCheckAlwaysReports() {
        assertThat(workflow).contains("\njobs:\n  test:\n")
        assertThat(jobConditions(workflow, "test")).isEmpty()
    }

    @Test
    fun theCheckout_bringsTheBaseCommit_andTheDecisionComesRightAfterIt() {
        assertThat(steps.size).isAtLeast(3)
        assertThat(steps[0].label).isEqualTo("actions/checkout@v7")
        assertThat(steps[0].text).contains("\n          fetch-depth: 2")
        assertThat(steps[0].condition).isNull()
        assertThat(steps[1].text).contains("\n        id: gate\n")
        assertThat(steps[1].text).contains("\n        run: .github/scripts/ci-gate.sh")
        assertThat(steps[1].condition).isNull()
    }

    @Test
    fun everyStepAfterTheDecision_runsOnlyWhenTheGateRuns() {
        assertWithMessage("steps of tests.yml that can run when ci-gate.sh says run=false")
            .that(ungatedSteps(steps, from = 2, gate = GATE))
            .isEmpty()
    }

    @Test
    fun theDecisionScript_isCheckedOnCi() {
        assertThat(steps.filter { ".github/scripts/check-ci-gate.sh" in it.text }).hasSize(1)
    }

    private companion object {
        const val GATE = "steps.gate.outputs.run == 'true'"
    }
}
