# Pull request runbook

## Purpose

Every completed, versioned change should leave a reviewable pull request instead
of an unpublished local branch. The default integration target is `develop`;
`main` is the deployment boundary and is promoted separately.

## Branch flow

```text
feat/*, fix/*, docs/*, chore/*
              |
              v
       pull request to develop
              |
       approval + green CI
              |
              v
           develop
              |
       release/promotion PR
              |
       approval + green CI
              |
              v
            main
              |
       selective component CD
```

- Start normal work from the latest `develop`.
- Use a short-lived branch with a descriptive prefix: `feat/`, `fix/`,
  `docs/`, `refactor/`, `test/`, `ci/`, or `chore/`.
- Open the task PR against `develop` as soon as the scoped work and proportional
  verification are complete.
- If a task is already represented by an open PR, push the follow-up commit to
  that PR's branch instead of opening a duplicate.
- Promote `develop` to `main` through a separate PR only when the owner requests a
  release. A merge to `main` can activate the selective deployment workflows.

## Required close-out sequence

The developer or coding agent completing a task must:

1. Inspect the final diff and exclude unrelated user or agent changes.
2. Run tests, linters, contract generation, infrastructure validation, and smoke
   checks proportional to the affected paths.
3. Confirm generated files are current and no secret, credential, local state,
   build artifact, or machine-only file is staged.
4. Commit with a focused Conventional Commit message.
5. Push the current short-lived branch to `origin`.
6. Open a non-draft PR to `develop`, or update the existing PR for that branch.
7. Complete the repository PR template with:
   - the problem and outcome;
   - the significant changes;
   - exact validation performed;
   - contract, migration, IAM, cost, security, and deployment impact when
     applicable;
   - linked issue or `N/A` when no issue exists.
8. Read the initial CI state and report the PR URL, target branch, and check
   status to the owner.

Documentation-only changes still use this workflow unless the owner explicitly
asks to keep them local.

## Authority and safety limits

The standing authorization is to commit task-scoped work, push its branch, and
open or update its PR to `develop`. It does not authorize an agent to:

- push directly to `develop` or `main`;
- merge, squash, rebase-merge, approve, close, or retarget a PR;
- create a `develop` to `main` promotion PR without an explicit release request;
- bypass required reviews, branch protection, CI, or the `hackathon` environment;
- include unrelated dirty-worktree changes merely to make the tree clean;
- expose or replace credentials to overcome an authentication failure.

If validation fails, fix the scoped issue before opening the PR when practical.
If authentication, permissions, merge conflicts, unrelated changes, or an owner
decision blocks safe completion, preserve the branch and commit, then report the
exact blocker instead of broadening authority.

## Review and merge

- CI must be green.
- One required CODEOWNER approval is sufficient under the current branch rules.
- Review comments are resolved on the same branch and PR.
- A maintainer performs the merge.
- After merge, delete the short-lived remote branch when GitHub offers it; local
  cleanup can occur once no work depends on that branch.

## Promotion to main

Promotion is a distinct release decision:

1. Confirm `develop` contains the intended release scope and its CI is green.
2. Open a PR from `develop` to `main` with release and deployment notes.
3. Require approval and green CI.
4. Merge only with explicit owner authorization.
5. Monitor the Web, Enterprise Core, and Agent Runtime delivery workflows. A
   component with no relevant path changes should stop after change detection and
   must not incur a build or deployment.

See [`ci-cd.md`](ci-cd.md) for deployment ownership, recovery, and trust
boundaries.
