# infra/

Configuración reproducible de Cloud Run, Cloud SQL PostgreSQL, Pub/Sub, Cloud Storage, IAM, Secret Manager, Artifact Registry y observabilidad.

La infraestructura mínima se define con Terraform y un entorno `hackathon`. Recursos opcionales como Memorystore, GKE o réplicas no se crean hasta que exista una necesidad medible.
