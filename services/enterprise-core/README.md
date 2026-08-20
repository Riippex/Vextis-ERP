# Enterprise Core

Monolito modular Java 21/Spring Boot y única autoridad transaccional del ERP/CRM.

Módulos:

- CRM y Ventas.
- Inventario y Operaciones.
- Finanzas y Facturación.
- Workflow, aprobaciones y auditoría.
- Gobierno de agentes.

Expone la API pública para Angular y la API restringida de herramientas para Agent Runtime. Publica eventos mediante transactional outbox. Ningún prompt o agente puede saltarse sus reglas de dominio.

