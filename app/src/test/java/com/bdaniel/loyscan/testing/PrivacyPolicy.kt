package com.bdaniel.loyscan.testing

/**
 * The privacy policy's Markdown (`play/privacy-policy/<locale>.md`, #75): a `# ` title, `## ` headings, `- ` bullets
 * (each a claim, [claimProblems]) and a few other lines. A policy file's text is read with `\r\n` as `\n`.
 */
fun policyText(file: String): String = file.replace("\r\n", "\n")

/** The `## ` headings of [markdown], in order, without their `## `. */
fun headings(markdown: String): List<String> =
    markdown.lines().filter { it.startsWith(HEADING) }.map { it.removePrefix(HEADING) }

/** How many `- ` bullets each `## ` section of [markdown] has, the part before the first heading first. */
fun bulletsPerHeading(markdown: String): List<Int> =
    markdown.split(Regex("""(?m)^(?=$HEADING)""")).map { section -> section.lines().count { it.startsWith(POLICY_BULLET) } }

/** The lines of [markdown] that are neither blank, a `## ` heading nor a `- ` bullet: the title and the plain lines. */
fun linesOutsideBullets(markdown: String): List<String> =
    markdown.lines().filterNot { it.isBlank() || it.startsWith(HEADING) || it.startsWith(POLICY_BULLET) }

/** The part of [page] from the line [heading] to the next line that starts as it does (`## `), or to the end. */
fun section(page: String, heading: String): String {
    val lines = page.lines()
    val start = lines.indexOf(heading)
    require(start >= 0) { "no line \"$heading\"" }
    val level = heading.substringBefore(' ') + " "
    val end = lines.drop(start + 1).indexOfFirst { it.startsWith(level) }.let { if (it < 0) lines.size else start + 1 + it }
    return lines.subList(start, end).joinToString("\n")
}

const val POLICY_BULLET = "- "
private const val HEADING = "## "
