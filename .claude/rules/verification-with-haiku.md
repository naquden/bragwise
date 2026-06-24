# Always Run Verification with Haiku Model

When running end-to-end verification of the Bragwise app (e.g., using the `/verify` skill or `bragwise-verify` agent), **always use the Haiku model** for the verification session.

Why: On-device verification (screenshots, logcat, UI navigation, APK install/test) is I/O-bound, not reasoning-heavy. Haiku is fast enough for these tasks, cuts token usage significantly, and frees up quota for reasoning-heavy code changes.

How: Before launching verification, run:
```
/model haiku
```

Then invoke the verification tool (e.g., `/verify` or skill `bragwise-verify`).

After verification, you may switch back to your preferred model if needed.
