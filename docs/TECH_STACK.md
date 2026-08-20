# Vextis — Technology Stack Decision

## Decisión

Vextis usará una arquitectura híbrida con solo dos backends desplegables:

1. **Enterprise Core en Java:** reglas y transacciones de CRM/Ventas, Inventario/Operaciones y Finanzas/Facturación.
2. **Agent Runtime en Python:** coordinación multiagente, Gemini, ADK, RAG, memoria y evaluaciones.

El frontend será Angular y la plataforma de ejecución y datos será Google Cloud.

Esta separación no es “microservicios por moda”. Existe una frontera concreta: el modelo decide y coordina en Python; el core Java valida y ejecuta todas las mutaciones empresariales.

## Stack definitivo

### Frontend

- Angular estable con TypeScript estricto.
- Angular Material para el sistema visual.
- Signals/RxJS para estado local y flujos asíncronos.
- Apollo Angular y GraphQL Code Generator para operaciones y tipos desde el schema público.
- Server-Sent Events para el timeline de ejecución; polling como fallback.
- Firebase Hosting o Cloud Run para el despliegue web.
- Identity Platform/Firebase Authentication para login de la demo.

### Enterprise Core

- Java 21 LTS.
- Spring Boot estable.
- Gradle Wrapper con Kotlin DSL.
- Spring for GraphQL para la API pública consumida por Angular.
- Spring Modulith para límites y eventos entre módulos.
- Arquitectura hexagonal/modular monolith.
- Spring Data JPA/Hibernate.
- Flyway para migraciones.
- Bean Validation.
- PostgreSQL.
- Testcontainers, JUnit 5 y ArchUnit.

Módulos internos:

```text
core/
├── crm-sales/
├── inventory-operations/
├── finance-billing/
├── workflow-audit/
└── shared-kernel/
```

El core es la única autoridad que puede crear pedidos, reservar inventario, aprobar descuentos, consumir crédito o emitir facturas. Los agentes no escriben directamente en sus tablas.

### Agent Runtime

- Python 3.13.
- Google ADK 2.x.
- Google GenAI SDK.
- Vertex AI para Gemini 3.5+ y embeddings.
- FastAPI únicamente para callbacks, health checks y adaptadores que no cubra Agent Engine.
- Pydantic para contratos estructurados.
- Vertex AI Agent Engine Runtime y Memory Bank, si la región/cuenta están disponibles.
- Cloud Run como alternativa de despliegue y para workers auxiliares.
- Pytest y evaluaciones de ADK.

Agentes:

```text
Coordinator Agent
├── CRM Agent
├── Inventory Agent
└── Billing Agent
```

Cada agente llama herramientas respaldadas por la API interna del core Java. No accede directamente a PostgreSQL.

### Capacidades multimodales opcionales

- **Imagen 4 en Vertex AI:** genera un activo visual para una cotización o propuesta aprobada. Agent Runtime construye la solicitud, Cloud Storage conserva el archivo y Enterprise Core registra la relación con la cotización.
- **Veo 3 Fast en Vertex AI:** genera de forma asíncrona un video comercial corto bajo solicitud explícita. Es una tool de CRM/Ventas y reutiliza el pipeline de assets de propuestas; no introduce un módulo de Marketing.
- **Gemini Live API:** habilita conversación bidireccional de baja latencia desde Angular. La sesión se transporta mediante un adaptador de Agent Runtime y traduce la conversación a los mismos comandos y tools existentes.
- **Gemini multimodal estándar:** procesa archivos de audio o mensajes no interactivos; no se abre una sesión Live para tareas batch.

Live Audio no crea un segundo conjunto de casos de uso. Texto, audio subido y conversación Live convergen en los mismos contratos, políticas, aprobaciones e idempotencia. Imagen, Veo y Live se controlan con feature flags y pueden deshabilitarse sin afectar el ERP.

### Datos y RAG

- Cloud SQL for PostgreSQL como fuente transaccional.
- `pgvector` para el primer RAG.
- Vertex AI `gemini-embedding` para embeddings.
- Cloud Storage para PDFs, imágenes generadas, facturas, audio temporal permitido y documentos originales.
- PostgreSQL para metadatos, chunks, ACL, hashes, versiones y relaciones con clientes.
- Memory Bank para preferencias y memoria agentiva de largo plazo; no para saldos, inventario ni datos contables.

Cloud SQL + pgvector evita introducir una base vectorial adicional durante la hackathon. Si el volumen o la latencia lo justifican posteriormente, la evolución natural será AlloyDB AI o Vertex AI Search.

### Eventos y workflows

- Pub/Sub para eventos entre el core y el runtime agentivo.
- Patrón transactional outbox en Java para no perder eventos tras un commit.
- Claves de idempotencia en PostgreSQL.
- Cloud Tasks para reintentos diferidos o callbacks programados, solo si el caso aparece.
- Estados durables en PostgreSQL; Pub/Sub y Redis nunca son la fuente de verdad.

Eventos principales:

```text
purchase_order.received
quote.approved
quote.visual.generated
quote.video.generated
sales_order.created
inventory.reservation.requested
inventory.exception.detected
human_approval.received
invoice.issued
workflow.completed
workflow.failed
live.session.started
live.session.ended
```

### Seguridad

