package com.bdaniel.loyscan.testing

// What a wiki page names in the code, read from its text (#222): the repo paths and the test members it names.
// WikiReferencesTest checks these on snippets first, then on the tracked wiki pages, against the tracked files.

/**
 * The repo-relative paths [text] names under the repo's top folders (`app/`, `gradle/`, `play/`, `wiki/`, `.github/`,
 * `.claude/`), each as "line N: path". Not a path inside another one (`~/.gradle/…`, `actions/setup-gradle`), a
 * placeholder (`<version>`, `…`, `*`, `{…}`), a name prefix (`ic_launcher_`) or a build output (`app/build/`).
 */
fun repoPaths(text: String): List<String> =
    text.lines().withIndex().flatMap { (index, line) ->
        REPO_PATH.findAll(line)
            .filterNot { PLACEHOLDER.containsMatchIn(it.value) || line.startsWith("@", it.range.last + 1) }
            .map { it.value.trimEnd('.', ',', ':', ';') }
            .filterNot { it.endsWith("_") || it.startsWith("app/build/") }
            .map { "line ${index + 1}: $it" }
            .toList()
    }

/**
 * The test members (methods, properties) [text] names as `` `SomeTest.member` `` (in backticks, the class's simple name
 * ending in `Test`), each as "line N: SomeTest.member".
 */
fun testMembers(text: String): List<String> =
    text.lines().withIndex().flatMap { (index, line) ->
        TEST_MEMBER.findAll(line).map { "line ${index + 1}: ${it.groupValues[1]}" }.toList()
    }

private val REPO_PATH = Regex("""(?<![\w./~@-])(?:app|gradle|play|wiki|\.github|\.claude)/[\w./<>…*{}-]*[\w/>…*}]""")
private val PLACEHOLDER = Regex("""[<>…*{}]|\.\.\.""")
private val TEST_MEMBER = Regex("""`(\w+Test\.\w+)(?:\(\))?`""")
