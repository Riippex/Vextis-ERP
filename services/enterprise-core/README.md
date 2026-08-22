# Enterprise Core

Modular Java 21/Spring Boot monolith and sole transactional authority for the ERP/CRM.

Modules:

- CRM and Sales.
- Inventory and Operations.
- Finance and Billing.
- Workflow, approvals, and audit.
- Agent governance.

Exposes a public GraphQL API for Angular and a restricted REST/OpenAPI tools API for Agent Runtime. Uses Gradle Kotlin DSL and publishes events via a transactional outbox. No prompt or agent can bypass its domain rules.

Commands:

```powershell
./gradlew.bat check
./gradlew.bat bootRun
```

The public schema source lives at `../../contracts/graphql/public-api.graphqls`; Gradle bundles it into resources without duplicating it.

## Purchase-order event relay

The transactional outbox publisher is disabled by default. Enable it only after the Pub/Sub topic exists and Application Default Credentials can publish to it:

```text
VEXTIS_PUBSUB_ENABLED=true
GOOGLE_CLOUD_PROJECT=<project-id>
VEXTIS_PUBSUB_TOPIC_ID=order-events
VEXTIS_AGENT_TOOLS_TOKEN=<secret-shared-with-agent-runtime>
```

Store the service token in Secret Manager in deployed environments; never commit it.
