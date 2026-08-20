# Web

Aplicación Angular de Vextis.

Responsabilidades:

- Inbox de órdenes.
- CRM, inventario y facturación.
- Mission Control, timeline, aprobaciones y resultados.
- Autenticación y presentación de capacidades autorizadas.

Consume exclusivamente la API pública del Enterprise Core mediante un cliente generado desde `contracts/openapi/public-api.yaml`. No accede a PostgreSQL, Pub/Sub ni Gemini.

