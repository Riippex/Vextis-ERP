# Enterprise Core

Monolito modular Java 21/Spring Boot y única autoridad transaccional del ERP/CRM.

Módulos:

- CRM y Ventas.
- Inventario y Operaciones.
- Finanzas y Facturación.
- Workflow, aprobaciones y auditoría.
- Gobierno de agentes.

Expone una API GraphQL pública para Angular y una API REST/OpenAPI restringida de herramientas para Agent Runtime. Usa Gradle Kotlin DSL y publica eventos mediante transactional outbox. Ningún prompt o agente puede saltarse sus reglas de dominio.

Comandos:

```powershell
./gradlew.bat check
./gradlew.bat bootRun
```

El schema público fuente vive en `../../contracts/graphql/public-api.graphqls`; Gradle lo incorpora a los recursos sin duplicarlo.
