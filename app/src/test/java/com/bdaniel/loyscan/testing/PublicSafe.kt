package com.bdaniel.loyscan.testing

// The repo is public (#200): what its tracked files must not hold, and the licence every adapted third-party file
// names. PublicSafeRepoTest checks the rules on snippets first, then on the files `git ls-files` lists.

/**
 * The local paths naming a user and the email addresses in [text], each as "line N: …". A path's user segment must
 * start with a letter to count, so placeholders (`…`, `<name>`, `$USER`) pass. Allowed addresses: `example.*`
 * domains, GitHub's noreply addresses and the `git@github.com` remote.
 */
fun publicSafeProblems(text: String): List<String> =
    text.lines().withIndex().flatMap { (index, line) ->
        val paths = USER_PATHS.flatMap { it.findAll(line) }.sortedBy { it.range.first }
            .map { "line ${index + 1}: a local path naming a user (${it.groupValues[1]})" }
        val emails = EMAIL.findAll(line).map { it.value }.filterNot { it.isAllowedEmail() }
            .map { "line ${index + 1}: an email address ($it)" }
        paths + emails
    }

/**
 * The adapted third-party files in [files] (path → text) that don't name an existing licence: a file names one with
 * `LICENSE-<name>`, which must be among [licences] (their file names). A file that names none must be in [ownWork].
 */
fun licenceProblems(files: Map<String, String>, licences: Set<String>, ownWork: Set<String>): List<String> =
    files.flatMap { (path, text) ->
        val named = LICENCE_NAME.findAll(text).map { it.value }.toSet()
        when {
            named.isEmpty() && path !in ownWork -> listOf("$path names no licence and isn't listed as own work")
            else -> (named - licences).map { "$path names $it, which doesn't exist" }
        }
    }

private const val USER = """([\p{L}][^\s\\/"'`()<>]*)"""
private val USER_PATHS = listOf(
    Regex("""(?i)(?<![\p{L}\p{N}])[a-z]:[\\/]{1,2}users[\\/]{1,2}$USER"""),
    Regex("""(?<![\w.~:-])(?:(?:/mnt)?/[a-z]/[Uu]sers|/Users|/home)/$USER"""),
)
private val EMAIL = Regex("""(?<![\w.+-])[\w.+-]+@[\w-]+(?:\.[\w-]+)*\.[A-Za-z]{2,}(?![\w-])""")
private val LICENCE_NAME = Regex("""LICENSE-[\w-]*\w""")

private fun String.isAllowedEmail(): Boolean {
    val domain = substringAfter('@')
    return domain.startsWith("example.") || domain == "users.noreply.github.com" || this == "git@github.com"
}
