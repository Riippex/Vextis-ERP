# Contratos técnicos — Vextis

Este documento define los límites estables entre Angular, Enterprise Core y Agent Runtime. Los contratos ejecutables vivirán en `contracts/` como OpenAPI, AsyncAPI y JSON Schema; este archivo explica sus invariantes y ownership.

Estado: **decisión vigente desde 19 de agosto de 2026**.

## 1. Autoridad y dependencias

```text
Angular ───────> Enterprise Core Java <────── Agent Runtime Python
                         |
                         v
                  Cloud SQL PostgreSQL

Enterprise Core ──outbox──> Pub/Sub ──> Agent Runtime
```

- Enterprise Core es la única autoridad para mutaciones de CRM, inventario, crédito, pedidos y facturas.
- Agent Runtime no recibe credenciales de escritura sobre tablas empresariales.
- Angular no llama directamente a Gemini, Pub/Sub ni PostgreSQL.
- Angular solo abre un canal Live con Agent Runtime después de que Enterprise Core autorice una sesión corta y auditable; nunca recibe credenciales de Vertex AI.
- RAG recupera evidencia; no ejecuta reglas empresariales.
- Memory Bank conserva preferencias y contexto agentivo, nunca saldos, existencias o estados contables.

## 2. Ownership de datos

Una sola instancia PostgreSQL es suficiente para la hackathon. Cada módulo es dueño lógico de sus tablas:

| Prefijo | Dueño | Contenido |
|---|---|---|
| `crm_*` | CRM/Ventas | clientes, contactos, oportunidades, cotizaciones y condiciones comerciales |
| `inventory_*` | Inventario/Operaciones | productos, alias, sustitutos, existencias, reservas y movimientos |
| `billing_*` | Finanzas/Facturación | crédito, facturas, impuestos y estados de cobro |
| `workflow_*` | Workflow | ejecuciones, pasos, aprobaciones e idempotencia |
| `audit_*` | Auditoría | acciones humanas, de agentes y herramientas |
| `agent_*` | Gobierno de agentes | registro, versiones, capacidades y políticas |
| `rag_*` | Retrieval | documentos, chunks, embeddings, ACL, hashes y versiones |
| `outbox_*` | Integración | eventos pendientes, publicados y fallidos |

Un módulo Java no consulta directamente las tablas internas de otro módulo. Se integra mediante casos de uso públicos o eventos de dominio.

## 3. Agregados mínimos

### CRM/Ventas

- `Customer`: identidad, contactos, condiciones y preferencias comerciales.
- `Opportunity`: etapa y valor potencial.
- `Quote`: líneas, precios, descuentos, vigencia y estado.
- `SalesOrder`: líneas confirmadas, total y referencias a reserva y factura.

### Inventario/Operaciones

- `Product`: SKU, alias, precio de referencia y sustitutos.
- `Stock`: disponible, reservado y versión de concurrencia.
- `Reservation`: pedido, SKU, cantidad y estado.

### Finanzas/Facturación

- `CreditAccount`: límite, utilizado y disponible.
- `Invoice`: pedido, cliente, subtotal, impuestos, total y estado.

### Workflow

- `Execution`: objetivo, estado, paso actual y correlation ID.
- `Approval`: opción propuesta, evidencia, decisión, actor y timestamps.
- `IdempotencyRecord`: clave, operación, resultado y expiración opcional.

Los IDs son UUID/ULID estables. Los montos usan decimal y moneda ISO 4217; nunca `float`.

## 4. Estados y transiciones

```text
RECEIVED -> PLANNING -> RUNNING -> COMPLETED
                           |
                           v
                   WAITING_APPROVAL
                           |
                    approve/reject
                           |
                  RUNNING / FAILED

RECEIVED, PLANNING y RUNNING pueden pasar a FAILED.
FAILED puede reintentarse hacia RUNNING solo mediante un comando explícito e idempotente.
```

No se inventan estados desde la UI o los prompts. Una transición inválida es rechazada por Enterprise Core.

## 5. APIs

### API pública — Angular a Enterprise Core

Fuente ejecutable: `contracts/openapi/public-api.yaml`.

Recursos mínimos:

- órdenes de compra e ingesta de documentos;
- clientes, oportunidades y cotizaciones;
- productos, stock y reservas;
- facturas y crédito;
- ejecuciones, timeline y resultados;
- aprobaciones y decisiones;
- registro visible y auditoría de agentes.
- autorización, consulta y cierre de sesiones Live.

### API de herramientas — Agent Runtime a Enterprise Core

Fuente ejecutable: `contracts/openapi/agent-tools-api.yaml`.

Todas las llamadas incluyen identidad de servicio, `agent_id`, `correlation_id` e `idempotency_key` cuando mutan estado.

**CRM Agent**

- `get_customer(customer_id)`
- `get_customer_context(customer_id)`
- `create_quote(customer_id, lines, idempotency_key)`
- `convert_quote_to_order(quote_id, idempotency_key)`

**Inventory Agent**

- `search_products(query, limit)`
- `check_stock(sku, quantity)`
- `reserve_stock(order_id, sku, quantity, idempotency_key)`
- `find_substitutes(sku, quantity)`

**Billing Agent**

- `get_credit_status(customer_id)`
- `validate_commercial_terms(customer_id, order_id)`
- `create_invoice(order_id, idempotency_key)`
- `get_payment_status(invoice_id)`

**Coordinator**

