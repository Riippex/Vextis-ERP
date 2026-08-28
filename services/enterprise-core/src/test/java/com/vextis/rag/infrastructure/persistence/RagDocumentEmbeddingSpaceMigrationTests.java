package com.vextis.rag.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves V19's derivation logic against real Postgres, one migration version
 * boundary at a time: apply everything up to V18 (the schema V19 assumes),
 * seed {@code rag_documents}/{@code rag_document_chunks} rows by hand the way
 * an existing deployment's data would look, then run V19 itself and check
 * what it did.
 *
 * <p>This is deliberately not a Java unit test against mocked data: the
 * behaviour under test — a {@code DO $$ ... $$} block driving an
 * {@code UPDATE ... FROM (SELECT ... GROUP BY ...)} and a conditional
 * {@code RAISE EXCEPTION} — only exists as SQL, so the only way to verify it
 * is to run it.
 */
@Testcontainers
@EnabledIfDockerAvailable
class RagDocumentEmbeddingSpaceMigrationTests {

    private static final String VERTEX_SPACE = "vertex:text-embedding-004:768";
    private static final String MOCK_SPACE = "mock-sha256:sha256-v1:768";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("vextis_rag_migration_test")
            .withUsername("vextis")
            .withPassword("vextis_test_only");

    private DataSource dataSource;
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void freshDatabase() {
        dataSource = buildDataSource();
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Test
    void derivesTheSpaceWhenEveryChunkAgrees() {
        migrateTo("18");
        UUID documentId = UUID.randomUUID();
        insertDocument(documentId, "gs://bucket/policy.pdf");
        insertChunk(documentId, 0, VERTEX_SPACE);
        insertChunk(documentId, 1, VERTEX_SPACE);

        migrateToLatest();

        assertThat(embeddingSpaceOf(documentId)).isEqualTo(VERTEX_SPACE);
    }

    @Test
    void refusesADocumentWithNoChunks() {
        migrateTo("18");
        UUID orphanId = UUID.randomUUID();
        insertDocument(orphanId, "gs://bucket/orphan.pdf");
        // Deliberately no chunks: nothing to derive the space from.

        assertThatThrownBy(this::migrateToLatest)
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining(orphanId.toString());
    }

    @Test
    void refusesADocumentWhoseChunksDisagreeOnSpace() {
        migrateTo("18");
        UUID mixedId = UUID.randomUUID();
        insertDocument(mixedId, "gs://bucket/mixed.pdf");
        insertChunk(mixedId, 0, VERTEX_SPACE);
        insertChunk(mixedId, 1, MOCK_SPACE);

        assertThatThrownBy(this::migrateToLatest)
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining(mixedId.toString());
    }

    @Test
    void namesEveryOffendingDocumentInOneFailure() {
        migrateTo("18");
        UUID firstOrphan = UUID.randomUUID();
        UUID secondOrphan = UUID.randomUUID();
        insertDocument(firstOrphan, "gs://bucket/orphan-1.pdf");
        insertDocument(secondOrphan, "gs://bucket/orphan-2.pdf");

        assertThatThrownBy(this::migrateToLatest)
                .hasStackTraceContaining(firstOrphan.toString())
                .hasStackTraceContaining(secondOrphan.toString());
    }

    @Test
    void aFailedDerivationLeavesTheSchemaUnchanged() {
        migrateTo("18");
        UUID orphanId = UUID.randomUUID();
        insertDocument(orphanId, "gs://bucket/orphan.pdf");

        assertThatThrownBy(this::migrateToLatest).isInstanceOf(FlywayException.class);

        // The ADD COLUMN that precedes the DO block runs in the same migration
        // transaction, so a failed derivation must not leave rag_documents with
        // a half-added, still-nullable embedding_space column behind.
        Integer columnExists = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'rag_documents' AND column_name = 'embedding_space'",
                Integer.class);
        assertThat(columnExists).isZero();
    }

    @Test
    void aDocumentThatFailsDoesNotBlockDerivingItsSiblings() {
        // The failure has to name every bad row before this migration can be
        // trusted to run unattended; if it stopped at the first offender, a
        // second, unrelated corrupt row would surface as a confusing second
        // failure on the next attempt instead of being reported up front.
        migrateTo("18");
        UUID healthyId = UUID.randomUUID();
        UUID orphanId = UUID.randomUUID();
        insertDocument(healthyId, "gs://bucket/healthy.pdf");
        insertChunk(healthyId, 0, VERTEX_SPACE);
        insertDocument(orphanId, "gs://bucket/orphan.pdf");

        assertThatThrownBy(this::migrateToLatest).hasStackTraceContaining(orphanId.toString());
    }

    private DataSource buildDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void insertDocument(UUID id, String uri) {
        jdbc.update(
                """
                INSERT INTO rag_documents
                    (id, tenant_id, document_uri, file_name, content_type, content_hash, created_at, updated_at)
                VALUES
                    (:id, 'demo-tenant', :uri, 'file.pdf', 'application/pdf', :hash, now(), now())
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("uri", uri)
                        .addValue("hash", Integer.toHexString(uri.hashCode())));
    }

    private void insertChunk(UUID documentId, int index, String embeddingSpace) {
        jdbc.update(
                """
                INSERT INTO rag_document_chunks
                    (id, document_id, tenant_id, chunk_index, chunk_text, token_count, embedding_space, created_at)
                VALUES
                    (:id, :documentId, 'demo-tenant', :index, :text, 1, :space, now())
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("documentId", documentId)
                        .addValue("index", index)
                        .addValue("text", "chunk text " + index)
                        .addValue("space", embeddingSpace));
    }

    private String embeddingSpaceOf(UUID documentId) {
        return jdbc.queryForObject(
                "SELECT embedding_space FROM rag_documents WHERE id = :id",
                new MapSqlParameterSource("id", documentId),
                String.class);
    }
}
