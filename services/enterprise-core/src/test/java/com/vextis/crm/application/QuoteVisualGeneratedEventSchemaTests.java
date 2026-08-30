package com.vextis.crm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vextis.audit.AuditTrail;
import com.vextis.crm.GcsProposalAssetStorage;
import com.vextis.crm.ProposalAssetDirectory;
import com.vextis.crm.QuoteExecutionLookup;
import com.vextis.crm.RegisterProposalAssetUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteVisualGeneratedEventSchemaTests {

    private static final Path SCHEMA_PATH = Path.of("../../contracts/events/schemas/quote-visual-generated.v1.json");
    private static final UUID QUOTE_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID ASSET_ID = UUID.fromString("11223344-5566-7788-99aa-bbccddeeff00");
    private static final String STORAGE_URI = "gs://vextis-assets/proposals/deadbeef/chair.png";
    private static final Instant NOW = Instant.parse("2026-08-28T16:00:00Z");

    private ProposalAssetDirectory proposalAssets;
    private QuoteExecutionLookup quoteLookup;
    private GcsProposalAssetStorage assetStorage;
    private AuditTrail audit;
    private NamedParameterJdbcTemplate jdbc;
    private ObjectMapper objectMapper;
    private ProposalAssetService service;

    @BeforeEach
    void setUp() {
        proposalAssets = mock(ProposalAssetDirectory.class);
        quoteLookup = mock(QuoteExecutionLookup.class);
        assetStorage = mock(GcsProposalAssetStorage.class);
        audit = mock(AuditTrail.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        objectMapper = new ObjectMapper();
        service = new ProposalAssetService(
                proposalAssets,
                quoteLookup,
                assetStorage,
                audit,
                jdbc,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void outboxEventPayloadMatchesJsonSchemaExactly() throws IOException {
        when(quoteLookup.findQuote("demo-tenant", QUOTE_ID)).thenReturn(Optional.of(
                new QuoteExecutionLookup.QuoteExecution(QUOTE_ID, "demo-tenant", "corr-auth-123")));
        when(assetStorage.assertUploaded("demo-tenant", STORAGE_URI, ProposalAssetDirectory.MediaType.IMAGE))
                .thenReturn(new GcsProposalAssetStorage.AssetObjectMetadata(101L, "image/png", "hash123", 4096L));

        when(proposalAssets.registerAsset(any(ProposalAssetDirectory.RegisterProposalAssetCommand.class)))
                .thenReturn(new ProposalAssetDirectory.RegisterProposalAssetResult(
                        new ProposalAssetDirectory.ProposalAssetView(
                                ASSET_ID,
                                QUOTE_ID.toString(),
                                STORAGE_URI,
                                101L,
                                "image/png",
                                "hash123",
                                4096L,
                                ProposalAssetDirectory.MediaType.IMAGE,
                                "imagen-3.0-generate-002",
                                "Ergonomic chair concept",
                                "AI-Generated Proposal Concept",
                                "AGENT",
                                "vextis_crm_agent",
                                "corr-auth-123",
                                NOW
                        ),
                        true
                ));

        service.registerAsset(new RegisterProposalAssetUseCase.RegisterCommand(
                "demo-tenant",
                "vextis_crm_agent",
                QUOTE_ID,
                "corr-auth-123",
                "idemp-001",
                STORAGE_URI,
                ProposalAssetDirectory.MediaType.IMAGE,
                "imagen-3.0-generate-002",
                "Ergonomic chair concept",
                "AI-Generated Proposal Concept"
        ));

        // Capture SQL payload
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), paramCaptor.capture());

        String eventEnvelopeJson = (String) paramCaptor.getValue().getValue("payload");
        assertThat(eventEnvelopeJson).isNotNull();

        // Read schema from disk
        assertThat(Files.exists(SCHEMA_PATH)).as("Schema file must exist at " + SCHEMA_PATH).isTrue();
        JsonNode schemaNode = objectMapper.readTree(Files.readString(SCHEMA_PATH));
        JsonNode eventNode = objectMapper.readTree(eventEnvelopeJson);

        // 1. Verify required top-level fields
        JsonNode requiredTopLevel = schemaNode.get("required");
        for (JsonNode field : requiredTopLevel) {
            String fieldName = field.asText();
            assertThat(eventNode.has(fieldName))
                    .as("Event envelope must contain required field '%s'", fieldName)
                    .isTrue();
        }

        // 2. Verify top-level constants & properties
        assertThat(eventNode.get("event_type").asText()).isEqualTo("quote.visual.generated");
        assertThat(eventNode.get("event_version").asInt()).isEqualTo(1);
        assertThat(eventNode.get("producer").asText()).isEqualTo("enterprise-core");
        assertThat(eventNode.get("tenant_id").asText()).isEqualTo("demo-tenant");
        assertThat(eventNode.get("correlation_id").asText()).isEqualTo("corr-auth-123");
        assertThat(eventNode.get("actor").get("type").asText()).isEqualTo("AGENT");
        assertThat(eventNode.get("actor").get("id").asText()).isEqualTo("vextis_crm_agent");

        // 3. Verify no extra top-level fields (additionalProperties: false)
        JsonNode topLevelProperties = schemaNode.get("properties");
        Iterator<String> eventFieldNames = eventNode.fieldNames();
        while (eventFieldNames.hasNext()) {
            String fieldName = eventFieldNames.next();
            assertThat(topLevelProperties.has(fieldName))
                    .as("Unexpected property '%s' in event envelope", fieldName)
                    .isTrue();
        }

        // 4. Verify required payload fields
        JsonNode payloadSchema = topLevelProperties.get("payload");
        JsonNode requiredPayload = payloadSchema.get("required");
        JsonNode payloadNode = eventNode.get("payload");
        for (JsonNode field : requiredPayload) {
            String fieldName = field.asText();
            assertThat(payloadNode.has(fieldName))
                    .as("Payload must contain required field '%s'", fieldName)
                    .isTrue();
        }

        // 5. Verify payload properties & types
        assertThat(payloadNode.get("asset_id").asText()).isEqualTo(ASSET_ID.toString());
        assertThat(payloadNode.get("quote_id").asText()).isEqualTo(QUOTE_ID.toString());
        assertThat(payloadNode.get("storage_uri").asText()).isEqualTo(STORAGE_URI);
        assertThat(Pattern.matches("^gs://[a-z0-9_.-]+/.+$", payloadNode.get("storage_uri").asText())).isTrue();
        assertThat(payloadNode.get("storage_generation").asLong()).isEqualTo(101L);
        assertThat(payloadNode.get("media_type").asText()).isEqualTo("IMAGE");
        assertThat(payloadNode.get("model_id").asText()).isEqualTo("imagen-3.0-generate-002");
        assertThat(payloadNode.get("prompt_summary").asText()).isEqualTo("Ergonomic chair concept");
        assertThat(payloadNode.get("ai_label").asText()).isEqualTo("AI-Generated Proposal Concept");
        assertThat(payloadNode.get("agent_id").asText()).isEqualTo("vextis_crm_agent");
        assertThat(payloadNode.get("correlation_id").asText()).isEqualTo("corr-auth-123");

        // 6. Verify no extra payload fields (additionalProperties: false)
        JsonNode payloadProperties = payloadSchema.get("properties");
        Iterator<String> payloadFieldNames = payloadNode.fieldNames();
        while (payloadFieldNames.hasNext()) {
            String fieldName = payloadFieldNames.next();
            assertThat(payloadProperties.has(fieldName))
                    .as("Unexpected property '%s' in event payload", fieldName)
                    .isTrue();
        }
    }
}
