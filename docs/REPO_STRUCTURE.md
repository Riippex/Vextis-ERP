# Vextis — Monorepo Structure

## Decisión

Vextis vivirá en un monorepo con tres aplicaciones desplegables y contratos compartidos:

```text
vextis-erp/
├── apps/
│   └── web/                         # Angular
├── services/
│   ├── enterprise-core/             # Java + Spring Boot
│   └── agent-runtime/               # Python + Google ADK
├── contracts/                       # OpenAPI, eventos y esquemas
├── infra/                           # Google Cloud e infraestructura local
├── docs/                            # Arquitectura, ADR y demo
├── tools/                           # Automatización de desarrollo
├── compose.yaml
├── .env.example
├── README.md
└── LICENSE
```

No se usará una carpeta genérica `backend/`: existen dos backends con responsabilidades y ciclos de despliegue diferentes.

## Regla principal de dependencias

```text
Angular ────────> Enterprise Core <──────── Agent Runtime
                         |
                         v
                    PostgreSQL

Enterprise Core ──eventos──> Pub/Sub ──> Agent Runtime
```

- Angular nunca consulta PostgreSQL ni llama directamente a Gemini.
- Agent Runtime nunca escribe directamente en tablas del ERP.
- Enterprise Core nunca importa código Python ni depende de prompts.
- Las tres aplicaciones dependen de `contracts/`, no entre sí a nivel de código.
- La comunicación se realiza con APIs autenticadas y eventos versionados.

## 1. Angular Web

```text
apps/web/
├── src/app/
│   ├── core/                        # Auth, layout, interceptores, configuración
│   ├── shared/                      # Componentes visuales reutilizables
│   ├── features/
│   │   ├── dashboard/
│   │   ├── crm-sales/
│   │   ├── inventory-operations/
│   │   ├── finance-billing/
│   │   ├── agent-mission-control/
│   │   └── approvals/
│   └── api/                         # Clientes generados desde OpenAPI
├── public/
├── Dockerfile
├── package.json
└── angular.json
```

### Reglas

- Organizar por feature, no por tipo técnico global (`components/`, `services/`, etc.).
- `shared/` contiene solo elementos realmente compartidos y sin reglas de negocio.
- `api/` es código generado; no se edita a mano.
- Mission Control es transversal, pero consume datos del Enterprise Core.
- El frontend no decide permisos ni reglas financieras; solo refleja capacidades entregadas por la API.

## 2. Enterprise Core

```text
services/enterprise-core/
├── src/main/java/com/vextis/
│   ├── crm/
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── api/
│   ├── inventory/
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── api/
│   ├── billing/
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── api/
│   ├── workflow/
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── api/
│   ├── identity/
│   ├── audit/
│   └── shared/
├── src/main/resources/
│   ├── db/migration/                # Flyway
│   └── application.yml
├── src/test/
├── Dockerfile
├── pom.xml
└── README.md
```

### Significado de las capas

- `domain/`: entidades, value objects, reglas e interfaces del dominio; sin Spring, JPA ni Google Cloud.
- `application/`: casos de uso y coordinación transaccional.
- `infrastructure/`: JPA, Pub/Sub, Storage, clientes externos y adaptadores.
- `api/`: controladores REST, DTO y mapeadores.

### Reglas

- CRM, Inventario y Facturación son módulos, no microservicios iniciales.
- Un módulo no consulta las tablas internas de otro módulo.
- La integración interna ocurre mediante casos de uso públicos o eventos de dominio.
- `shared/` solo contiene primitivas técnicas o conceptos verdaderamente universales; no es un vertedero.
- Toda mutación iniciada por un agente pasa por los mismos casos de uso y validaciones que una mutación iniciada por un humano.
- El outbox transaccional vive aquí porque el core es dueño de las transacciones empresariales.

## 3. Agent Runtime

```text
services/agent-runtime/
├── src/vextis_agents/
│   ├── app/                         # Configuración y entrypoints
│   ├── coordinator/                 # Enrutamiento de la flota
│   ├── agents/
│   │   ├── crm/
│   │   ├── inventory/
│   │   └── billing/
│   ├── workflows/
│   │   └── order_to_cash/
│   ├── tools/
│   │   ├── core_api/                # Herramientas que llaman a Java
│   │   ├── documents/
│   │   └── approvals/
│   ├── rag/
│   │   ├── ingestion/
│   │   ├── retrieval/
│   │   └── embeddings/
│   ├── memory/
│   ├── policies/
│   ├── observability/
│   └── generated/                   # Cliente OpenAPI generado
├── tests/
│   ├── unit/
│   ├── integration/
│   └── evals/                       # Evaluaciones de agentes
├── pyproject.toml
├── Dockerfile
└── README.md
```

### Reglas

- Los prompts permanecen cerca del agente o workflow que los usa y se versionan.
- Las tools son adaptadores pequeños; no contienen reglas de inventario, crédito o facturación.
- Las salidas de agentes usan modelos Pydantic, no diccionarios libres.
- `rag/` recupera evidencia; no decide acciones empresariales.
- `memory/` almacena preferencias y contexto, nunca saldos o existencias.
- Los evals son parte del producto y se ejecutan en CI.

