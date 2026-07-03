# Avoid Permission Prompts via Smart Command Splitting

When a Bash invocation would chain or pipe commands, anticipate that each segment is
permission-checked independently against the allowlist (`.claude/settings.local.json`).
A piped/chained command prompts the user if **any** segment misses the allowlist — even
when the leading command does not.

**Default: one command per Bash call.** Never chain (`&&`, `;`, `|`) across multiple logical operations unless shell composition is semantically required (see below). Each call is checked independently — splitting always wins.

**Before running:** ask whether the same result can be obtained by:

1. Running each segment as a separate Bash call (storing intermediate output to
   `./temp/bajs/*.txt`, then piping locally with `Read`/`Grep`).
2. Replacing a piped helper with a tool that does not need shell composition
   (e.g. the `Grep` tool instead of `| grep`, `Read` with `offset`/`limit` instead of
   `| tail -N` or `| head -N`).
3. Routing output to a file the first time, then operating on the file with built-in
   tools that don't trigger Bash permission checks at all.

All scratch output goes in `./temp/bajs/` (create it if missing).

**Order of preference** (least → most prompts):

1. Single Bash command, output to `./temp/bajs/*` → followed by `Read` / `Grep` tools.
2. Single Bash command using only allowlisted segments.
3. Piped/chained Bash with potentially-prompting segments — **last resort**, only when
   splitting would hide a real shell-side dependency (e.g. `$(adb shell pidof …)` command
   substitution that must happen in one shell, exit-status forwarding, `xargs`,
   process-substitution).

**Real example that prompted unnecessarily:**

```bash
adb logcat -d -v time | grep "$(adb shell pidof com.assaabloy.hospitality.vostio.debug)" | grep -iE "Dialog|orientation" | tail -40
```

**Better split:**

```bash
PID=$(adb shell pidof com.assaabloy.hospitality.vostio.debug)
adb logcat -d -v time > ./temp/bajs/logcat.txt
# then: Grep tool on ./temp/bajs/logcat.txt with pattern "(Dialog|orientation).*\\b$PID\\b"
# — no `tail` needed; Grep returns the matches directly.
```

## When the chained form is actually required

Do NOT contort code to split when shell composition is semantically required:

- Command substitution that feeds a positional arg in the same shell:
  `git checkout -B "fix/$(date +%s)"` — splitting adds no value.
- `xargs`, `tee` to multiple sinks, process substitution `<(…)`.
- Exit-status forwarding where `set -o pipefail` semantics matter.
- Single-shell environment variables that must persist across the pipeline.

In those cases proceed with the chained form. The rule is "prefer split when split is
equivalent", not "always split".

## Quick checklist before running a piped command

- [ ] Is every binary in the pipeline already in `.claude/settings.local.json` allowlist?
- [ ] If not, can I redirect to `./temp/bajs/*.txt` and use `Read`/`Grep` instead?
- [ ] If neither — does the chain semantically require one shell? If yes, run it.
