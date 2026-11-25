# gradlew hangs in Git Bash

In Git Bash, `./gradlew … 2>&1 | X` hangs forever when `X` is a native Windows program (`findstr`, or any `.exe` that
isn't one of Git Bash's own tools). Filter with Git Bash's own `grep` / `tail`, drop the `2>&1`, or run `.\gradlew.bat`
from PowerShell. Found 2026-11-25.

**Use when:**
- a `./gradlew` command in Git Bash prints nothing and never ends, with no build in the Gradle daemon log;
- leftover `xargs.exe` processes are still running after the shell was killed.

## Root cause

The hang is inside `gradlew` itself, before Java starts: the `eval "set -- $( … | xargs -n1 | sed … | tr … )"` block
that splits `DEFAULT_JVM_OPTS`, `JAVA_OPTS` and `GRADLE_OPTS` into arguments. When the script's stderr is a pipe read
by a native Windows process, that `xargs -n1` never exits. Measured 2026-11-25, 8 runs each, with a copy of `gradlew`
whose final `exec` of Java was replaced by a `printf`:

| Pipeline | Hung |
|---|---|
| `gradlew 2>&1 \| findstr x` | 8/8 |
| `gradlew 2>&1 \| <another native .exe>` | 8/8 |
| `gradlew 2>&1 \| grep x` (Git Bash's grep) | 0/8 |
| `gradlew \| <the same native .exe>` (no `2>&1`) | 0/8 |

The real `./gradlew help 2>&1 | <native .exe>` hung the same way; without `2>&1` it finished in 2 s. The same
`xargs -n1` pipeline outside `gradlew` did not hang, so the exact trigger inside the script isn't known
*(inferred: an MSYS2 runtime interaction with a non-MSYS pipe reader; not traced further)*.

**How it got frequent.** A shell hook that rewrites `grep` into a native `.exe` turns the habitual
`./gradlew … 2>&1 | grep …` into this shape. With one in place, 4 of 132 such agent runs hung, against 1 of 1,281 before
it (agent session transcripts, 2026-10-08 to 2026-11-25). In the agent's own shell it hung only sometimes; in a plain
`sh -c` loop every time.

## Steps

```sh
# 1. Stop the hung command (Ctrl+C, or stop the background task).

# 2. Kill the stuck xargs processes. Git Bash's kill -9 doesn't end them; Windows does. In PowerShell:
#    Get-Process xargs -ErrorAction SilentlyContinue | Stop-Process -Force

# 3. Rerun one of these ways:
./gradlew testDebugUnitTest 2>&1 | grep -E "FAILED|BUILD"   # Git Bash's own grep, with no rewriting hook
./gradlew testDebugUnitTest | grep -E "FAILED|BUILD"        # no 2>&1: stderr goes to the terminal
```

```powershell
# Or from PowerShell, which has no MSYS layer:
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest koverVerify lintDebug
```

## If it still hangs

Check what the pipe reader really is: `type grep` in Git Bash should print `/usr/bin/grep`. A wrapper, alias or hook
that swaps in another program brings the hang back. If `./gradlew` hangs with nothing piped at all, it is another
problem; look in the daemon log under `~/.gradle/daemon/<version>/`.
