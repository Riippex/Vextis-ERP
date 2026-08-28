package com.vextis.demo;

import com.vextis.billing.CreditAdministration;
import com.vextis.billing.CreditLookup;
import com.vextis.crm.CustomerAdministration;
import com.vextis.crm.CustomerDirectory;
import com.vextis.inventory.StockAdministration;
import com.vextis.inventory.StockDirectory;
import com.vextis.rag.RagChunkInput;
import com.vextis.rag.RagDirectory;
import com.vextis.rag.RagDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class DemoSeedingService {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedingService.class);

    public static final UUID DEMO_CUSTOMER_1_ID = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");
    public static final UUID DEMO_CUSTOMER_2_ID = UUID.fromString("88dd74dd-4d02-5e91-b029-716c80342da9");

    /**
     * Identifies the vectors {@link #generateDeterministicMockVector} produces.
     * It has to differ from any real provider space so a Vertex query can never
     * match a seeded chunk.
     */
    public static final String MOCK_EMBEDDING_SPACE = "mock-sha256:sha256-v1:768";

    private final String defaultTenantId;
    private final boolean mockEmbeddingsEnabled;
    private final CustomerAdministration customerAdmin;
    private final CreditAdministration creditAdmin;
    private final StockAdministration stockAdmin;
    private final RagDirectory ragDirectory;

    public DemoSeedingService(
            @Value("${vextis.demo.tenant-id:demo-tenant}") String defaultTenantId,
            @Value("${vextis.rag.mock-embeddings.enabled:false}") boolean mockEmbeddingsEnabled,
            CustomerAdministration customerAdmin,
            CreditAdministration creditAdmin,
            StockAdministration stockAdmin,
            RagDirectory ragDirectory
    ) {
        this.defaultTenantId = defaultTenantId;
        this.mockEmbeddingsEnabled = mockEmbeddingsEnabled;
        this.customerAdmin = customerAdmin;
        this.creditAdmin = creditAdmin;
        this.stockAdmin = stockAdmin;
        this.ragDirectory = ragDirectory;
    }

    public record SeedResult(
            String tenantId,
            List<CustomerDirectory.CustomerSummary> customers,
            List<CreditAdministration.SavedCreditProfile> creditProfiles,
            List<StockDirectory.StockSummary> inventory,
            List<RagDocument> knowledgeDocuments
    ) {
    }

    public SeedResult seedDemoData(String tenantId, String actorId) {
        String effectiveTenant = (tenantId == null || tenantId.isBlank()) ? defaultTenantId : tenantId.trim();
        String effectiveActor = (actorId == null || actorId.isBlank()) ? "demo-seeder" : actorId.trim();

        log.info("Seeding deterministic demo data for tenant={}, actor={}", effectiveTenant, effectiveActor);

        // 1. Customers
        CustomerDirectory.CustomerSummary c1 = customerAdmin.save(new CustomerAdministration.SaveCustomerCommand(
                effectiveTenant, effectiveActor, DEMO_CUSTOMER_1_ID, "Acme Colombia S.A.S.", true
        ));
        CustomerDirectory.CustomerSummary c2 = customerAdmin.save(new CustomerAdministration.SaveCustomerCommand(
                effectiveTenant, effectiveActor, DEMO_CUSTOMER_2_ID, "Globex Logistics Corp", true
        ));
        List<CustomerDirectory.CustomerSummary> customers = List.of(c1, c2);

        // 2. Credit Profiles
        CreditAdministration.SavedCreditProfile cr1 = creditAdmin.save(new CreditAdministration.SaveCreditProfileCommand(
                effectiveTenant, effectiveActor, DEMO_CUSTOMER_1_ID, CreditLookup.CreditStanding.GOOD, 30
        ));
        CreditAdministration.SavedCreditProfile cr2 = creditAdmin.save(new CreditAdministration.SaveCreditProfileCommand(
                effectiveTenant, effectiveActor, DEMO_CUSTOMER_2_ID, CreditLookup.CreditStanding.REVIEW, 15
        ));
        List<CreditAdministration.SavedCreditProfile> creditProfiles = List.of(cr1, cr2);

        // 3. Inventory Stock
        StockDirectory.StockSummary s1 = stockAdmin.setAvailability(new StockAdministration.SetAvailabilityCommand(
                effectiveTenant, effectiveActor, "VXT-CHAIR-01", 40
        ));
        StockDirectory.StockSummary s2 = stockAdmin.setAvailability(new StockAdministration.SetAvailabilityCommand(
                effectiveTenant, effectiveActor, "VXT-DESK-01", 25
        ));
        StockDirectory.StockSummary s3 = stockAdmin.setAvailability(new StockAdministration.SetAvailabilityCommand(
                effectiveTenant, effectiveActor, "VXT-LAMP-01", 50
        ));
        List<StockDirectory.StockSummary> inventory = List.of(s1, s2, s3);

        // 4. RAG Reference Documents
        List<RagDocument> docs = seedRagDocuments(effectiveTenant);

        return new SeedResult(effectiveTenant, customers, creditProfiles, inventory, docs);
    }

    private List<RagDocument> seedRagDocuments(String tenantId) {
        if (!mockEmbeddingsEnabled) {
            // Mock vectors are only meaningful next to mock queries. Writing them
            // into an environment whose agents embed with Vertex would leave
            // chunks that no real query can retrieve, so the seeder writes
            // nothing and knowledge has to arrive through governed ingestion.
            log.info(
                    "Skipping demo knowledge documents for tenant={}: mock embeddings are disabled "
                            + "(set vextis.rag.mock-embeddings.enabled=true for local or test runs)",
                    tenantId
            );
            return List.of();
        }

        // Doc 1: Commercial Policy
        String doc1Uri = "gs://vextis-demo-docs/commercial_policy.pdf";
        String doc1Name = "commercial_policy.pdf";
        String text1Chunk0 = "Vextis Commercial Policy: Standard payment terms for approved corporate customers are Net 30 days. Maximum standard discount without CFO approval is 15%.";
        String text1Chunk1 = "Credit terms and billing: Invoices are generated upon successful inventory reservation. Customers in GOOD standing may purchase up to credit limits.";
        String hash1 = computeSha256(text1Chunk0 + text1Chunk1);

        RagChunkInput c1_0 = mockChunk(0, text1Chunk0, Map.of("section", "commercial"));
        RagChunkInput c1_1 = mockChunk(1, text1Chunk1, Map.of("section", "billing"));
        RagDocument doc1 = ragDirectory.ingestDocument(tenantId, doc1Uri, doc1Name, "application/pdf", hash1, List.of(c1_0, c1_1));

        // Doc 2: Inventory Terms
        String doc2Uri = "gs://vextis-demo-docs/inventory_policy.pdf";
        String doc2Name = "inventory_policy.pdf";
        String text2Chunk0 = "Inventory and Returns Policy: Standard return window is 30 days from invoice issuance in original packaging. Stock reservations expire after 24 hours if unconfirmed.";
        String hash2 = computeSha256(text2Chunk0);

        RagChunkInput c2_0 = mockChunk(0, text2Chunk0, Map.of("section", "inventory"));
        RagDocument doc2 = ragDirectory.ingestDocument(tenantId, doc2Uri, doc2Name, "application/pdf", hash2, List.of(c2_0));

        return List.of(doc1, doc2);
    }

    private static RagChunkInput mockChunk(int index, String text, Map<String, Object> metadata) {
        return new RagChunkInput(
                index,
                text,
                text.length() / 4,
                generateDeterministicMockVector(text, 768),
                MOCK_EMBEDDING_SPACE,
                metadata
        );
    }

    public static List<Double> generateDeterministicMockVector(String text, int dimension) {
        String clean = (text == null) ? "" : text.trim().toLowerCase();
        if (clean.isEmpty()) {
            List<Double> zeros = new ArrayList<>(dimension);
            for (int i = 0; i < dimension; i++) {
                zeros.add(0.0);
            }
            return zeros;
        }

        List<Double> raw = new ArrayList<>(dimension);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            int rounds = (dimension + 31) / 32;
            for (int i = 0; i < rounds; i++) {
                byte[] digest = md.digest((clean + ":" + i).getBytes(StandardCharsets.UTF_8));
                for (byte b : digest) {
                    int unsignedByte = b & 0xFF;
                    raw.add(((double) unsignedByte / 127.5) - 1.0);
                    if (raw.size() == dimension) {
                        break;
                    }
                }
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }

        double sumSq = 0.0;
        for (Double val : raw) {
            sumSq += val * val;
        }
        double norm = Math.sqrt(sumSq);

        List<Double> normalized = new ArrayList<>(dimension);
        for (Double val : raw) {
            normalized.add(norm > 0 ? val / norm : 0.0);
        }
        return normalized;
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
