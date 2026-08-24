# Web

Vextis's Angular application.

Responsibilities:

- Order inbox.
- CRM, inventory, and billing.
- Mission Control, timeline, approvals, and results.
- Authentication and presentation of authorized capabilities.

Consumes exclusively Enterprise Core's public GraphQL API via operations and types generated from `contracts/graphql/public-api.graphqls`. Does not access PostgreSQL, Pub/Sub, or Gemini.

Commands:

```powershell
pnpm generate:graphql
pnpm lint
pnpm test
pnpm start
```
