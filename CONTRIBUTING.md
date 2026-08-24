# Contributing to Vextis ERP

Thank you for taking the time to contribute! Vextis welcomes contributions
from developers of all backgrounds and experience levels.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Architecture Decisions](#architecture-decisions)

---

## Code of Conduct

This project follows our [Code of Conduct](CODE_OF_CONDUCT.md). By
participating, you agree to uphold it.

---

## Getting Started

1. **Search existing issues** before opening a new one — your idea or bug
   may already be tracked.
2. **Open an issue first** for any significant change (new module, contract
   change, or architectural decision) so we can discuss it before you invest
   time coding. Changes that touch `contracts/`, service boundaries, or
   persistence ownership are reviewed against the accepted ADRs in
   `docs/adr/` — see [Architecture Decisions](#architecture-decisions) below.
3. **Small fixes** (typos, docs, minor bugs) can go directly to a PR without
   an issue.

Before writing code, read in this order — it's the same order the project
itself follows:

1. [`docs/TECH_STACK.md`](docs/TECH_STACK.md) — technologies and
   responsibilities of each runtime.
2. [`docs/REPO_STRUCTURE.md`](docs/REPO_STRUCTURE.md) — structure and
   dependency rules.
3. [`docs/CONTRACTS.md`](docs/CONTRACTS.md) — model, APIs, events, and
   integration rules.
4. [`docs/adr/`](docs/adr/) — accepted decisions, newest first.

---

## How to Contribute

### Reporting Bugs

Use the [Bug Report](.github/ISSUE_TEMPLATE/bug_report.md) template.
Include:
- Steps to reproduce
- Expected vs. actual behavior
- Your environment (OS, Java/Node/Python versions, which service is
  affected)

### Suggesting Features

Use the [Feature Request](.github/ISSUE_TEMPLATE/feature_request.md)
template.

### Submitting Code

- One PR per feature or fix — keep scope tight.
- Link the PR to the issue it resolves (`Closes #123`).
- New use cases, resolvers, tools, and agents require tests.
- Any change to a contract (`contracts/graphql`, `contracts/openapi`,
  `contracts/events`) updates the schema, examples, consumer, and test in
  the same PR — never hand-edit generated clients.
- Update documentation if your change affects behavior described in
  `docs/`.

---

## Development Setup

### Prerequisites

- Java 17+ (Gradle Wrapper downloads the Java 21 toolchain)
- Node.js 24 + pnpm 11
- Python 3.13 + [uv](https://docs.astral.sh/uv/)
- Docker Desktop (PostgreSQL locally)

### Steps

```powershell
git clone https://github.com/Riippex/Vextis-ERP.git
cd Vextis-ERP
Copy-Item .env.example .env
./tools/dev.ps1 infra   # Postgres via Docker Compose
./tools/dev.ps1 core    # Enterprise Core, http://localhost:8080/graphql
./tools/dev.ps1 agents  # Agent Runtime, http://localhost:8081/health
./tools/dev.ps1 web     # Angular, http://localhost:4200
```

### Running Tests

```powershell
# Everything, same checks CI runs
./tools/check.ps1

# Individually
cd services/enterprise-core; ./gradlew.bat test
cd services/agent-runtime; uv run pytest; uv run ruff check src tests; uv run mypy src tests
cd apps/web; pnpm lint; pnpm test; pnpm build
```

---

## Pull Request Process

The canonical workflow, including automation authority and release promotion, is
documented in [`docs/runbooks/pull-requests.md`](docs/runbooks/pull-requests.md).

1. Update `develop`, then fork the repo or create a short-lived branch from it:
   ```bash
   git checkout develop
   git pull --ff-only origin develop
   git checkout -b feat/your-feature-name
   ```
2. Make your changes following the [Coding Standards](#coding-standards)
   below.
3. Ensure tests pass locally (`./tools/check.ps1`).
4. Push the branch and open a non-draft PR targeting `develop` using the PR
   template. Finishing a versioned task includes creating or updating this PR.
5. **A required reviewer must approve before merging.** Only
   [@Riippex](https://github.com/Riippex) and
   [@Rapd33](https://github.com/Rapd33) count as required reviewers — either
   one's approval is sufficient. See `.github/CODEOWNERS`.
6. Once approved and CI passes, a maintainer will merge it. Direct pushes to
   `main` and `develop` are blocked by branch protection.
7. Promotion from `develop` to `main` uses a separate, explicitly requested
   release PR because merging `main` can trigger component deployments.

---

## Coding Standards

### Java (Enterprise Core)

- `domain/` has no Spring, JPA, or Google Cloud dependency — enforced by
  ArchUnit (`ArchitectureTests`).
- `application/` holds use cases and transactional coordination;
  `infrastructure/` holds adapters; `api/` holds GraphQL/REST adapters, DTOs,
  and mappers. Transport DTOs are never reused as domain entities.
- A module never queries another module's internal tables — integrate
  through public use cases or domain events.
- Every agent-initiated mutation goes through the same use cases and
  validations as a human-initiated one.

### Python (Agent Runtime)

- Type hints on all functions.
- Agent outputs use Pydantic models, not free-form dicts.
- Tools are small adapters — no inventory, credit, or billing rules live in
  `tools/`.
- `rag/` retrieves evidence; it never decides business actions. `memory/`
  stores preferences and context, never balances or stock.
- Run `ruff check` and `mypy` before committing.

### TypeScript (Angular Web)

- Organize by feature (`features/<name>/`), not by generic technical type.
- `api/` is generated from `contracts/graphql/public-api.graphqls` — never
  edited by hand (`pnpm generate:graphql`).
- `shared/` contains only genuinely shared, business-rule-free elements.
- Don't hardcode color hex values — use the `--vxt-*` tokens documented in
  [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md).

### General

- No commented-out code in PRs.
- Comments only when the *why* is non-obvious — not the *what*.
- Keep PRs focused. Refactors and features go in separate PRs.

---

## Architecture Decisions

Significant changes (service boundaries, new contracts, persistence
ownership, or anything that contradicts an existing ADR) need a new ADR in
`docs/adr/`, not just a PR description. Follow the format of the existing
ones (`0001-*.md`, `0002-*.md`): Status, Date, Context, Decision,
Consequences. If your change conflicts with an accepted ADR, open the issue
first — don't create a silent exception in code.

---

## Questions?

Open a [GitHub Discussion](https://github.com/Riippex/Vextis-ERP/discussions)
— issues are for bugs and features only.
