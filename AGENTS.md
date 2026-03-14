# Repository Instructions

## Git Policy

- When merging work to `main`, always use a squash merge.
- Do not create merge commits on top of `main`.

## Delivery Process

- Start each substantial item from a fresh `codex/` branch created from current `origin/main`.
- Keep each branch focused on one main objective.
- Update tests and user-facing docs as part of the same branch when behavior or APIs change.
- Run the narrowest useful local verification first, then wider verification as needed.
- Push the branch and open a GitHub PR.
- Watch GitHub Actions until all required checks finish.
- If any check fails, fix the branch and keep looping until the PR is green.
- Merge to `main` only after the PR is green, using a squash merge.
- If branch protection blocks a green PR for repo-admin reasons, use the smallest reversible admin step needed, then restore the original protection immediately afterward.
