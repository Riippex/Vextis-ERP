# Agent workflow

All coding agents working in this repository must follow the project workflow in
[`docs/runbooks/pull-requests.md`](docs/runbooks/pull-requests.md).

Unless the owner explicitly says otherwise, completing a versioned task includes:

1. validating the change proportionally to its risk;
2. committing only the task's files on a short-lived branch;
3. pushing that branch; and
4. opening or updating a pull request targeting `develop`.

Report the PR URL and check status in the handoff. Do not merge, approve, close,
or retarget a PR, and do not promote `develop` to `main`, unless the owner asks
explicitly. Preserve unrelated working-tree changes and never commit secrets or
machine-local files.