## 4. Contratos

```text
contracts/
├── openapi/
│   ├── public-api.yaml              # Angular -> Enterprise Core
│   └── agent-tools-api.yaml         # Agent Runtime -> Enterprise Core
├── events/
│   ├── asyncapi.yaml
│   └── schemas/
│       ├── purchase-order-received.v1.json
│       ├── inventory-exception.v1.json
│       ├── approval-received.v1.json
│       └── workflow-completed.v1.json
└── examples/
```

### Reglas

- OpenAPI y JSON Schema son la fuente de verdad de integración.
- Se generan clientes TypeScript y Python; no se comparte una librería binaria entre lenguajes.
- Todos los eventos llevan `eventId`, `eventType`, `version`, `occurredAt`, `correlationId`, `causationId`, `tenantId` y `payload`.
- Los contratos publicados son compatibles hacia atrás o reciben una nueva versión.
- Los ejemplos válidos de payload se prueban en CI.

## 5. Infraestructura

```text
infra/
├── terraform/
│   ├── modules/
│   │   ├── cloud-run/
│   │   ├── cloud-sql/
│   │   ├── pubsub/
│   │   ├── storage/
│   │   └── iam/
│   └── environments/
│       ├── hackathon/
│       └── production/              # Preparado, no necesariamente desplegado
├── docker/
└── seed/
```

### Reglas

- Un entorno `hackathon` pequeño y reproducible.
- Recursos costosos u opcionales se controlan con flags.
- Una service account distinta para web/core y agent runtime.
- Secretos se referencian desde Secret Manager y nunca se guardan en `.env` versionados.
- Los datos semilla pertenecen a `infra/seed/`; las migraciones de esquema pertenecen al core.

## 6. Documentación

```text
docs/
├── architecture/
│   ├── system-context.md
│   ├── containers.md
│   ├── components.md
│   └── diagrams/
├── adr/
│   ├── 0001-monorepo.md
│   ├── 0002-java-python-boundary.md
│   └── 0003-postgres-pgvector.md
├── demo/
│   ├── script.md
│   └── test-scenarios.md
└── runbooks/
```

Los ADR registran decisiones y consecuencias; no repiten tutoriales de instalación.

## 7. Automatización raíz

Los comandos raíz deben ocultar la diferencia entre Maven, npm y Python:

```text
tools/
├── dev.ps1
├── test.ps1
├── generate-contracts.ps1
├── seed.ps1
└── deploy.ps1
```

Comandos conceptuales:

- `dev`: levanta PostgreSQL y las tres aplicaciones.
- `test`: ejecuta Java, Angular, Python, contratos y evals.
- `generate-contracts`: regenera los clientes Angular y Python.
- `seed`: crea los escenarios reproducibles de demo.
- `deploy`: construye y publica los servicios en Google Cloud.

También pueden existir equivalentes `.sh`, pero la lógica no debe duplicarse de forma divergente.

## 8. CI/CD por cambios

```text
Cambio en apps/web/**
  -> lint + unit tests + build Angular

Cambio en services/enterprise-core/**
  -> unit + architecture + integration tests + container build

Cambio en services/agent-runtime/**
  -> lint + type check + unit + evals + container build

Cambio en contracts/**
  -> validate schemas + regenerate clients + test all consumers

Cambio en infra/**
  -> terraform fmt + validate + plan
```

El monorepo no obliga a reconstruir todo en cada cambio; los pipelines usan filtros por ruta.

## 9. Propiedad de datos

Aunque inicialmente exista una sola instancia PostgreSQL, cada módulo es dueño de sus tablas:

```text
crm_*          -> CRM/Ventas
inventory_*    -> Inventario/Operaciones
billing_*      -> Finanzas/Facturación
workflow_*     -> Ejecuciones, aprobaciones e idempotencia
audit_*        -> Auditoría funcional
rag_*          -> Chunks, embeddings y metadatos
outbox_*       -> Publicación confiable de eventos
```

El Agent Runtime accede a `rag_*` mediante un puerto dedicado o servicio de retrieval. No recibe permisos de escritura sobre tablas empresariales.

## 10. Qué se despliega

Para la hackathon:

| Unidad | Tecnología | Destino |
|---|---|---|
| `apps/web` | Angular | Firebase Hosting o Cloud Run |
| `services/enterprise-core` | Java | Cloud Run |
| `services/agent-runtime` | Python/ADK | Agent Engine Runtime o Cloud Run |
| PostgreSQL | Cloud SQL | Google Cloud |
| Documentos | Cloud Storage | Google Cloud |
| Eventos | Pub/Sub | Google Cloud |

La separación física futura de CRM, Inventario o Billing solo se considera cuando exista una razón medible de escalado, disponibilidad, equipo o cumplimiento.

