package com.bdaniel.loyscan.testing

// A GitHub Actions workflow's text, read as far as TestsWorkflowTest needs it (#199). No YAML parser is on the test
// classpath, so a job's steps are the blocks that start with "      - " under its "    steps:" line.

/** One step of a job: its 0-based [index], the `name:` (or the `uses:` when it has no name), its `if:` and its text. */
data class WorkflowStep(val index: Int, val label: String, val condition: String?, val text: String)

/** The steps of the job [job] in a workflow's [text], in order. Empty when the job or its steps aren't there. */
fun workflowSteps(text: String, job: String): List<WorkflowStep> {
    val lines = text.replace("\r\n", "\n").lines()
    val start = lines.indexOf("  $job:").takeIf { it >= 0 } ?: return emptyList()
    val end = (start + 1 until lines.size).firstOrNull { lines[it].matches(Regex("""  \S.*""")) } ?: lines.size
    val jobLines = lines.subList(start, end)
    val stepsAt = jobLines.indexOf("    steps:").takeIf { it >= 0 } ?: return emptyList()
    val blocks = mutableListOf<MutableList<String>>()
    for (line in jobLines.drop(stepsAt + 1)) {
        when {
            line.startsWith("      - ") -> blocks += mutableListOf("        " + line.removePrefix("      - "))
            line.isNotBlank() && !line.startsWith("      ") -> break // the job's next key after its steps
            else -> blocks.lastOrNull()?.add(line)
        }
    }
    return blocks.mapIndexed { index, block ->
        fun key(name: String) = block.firstOrNull { it.startsWith("        $name: ") }?.substringAfter(": ")?.trim()
        WorkflowStep(index, key("name") ?: key("uses") ?: "step ${index + 1}", key("if"), block.joinToString("\n"))
    }
}

/**
 * The steps from index [from] on that can run without their gate holding, each as "label: why". A step's `if:` must
 * name its gate ([gateOf]) as one of its `&&` terms and hold no `||`, so no other term can bring the step back.
 */
fun ungatedSteps(steps: List<WorkflowStep>, from: Int, gateOf: (WorkflowStep) -> String): List<String> =
    steps.drop(from).mapNotNull { step ->
        val gate = gateOf(step)
        val condition = step.condition
        when {
            condition == null -> "${step.label}: no if:"
            "||" in condition -> "${step.label}: its if: has ||"
            condition.split("&&").none { it.trim() == gate } -> "${step.label}: its if: doesn't require $gate"
            else -> null
        }
    }

/** The `if:` lines of the job [job] itself (indented 4 spaces), which would skip the whole job. */
fun jobConditions(text: String, job: String): List<String> {
    val lines = text.replace("\r\n", "\n").lines()
    val start = lines.indexOf("  $job:").takeIf { it >= 0 } ?: return emptyList()
    return lines.drop(start + 1).takeWhile { !it.matches(Regex("""  \S.*""")) }.filter { it.startsWith("    if:") }
}
