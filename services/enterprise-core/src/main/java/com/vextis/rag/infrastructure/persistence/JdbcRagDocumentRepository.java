package com.vextis.rag.infrastructure.persistence;

import com.vextis.rag.RagChunk;
import com.vextis.rag.RagDocument;
import com.vextis.rag.RagSearchResult;
import com.vextis.rag.application.port.RagDocumentRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcRagDocumentRepository implements RagDocumentRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcRagDocumentRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(RagDocument document) {
        String sql = """
                INSERT INTO rag_documents (
                    id, tenant_id, document_uri, file_name, content_type,
                    content_hash, version, status, created_at, updated_at
                ) VALUES (
                    :id, :tenantId, :documentUri, :fileName, :contentType,
                    :contentHash, :version, :status, :createdAt, :updatedAt
                )
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", document.id())
                .addValue("tenantId", document.tenantId())
                .addValue("documentUri", document.documentUri())
                .addValue("fileName", document.fileName())
                .addValue("contentType", document.contentType())
                .addValue("contentHash", document.contentHash())
                .addValue("version", document.version())
                .addValue("status", document.status().name())
                .addValue("createdAt", Timestamp.from(document.createdAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("updatedAt", Timestamp.from(document.updatedAt()), Types.TIMESTAMP_WITH_TIMEZONE);
        jdbc.update(sql, params);
    }

    @Override
    public void update(RagDocument document) {
        String sql = """
                UPDATE rag_documents
                SET file_name = :fileName,
                    content_type = :contentType,
                    content_hash = :contentHash,
                    version = :version,
                    status = :status,
                    updated_at = :updatedAt
                WHERE id = :id AND tenant_id = :tenantId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", document.id())
                .addValue("tenantId", document.tenantId())
                .addValue("fileName", document.fileName())
                .addValue("contentType", document.contentType())
                .addValue("contentHash", document.contentHash())
                .addValue("version", document.version())
                .addValue("status", document.status().name())
                .addValue("updatedAt", Timestamp.from(document.updatedAt()), Types.TIMESTAMP_WITH_TIMEZONE);
        jdbc.update(sql, params);
    }

    @Override
    public Optional<RagDocument> findById(String tenantId, UUID documentId) {
        return findOne("d.tenant_id = :tenantId AND d.id = :documentId",
                Map.of("tenantId", tenantId, "documentId", documentId));
    }

    @Override
    public Optional<RagDocument> findByUri(String tenantId, String documentUri) {
        return findOne("d.tenant_id = :tenantId AND d.document_uri = :documentUri",
                Map.of("tenantId", tenantId, "documentUri", documentUri));
    }

    @Override
    public Optional<RagDocument> findByHash(String tenantId, String contentHash) {
        return findOne("d.tenant_id = :tenantId AND d.content_hash = :contentHash",
                Map.of("tenantId", tenantId, "contentHash", contentHash));
    }

    @Override
    public List<RagDocument> listAll(String tenantId) {
        String sql = """
                SELECT d.id, d.tenant_id, d.document_uri, d.file_name, d.content_type,
                       d.content_hash, d.version, d.status, d.created_at, d.updated_at,
                       COUNT(c.id) AS chunk_count
                FROM rag_documents d
                LEFT JOIN rag_document_chunks c ON d.id = c.document_id
                WHERE d.tenant_id = :tenantId
                GROUP BY d.id
                ORDER BY d.created_at DESC
                """;
        return jdbc.query(sql, Map.of("tenantId", tenantId), this::mapDocument);
    }

    @Override
    public void deleteChunksForDocument(String tenantId, UUID documentId) {
        String sql = "DELETE FROM rag_document_chunks WHERE tenant_id = :tenantId AND document_id = :documentId";
        jdbc.update(sql, Map.of("tenantId", tenantId, "documentId", documentId));
    }

    @Override
    public void saveChunks(List<RagChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO rag_document_chunks (
                    id, document_id, tenant_id, chunk_index, chunk_text,
                    token_count, embedding, embedding_space, metadata, created_at
                ) VALUES (
                    :id, :documentId, :tenantId, :chunkIndex, :chunkText,
                    :tokenCount, CAST(:embedding AS vector), :embeddingSpace,
                    CAST(:metadata AS jsonb), :createdAt
                )
                """;
        SqlParameterSource[] batchParams = chunks.stream()
                .map(chunk -> new MapSqlParameterSource()
                        .addValue("id", chunk.id())
                        .addValue("documentId", chunk.documentId())
                        .addValue("tenantId", chunk.tenantId())
                        .addValue("chunkIndex", chunk.chunkIndex())
                        .addValue("chunkText", chunk.chunkText())
                        .addValue("tokenCount", chunk.tokenCount())
                        .addValue("embedding", formatVector(chunk.embedding()))
                        .addValue("embeddingSpace", chunk.embeddingSpace())
                        .addValue("metadata", chunk.metadataJson() != null ? chunk.metadataJson() : "{}")
                        .addValue("createdAt", Timestamp.from(chunk.createdAt()), Types.TIMESTAMP_WITH_TIMEZONE))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(sql, batchParams);
    }

    @Override
    public List<RagSearchResult> searchSimilar(
            String tenantId,
            String embeddingSpace,
            List<Double> embedding,
            int limit,
            double minScore
    ) {
        if (embedding == null || embedding.isEmpty()) {
            return Collections.emptyList();
        }
        String vectorStr = formatVector(embedding);
        String sql = """
                SELECT c.document_id, d.file_name, d.document_uri, c.chunk_index, c.chunk_text,
                       (1.0 - (c.embedding <=> CAST(:vectorStr AS vector))) AS similarity_score,
                       c.metadata
                FROM rag_document_chunks c
                JOIN rag_documents d ON c.document_id = d.id
                WHERE c.tenant_id = :tenantId
                  AND c.embedding_space = :embeddingSpace
                  AND c.embedding IS NOT NULL
                  AND (1.0 - (c.embedding <=> CAST(:vectorStr AS vector))) >= :minScore
                ORDER BY c.embedding <=> CAST(:vectorStr AS vector) ASC
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("embeddingSpace", embeddingSpace)
                .addValue("vectorStr", vectorStr)
                .addValue("minScore", minScore)
                .addValue("limit", Math.max(1, Math.min(limit, 20)));

        return jdbc.query(sql, params, this::mapSearchResult);
    }

    private Optional<RagDocument> findOne(String whereClause, Map<String, Object> params) {
        String sql = """
                SELECT d.id, d.tenant_id, d.document_uri, d.file_name, d.content_type,
                       d.content_hash, d.version, d.status, d.created_at, d.updated_at,
                       COUNT(c.id) AS chunk_count
                FROM rag_documents d
                LEFT JOIN rag_document_chunks c ON d.id = c.document_id
                WHERE %s
                GROUP BY d.id
                """.formatted(whereClause);
        List<RagDocument> results = jdbc.query(sql, params, this::mapDocument);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    private RagDocument mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new RagDocument(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getString("document_uri"),
                rs.getString("file_name"),
                rs.getString("content_type"),
                rs.getString("content_hash"),
                rs.getInt("version"),
                RagDocument.Status.valueOf(rs.getString("status")),
                rs.getInt("chunk_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private RagSearchResult mapSearchResult(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> metadata = Map.of();
        String metadataJson = rs.getString("metadata");
        if (metadataJson != null && !metadataJson.isBlank()) {
            try {
                metadata = objectMapper.readValue(metadataJson, MAP_TYPE);
            } catch (Exception ignored) {
                metadata = Map.of();
            }
        }
        return new RagSearchResult(
                rs.getObject("document_id", UUID.class),
                rs.getString("file_name"),
                rs.getString("document_uri"),
                rs.getInt("chunk_index"),
                rs.getString("chunk_text"),
                rs.getDouble("similarity_score"),
                metadata
        );
    }

    private String formatVector(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}
