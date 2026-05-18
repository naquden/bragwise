---
name: ralph-loop
description: >-
  Run an iterative agent loop that automatically works on a task until all
  user-defined verifications are met. Use when the user says "iterate until it
  builds", "keep trying until the tests pass", "fix in a loop", "run until
  green", "retry loop", "loop until it works", "ralph loop", or any task
  requiring iterative refinement with a clear pass/fail verification step.
---

# Ralph Loop (Agent Loop Script)

This skill provides an automated way to spawn a CLI agent in a loop to iteratively work on a task until **all verifications are met**. It is a general-purpose loop, not limited to just build or lint errors.

The same script (`agent_loop.sh`) works with either the Cursor CLI or the Claude Code CLI. It auto-detects which one is available and invokes it appropriately.

## Verification steps requirement

**CRITICAL:** The loop must continue iterating until all verification steps pass.
If verification steps (e.g., a test script, a build command, a custom validation script) are not explicitly provided by the user from the start, **you MUST ask the user what verification steps are required** before starting the loop.

## When to use

Use this for any task where an agent might need multiple attempts to get it right, and where success can be verified programmatically via a terminal command. Examples:
- Implementing a new feature until all unit tests pass
- Refactoring code until the build succeeds and tests pass
- Fixing complex bugs
- Resolving lint or type errors

## Prerequisites

Ensure `agent_loop.sh` exists in the workspace root and is executable:

```bash
chmod +x agent_loop.sh
```

At least one of `cursor` or `claude` must be on `$PATH`. If both are installed, Claude is preferred (override with `CLI=cursor`).

## How to use

1. **Ask for verifications:** If not already provided, ask the user what verification command must pass (e.g., `./gradlew test`, `npm run build`, a custom python script, etc.).
2. **Prepare prompt:** Create a text file containing the initial prompt (e.g., `prompt.txt`) with the context and goal of the task.
3. **Run the loop:**

   ```bash
   ./agent_loop.sh prompt.txt "./gradlew testDebugUnitTest"
   ```

   Each verification run uses `--no-daemon` by default so parallel loops don't share a Gradle daemon and contend. Pass `"--daemon"` as a third argument to opt back in to daemon reuse when running a single loop:

   ```bash
   ./agent_loop.sh prompt.txt "./gradlew testDebugUnitTest" "--daemon"
   ```

4. **Iterate:** The script will:
   - Spawn an agent (Cursor or Claude) with the prompt.
   - Run the verification command.
   - If verification fails, feed the error output back into a new agent prompt.
   - Repeat until verification passes, up to a maximum of **50 iterations**.

## Customization

Override defaults via environment variables (no need to edit the script):

| Variable | Default | Purpose |
|---|---|---|
| `CLI` | `auto` | Force `cursor` or `claude` (auto picks Claude if available, else Cursor) |
| `MODEL` | `sonnet` | Model alias passed to the CLI. Use `opus` for harder tasks, `haiku` for fast/cheap iterations |
| `MAX_ITERATIONS` | `50` | Maximum loop iterations before giving up |

Example forcing Cursor with a different model:

```bash
CLI=cursor MODEL=gemini-3.1-pro ./agent_loop.sh prompt.txt "./gradlew lint"
```