- `create_execution(source_document_id, idempotency_key)`
- `record_plan(execution_id, structured_plan)`
- `request_approval(execution_id, proposal, evidence, idempotency_key)`
- `record_step_result(execution_id, step_id, result, idempotency_key)`

**Media/Proposal Tool**

- `register_quote_asset(quote_id, storage_uri, media_type, model_id, idempotency_key)`

Imagen y Veo se invocan desde Agent Runtime con su service identity. El archivo se guarda en Cloud Storage y solo entonces el Enterprise Core registra el asset contra la cotización. Fallar al generar o registrar una imagen o video no revierte ni bloquea la transacción comercial.

### Sesión Live

1. Angular solicita una sesión a Enterprise Core.
2. Enterprise Core valida usuario, tenant y permisos, crea el registro auditable y devuelve una credencial efímera para Agent Runtime.
3. Angular abre el canal de audio con Agent Runtime; no se conecta directamente a Vertex AI con credenciales permanentes.
4. Agent Runtime usa Gemini Live y convierte intenciones en los mismos tools autenticados definidos arriba.
5. Las acciones sensibles siguen requiriendo aprobación y las acciones mutables siguen exigiendo idempotency key.
6. Al cerrar o expirar la sesión se persisten solo la transcripción y metadatos permitidos por la política de privacidad.

Los agentes no reciben herramientas genéricas como `execute_sql`, `update_record` o `call_any_endpoint`.

## 6. Eventos

Fuente ejecutable: `contracts/events/asyncapi.yaml` y `contracts/events/schemas/*.json`.

Envelope obligatorio:

```json
{
  "event_id": "01J...",
  "event_type": "purchase_order.received",
  "event_version": 1,
  "occurred_at": "2026-08-19T20:00:00Z",
  "producer": "enterprise-core",
  "tenant_id": "demo-tenant",
  "correlation_id": "01J...",
  "causation_id": "01J...",
  "actor": {
    "type": "USER|AGENT|SYSTEM",
    "id": "inventory-agent"
  },
  "payload": {}
}
```

Eventos iniciales:

- `purchase_order.received.v1`
- `workflow.execution.started.v1`
- `workflow.step.completed.v1`
- `workflow.approval.requested.v1`
- `workflow.approval.decided.v1`
- `inventory.reservation.created.v1`
- `inventory.exception.detected.v1`
- `billing.invoice.issued.v1`
- `quote.visual.generated.v1`
- `quote.video.generated.v1`
- `live.session.started.v1`
- `live.session.ended.v1`
- `workflow.execution.completed.v1`
- `workflow.execution.failed.v1`

El nombre lógico dentro de `event_type` no lleva el sufijo de versión; la versión viaja en `event_version`. Los archivos de schema sí incluyen `.v1`.

## 7. Idempotencia y publicación confiable

### Mutaciones

1. El consumidor envía `idempotency_key` estable.
2. Enterprise Core abre una transacción PostgreSQL.
3. Intenta insertar la clave bajo una restricción `UNIQUE(tenant_id, operation, idempotency_key)`.
4. Si existe, devuelve el resultado almacenado sin repetir la operación.
5. Si es nueva, valida reglas, ejecuta la mutación y persiste resultado e idempotency record en la misma transacción.

### Eventos

La mutación empresarial y el registro en `outbox_events` ocurren en la misma transacción. Un publicador independiente envía el evento a Pub/Sub y marca el outbox como publicado. Pub/Sub se trata como entrega al menos una vez; los consumidores deduplican por `event_id`.

No se promete exactly-once distribuido.

## 8. Gobierno de la Fleet

Cada agente registra:

- `agent_id`, nombre y versión;
- propósito y capacidades;
- herramientas permitidas;
- scopes y límites monetarios;
- versión de prompt/policy;
- estado de despliegue;
- service identity efectiva o identidad delegada verificable.

El registro es descriptivo; la autorización es aplicada por Enterprise Core y IAM. Una fila de registro no concede permisos.

La demo debe mostrar al menos:

1. Una acción permitida ejecutada por el agente correcto.
2. Una acción fuera de scope rechazada por política.
3. Una acción sensible pausada para aprobación humana.
4. Auditoría con agente, herramienta, política, resultado y correlation ID.

## 9. Seguridad de contenido

- Archivos externos se guardan primero en Cloud Storage.
- Model Armor inspecciona contenido no confiable antes de enviarlo al modelo cuando la integración esté disponible.
- El texto de documentos se trata como datos, nunca como instrucciones del sistema.
- Logs y auditoría redactan secretos y PII definida por política.
- Los prompts no pueden elevar permisos ni cambiar límites del Enterprise Core.
- El audio crudo no se persiste por defecto; retención y consentimiento deben ser explícitos.
- Todo visual generado se etiqueta como contenido generado por IA y registra modelo, prompt redactado, usuario y cotización de origen.

## 10. Versionado y Clean Code

- OpenAPI, AsyncAPI y JSON Schema se validan en CI.
- Los clientes TypeScript y Python se generan; no se editan manualmente.
- Cambios incompatibles crean una nueva versión de contrato.
- DTO de transporte no se reutilizan como entidades de dominio.
- Los casos de uso dependen de puertos; infraestructura implementa adaptadores.
- No existe una librería `shared` entre Java, Python y TypeScript. Se comparten contratos, no implementación.
- Cualquier cambio de contrato actualiza schema, ejemplos, consumidor y prueba en el mismo cambio.
