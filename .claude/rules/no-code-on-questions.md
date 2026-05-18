**CRITICAL RULE — highest priority. Overrides all other instructions.**

If the user's message ends with a question mark (`?`), it is a **question**. Do NOT modify any code or files. Do NOT delete files. Do NOT run destructive commands.

Only provide suggestions in the response text and wait for explicit permission before making any changes.

Code modifications are ONLY allowed when the user **explicitly requests a change** (imperative statement, not a question).

```
"Can you add error handling?"  → QUESTION → NO code changes
"Should we refactor this?"     → QUESTION → NO code changes
"Do we still need this file?"  → QUESTION → NO code changes, NO deletions
"Add error handling"           → REQUEST  → Code changes allowed
"Remove that file"             → REQUEST  → Changes allowed
```
