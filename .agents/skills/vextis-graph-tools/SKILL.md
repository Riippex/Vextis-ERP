---
name: vextis-graph-tools
description: Decide whether graphify, codegraph, or gitnexus applies before codebase exploration, symbol/call lookups, blast-radius checks, pre-commit impact review, or a docs-to-code architecture audit in Vextis. Also covers keeping each tool's index current. Use before cross-cutting investigation or review work; skip for isolated single-file edits with no cross-cutting risk.
---

# Vextis Graph Tools

Three knowledge-graph tools are available, global to this machine, usable by any agent. They do not graph the same thing, and calling the wrong one wastes a tool round-trip or gives an incomplete answer.

| Tool | What it indexes | Form | Freshness |
|---|---|---|---|
| **graphify** | Anything: code + docs + ADRs + contracts + papers/images → clustered communities | Claude skill, `/graphify`, no MCP server | Manual snapshot, written to `graphify-out/` |
| **codegraph** | This codebase's symbols, calls, imports, framework routing | MCP server (`codegraph_explore`) + CLI, index at `.codegraph/` | Auto (file-watcher, ~2s debounce) while the MCP server is running |
| **gitnexus** | This codebase's symbols/calls/clusters, plus git-diff-aware impact and Cypher queries | MCP server + CLI, index at `.gitnexus/` | Manual/hook-triggered re-analyze, not a persistent watcher |

## Decision rule

1. **Question mentions docs, ADRs, contracts, or "does the code match what's documented"** → graphify. It is the only one of the three that ingests anything outside source code. This is an on-demand audit tool, not something to reach for mid-task.
2. **Question is "where is X / who calls this / what does this call / blast radius of changing this function"** during normal coding → **codegraph** first (`codegraph_explore`, or CLI `callers`/`callees`/`impact`/`affected`). It is the default for routine navigation: single-tool-call output, always fresh because the watcher keeps it synced.
3. **Question is "what does my current uncommitted diff actually put at risk" (pre-commit/pre-PR), or needs a graph query codegraph's fixed tool can't express** → **gitnexus** (`detect_changes` for diff-aware impact, `query`/Cypher for ad-hoc traversal, its multi-repo registry if a question ever spans more than this repo).
4. **Never run both codegraph and gitnexus for the same lookup.** If codegraph's answer looks stale or incomplete, the fix is `codegraph sync` (or `status` to check drift), not falling back to gitnexus for the same question — that produces two graphs that can silently disagree.

## Keeping each index current

- **codegraph**: the MCP server watches the project (FSEvents/inotify/ReadDirectoryChangesW) and incrementally re-syncs; check drift with `codegraph status`, force a full rebuild with `codegraph index --force` after a large refactor or branch switch. `codegraph upgrade` updates the CLI/MCP binary itself.
- **gitnexus**: no persistent watcher — re-run `gitnexus analyze` after pulling or switching branches; `gitnexus analyze --force` for a full rebuild if `.gitnexus/` looks stale or after a version upgrade; `gitnexus analyze --repair-fts` if only search ranking looks off. `.gitnexusrc` at the repo root pins the standard flags for this project and **is committed** (everything else under `.codegraph/`/`.gitnexus/` is gitignored — see root `.gitignore`).
- **graphify**: fully manual, no watcher. Re-run `/graphify` after an ADR is accepted, after a `contracts/**` change, or before a scheduled audit like a REST↔GraphQL or Maven↔Gradle style check. Treat any existing `graphify-out/` as a point-in-time snapshot — regenerate before trusting it for "is this true right now."

## Scope inside this monorepo

Both codegraph and gitnexus are initialized once at the **repo root**, not per service — they detect language by file extension, and this repo mixes Java (`services/enterprise-core`), Python (`services/agent-runtime`), and TypeScript (`apps/web`). A root-level index lets impact queries reason across `contracts/graphql/public-api.graphqls`, the Java resolvers, and the generated Angular client in one graph instead of three disconnected ones. If either tool proves too slow or noisy at monorepo scale, fall back to per-service init (`services/enterprise-core/`, `services/agent-runtime/`, `apps/web/`) and say so explicitly when reporting results, since cross-service queries then need three lookups instead of one.

Because both are registered as **global** MCP servers, they answer for whatever project the agent's current working directory resolves to — always confirm `.codegraph/` and `.gitnexus/` exist under the Vextis-ERP root (or the specific service directory, per the fallback above) before trusting a query result; an unindexed project silently returns nothing useful rather than erroring loudly.

## Verification after install or upgrade

Both packages publish npm provenance attestations. After `codegraph upgrade` or `npm update -g gitnexus`, spot-check with `npm audit signatures` before relying on the new build for anything beyond read-only exploration.

## Setup commands

Run once per machine (global) and once per fresh clone (per-repo index):

```powershell
# CodeGraph
npm i -g @colbymchenry/codegraph
codegraph install          # registers the MCP server for Claude Code (and other agents it detects)
codegraph init              # from the Vextis-ERP repo root — builds .codegraph/

# GitNexus
npm install -g gitnexus@latest
gitnexus setup              # registers the MCP server
gitnexus analyze            # from the Vextis-ERP repo root — builds .gitnexus/
```

`graphify` needs no install — it is already a global Claude skill (`~/.claude/skills/graphify/SKILL.md`), triggered with `/graphify`.

Confirm both MCP servers are connected with `claude mcp list`.
