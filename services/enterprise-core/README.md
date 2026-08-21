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
