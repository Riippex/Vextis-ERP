# Web

Aplicación Angular de Vextis.

Responsabilidades:

- Inbox de órdenes.
- CRM, inventario y facturación.
- Mission Control, timeline, aprobaciones y resultados.
- Autenticación y presentación de capacidades autorizadas.

Consume exclusivamente la API GraphQL pública del Enterprise Core mediante operaciones y tipos generados desde `contracts/graphql/public-api.graphqls`. No accede a PostgreSQL, Pub/Sub ni Gemini.

Comandos:

```powershell
pnpm generate:graphql
pnpm lint
pnpm test
pnpm start
```