- IAM y una service account por servicio.
- Autenticación service-to-service entre Agent Runtime y Enterprise Core.
- Secret Manager para secretos.
- Model Armor antes de enviar documentos o prompts no confiables al modelo.
- Políticas de herramientas y aprobaciones en el core, no únicamente en prompts.
- Cloud Audit Logs y auditoría funcional propia.
- Datos privados y Cloud SQL sin exposición pública en una configuración productiva.

### Observabilidad

- OpenTelemetry.
- Cloud Logging, Monitoring, Trace y Error Reporting.
- Correlation ID compartido entre frontend, coordinador, agentes, Pub/Sub y core.
- Métricas de negocio: tiempo por workflow, pasos autónomos, aprobaciones, fallos, reintentos y ahorro estimado.

### Entrega

- Monorepo.
- Docker por aplicación.
- Artifact Registry.
- Cloud Build para CI/CD.
- Terraform para infraestructura mínima reproducible.
- Configuración local con Docker Compose para PostgreSQL y emuladores cuando existan.

Estructura:

```text
vextis/
├── apps/
│   └── web/
├── services/
│   ├── enterprise-core/
│   └── agent-runtime/
├── packages/
│   └── api-contracts/
├── infra/
│   └── terraform/
├── docs/
└── compose.yaml
```

## Diagrama de ejecución

```text
Angular Web
    |
    | GraphQL/HTTPS + SSE
    v
Enterprise Core — Java/Spring Boot — Cloud Run
    |                     |
    | PostgreSQL          | Outbox events
    v                     v
Cloud SQL + pgvector    Pub/Sub
                          |
                          v
              Agent Runtime — Python/ADK
                 |        |         |
                 |        |         +-> Memory Bank
                 |        +------------> Gemini / Vertex AI
                 +---------------------> Model Armor
                          |
                          | authenticated tools
                          v
                  Enterprise Core API

Cloud Storage conserva documentos y artefactos.
OpenTelemetry conecta toda la trazabilidad.
```

## Por qué no elegir una sola tecnología

### Solo Java

Es viable y ADK Java existe, pero Python tiene actualmente el camino más completo para ADK 2, workflows en grafo, RAG, ejemplos, evaluaciones y nuevas capacidades de agentes. Todo Java reduce operaciones, pero aumenta el tiempo de experimentación agentiva.

### Solo .NET

.NET es excelente para software empresarial y Cloud Run lo soporta. También existe Google GenAI SDK para C#. Sin embargo, el ecosistema actual de Google ADK y las guías del hackathon son más fuertes en Python, Java y Go. No aporta una ventaja suficiente frente a Java dentro de una hackathon centrada en Google.

### Solo Django/Python

Es la ruta más rápida y puede escalar horizontalmente. Sin embargo, mezclar el runtime probabilístico del agente con las reglas y transacciones del ERP reduce la claridad de los límites y exige más disciplina para mantener un dominio grande. Django Admin sí puede ser útil para herramientas internas, pero no justifica convertirlo en el núcleo del producto.

### Go

Go sería excelente para servicios eficientes, pero no ofrece una ventaja decisiva para este alcance frente a Java en modelado empresarial ni frente a Python en velocidad de construcción de IA.

## Regla para la hackathon

La arquitectura objetivo es Java + Python, pero la entrega se protege con una regla:

> Si al finalizar el **21 de agosto** no existe un flujo vertical desplegado que atraviese Angular, core, Pub/Sub y un agente, se implementarán temporalmente las herramientas de negocio dentro del servicio Python, conservando los contratos GraphQL/OpenAPI y límites de módulos. La separación física a Java se retomará después de la entrega.
>
> Esta fecha depende de que los créditos de Google Cloud ya estén activos — si al 19 de agosto siguen sin pedirse, correr la fecha de este checkpoint el mismo número de días que se demoró la aprobación, no dejarla fija en el calendario.

Esto preserva la visión sin sacrificar una demo funcional.

## Uso de Claude

Claude puede seguir siendo asistente de desarrollo para diseño, generación de código, pruebas y revisión. No condiciona el stack de producción.

En runtime, Gemini debe ser el modelo principal y visible porque es un requisito de la competencia. Si se integra Claude como modelo secundario, debe existir una razón medible —por ejemplo, evaluación cruzada— y no debe ocultar ni diluir el uso de Gemini y Google ADK.

## Lo que no se incorpora inicialmente

- Kubernetes/GKE.
- Kafka.
- Service mesh.
- Una base de datos por módulo.
- Elasticsearch.
- Redis/Memorystore sin un cuello de botella demostrado.
- Microfrontends.
- Event sourcing completo.

Estas tecnologías pueden ser válidas más adelante, pero no resuelven un riesgo actual del MVP.

## Evolución enterprise

1. **Hackathon:** Cloud Run, Cloud SQL, Pub/Sub, Storage, Agent Engine/Memory Bank y un solo entorno.
2. **Primeros clientes:** alta disponibilidad, backups, réplicas, Memorystore si se mide su necesidad, entornos separados y políticas IAM endurecidas.
3. **Escala:** AlloyDB/Spanner según patrones reales, Vertex AI Search para corpus grandes, partición de módulos con presión independiente y GKE solo si Cloud Run deja de encajar.

La escalabilidad se conservará mediante contratos, idempotencia, observabilidad y límites de dominio. No depende de comenzar con microservicios o Kubernetes.
