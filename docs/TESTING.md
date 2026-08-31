# How to Test Vextis (Reproducible Testing Guide)

This guide provides a repeatable way to evaluate Vextis from the hosted application or a local checkout. It covers the three departmental workspaces, the governed order-to-cash workflow, grounded text assistance, Gemini Live voice, and the automated verification suites.

## 1. Hosted evaluation prerequisites

- Use the latest desktop version of Chrome or Edge. A desktop browser is required for the best Gemini Live microphone experience.
- Open [https://vextis-erp.web.app](https://vextis-erp.web.app).
- Use the evaluation account supplied privately in the Devpost **Testing instructions** field. Credentials are intentionally not stored in this public repository.
- Allow microphone access when testing Gemini Live.
- Use a unique purchase-order number for every run, for example `PO-JUDGE-20260831-1530`.
- Upload only synthetic test documents. Do not upload confidential or personal information to the shared evaluation workspace.

The hosted application uses the shared `demo-tenant` workspace. Its baseline seed contains:

| Domain | Synthetic records |
|---|---|
| CRM | `Acme Colombia S.A.S.` and `Globex Logistics Corp` |
| Inventory | `VXT-CHAIR-01`, `VXT-DESK-01`, and `VXT-LAMP-01` |
| Finance | A credit profile and payment-term policy for each seeded customer |

Because the workspace is shared, quantities and recent activity can change when another evaluator completes a workflow.

## 2. Sign in and inspect Mission Control

1. Sign in with the private Devpost evaluation account.
2. Open **Overview / Mission Control** at `/app`.
3. Confirm that the registered fleet includes:
   - Vextis Coordinator
   - CRM & Sales Agent
   - Inventory Agent
   - Finance & Billing Agent
4. Inspect the model and prompt versions, allowed tools, operational status, and recent agent activity.
5. Open the CRM, Inventory, and Finance workspaces from the sidebar and confirm that their synthetic records load.

**Pass criteria:** authoritative data is visible for all three departments, the agent fleet is registered, and recent activity includes agent/tool evidence with timestamps.

## 3. Run the governed order-to-cash workflow

### Prepare the sample document

From the repository root, generate the deterministic sample PDF:

```powershell
uv run --with reportlab infra/seed/generate_demo_purchase_order.py
```

The generated file is:

```text
output/pdf/vextis-demo-purchase-order.pdf
```

If Python and `uv` are unavailable, create a one-page PDF containing this synthetic data:

```text
PURCHASE ORDER
Customer: Acme Colombia S.A.S.
SKU: VXT-CHAIR-01
Quantity: 10
Unit price: 100.00 COP
Currency: COP
Payment terms: Net 30
Subtotal: 1000.00 COP
Tax: 190.00 COP
Total: 1190.00 COP
```

### Create and observe an execution

1. Open **Order intake** at `/app/purchase-orders/new`.
2. Enter a new purchase-order number such as `PO-JUDGE-20260831-1530`. Never reuse a number from a previous test.
3. Enter `Acme Colombia S.A.S.` as the customer.
4. Select the generated PDF and click **Upload and create execution**.
5. Observe the execution receipt, correlation ID, Gemini structured plan, departmental steps, and audit evidence.
6. Allow approximately 30–120 seconds for live Cloud Run, Pub/Sub, Cloud SQL, Cloud Storage, Google ADK, and Vertex AI processing. Refresh the execution if the displayed state has not changed.

The expected state sequence is:

```text
RECEIVED -> PLANNING -> RUNNING -> WAITING_APPROVAL
```

Before approval, verify that:

- Gemini 3.5 Flash created a structured execution plan.
- CRM validated the customer and commercial status.
- Inventory checked `VXT-CHAIR-01` availability.
- Finance evaluated credit and Net 30 payment terms.
- The UI shows the correlation ID, agent evidence, readiness checks, and audit events.
- The workflow paused before the sensitive financial action.

### Approve and complete

1. Open the pending execution from **Recent workflow activity** if it is not already visible.
2. Review the plan and evidence.
3. Add an optional approval note and click **Approve**.
4. Monitor the state until it reaches:

```text
WAITING_APPROVAL -> RUNNING -> COMPLETED
```

5. Verify that the final view includes the issued invoice and final audit evidence. For the sample document, the expected invoice total is `1190.00 COP`.
6. Refresh Mission Control and Inventory. The completed run should appear in recent activity and the reserved stock should be reflected in the authoritative inventory total.

**Pass criteria:** the execution reaches `COMPLETED`, the invoice is shown, the inventory change is visible, and the audit trail connects the human approval to the final outcome.

## 4. Test Ask Vextis text assistance

Open the floating **Ask Vextis** panel. Send these prompts one at a time:

```text
What customers do we have?
How many orders does Acme Colombia S.A.S. have?
What products are in inventory?
Find inventory products related to chair.
How much stock is available for VXT-CHAIR-01?
```

Then ask a follow-up in the same conversation:

```text
And how much is available for VXT-DESK-01?
```

**Pass criteria:** responses use current tenant-scoped ERP data, the conversation preserves the immediately preceding context, and the UI identifies the coordinator, specialist agent, and governed tool used. The assistant must distinguish authoritative values from recommendations and must not expose hidden chain-of-thought.

## 5. Test Gemini Live voice

1. Open **Ask Vextis**.
2. Activate **Live** voice mode.
3. Allow microphone access in the browser.
4. Say: `How much stock is available for VXT-CHAIR-01?`
5. Continue with: `And what customers do we have?`

**Pass criteria:** the Live session connects, captures the spoken request, and returns a low-latency spoken response grounded in the same tenant-scoped enterprise data. The visible transcript should make the interaction reviewable.

If Live does not connect, verify that microphone permission is enabled for `vextis-erp.web.app`, no other application has exclusive microphone access, and the authenticated session has not expired. Reload the application and reopen the Live session before retrying.

## 6. Test departmental views and global search

1. Open **CRM & Sales** and locate `Acme Colombia S.A.S.`.
2. Open **Inventory** and locate the three seeded SKUs.
3. Open **Finance & Billing** and inspect the seeded credit/payment-term profiles.
4. Use the global search for `Acme`, `VXT-CHAIR-01`, and the unique purchase-order number created earlier.

**Pass criteria:** the search results link to the corresponding authoritative records and the three departmental views reflect the workflow outcome. Avoid editing shared baseline records during judge evaluation unless mutation behavior is specifically being assessed.

## 7. Expected hosted-evaluation result

The end-to-end evaluation is successful when all of the following are true:

- [ ] Authentication succeeds with the private evaluation account.
- [ ] Mission Control displays the governed four-agent fleet.
- [ ] CRM, Inventory, and Finance show tenant-scoped synthetic data.
- [ ] A unique purchase order advances to `WAITING_APPROVAL`.
- [ ] Human approval advances the execution to `COMPLETED`.
- [ ] The invoice, inventory effect, correlation ID, and audit evidence are visible.
- [ ] Ask Vextis answers list, lookup, search, and contextual follow-up questions.
- [ ] Gemini Live accepts speech and returns a grounded spoken response.

## 8. Local reproducibility

### Prerequisites

- Java 21 (the Gradle wrapper configures the toolchain)
- Node.js 22.22.3 or newer and pnpm 11
- Python 3.13 and `uv`
- Docker Desktop

### Start the stack

```powershell
Copy-Item .env.example .env
./tools/dev.ps1 infra
./tools/dev.ps1 core
./tools/dev.ps1 agents
./tools/dev.ps1 web
./tools/seed-demo.ps1
```

Local endpoints:

| Component | URL |
|---|---|
| Angular UI | `http://localhost:4200` |
| Enterprise Core GraphQL | `http://localhost:8080/graphql` |
| Agent Runtime health | `http://localhost:8081/health` |

The local environment may use configured emulators or test doubles. Live Vertex AI and Gemini Live verification requires a Google Cloud project, Application Default Credentials, enabled APIs, and the repository's documented environment configuration.

## 9. Automated verification

Run the repository-wide verification from the root:

```powershell
./tools/check.ps1
```

Or run the component suites independently:

```powershell
# Enterprise Core
Set-Location services/enterprise-core
./gradlew.bat test

# Agent Runtime
Set-Location ../agent-runtime
uv run pytest
uv run ruff check src tests
uv run mypy src

# Angular Web UI (from the repository root)
Set-Location ../..
pnpm web:test
pnpm web:lint
pnpm web:build
```

Tests marked `model_eval` make live Vertex AI calls and are intentionally excluded from the default fast suite. Run them only with authorized Google Cloud credentials:

```powershell
Set-Location services/agent-runtime
uv run pytest -m model_eval
```

## 10. Troubleshooting and shared-workspace notes

| Symptom | Action |
|---|---|
| Duplicate-order error | Change the purchase-order number and submit again. Duplicate protection is intentional. |
| Execution still processing after two minutes | Refresh the execution and inspect its current state before retrying. Do not resubmit the same order number. |
| Inventory differs from the baseline | Another evaluator may have completed a workflow. Use the currently displayed value as the authoritative starting point. |
| No historical completed-workflow chart yet | Complete and approve an order, then refresh Mission Control. The chart is sourced from completed Core executions. |
| Live microphone is unavailable | Grant site permission, close other microphone users, reload, and start a new Live session. |
| Authentication has expired | Sign out, sign in again with the private evaluation account, and repeat the request. |

All public fixtures are synthetic. Secrets, database credentials, reset tokens, and judge passwords must remain outside the repository.
