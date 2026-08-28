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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoSeedingServiceTests {

    @Mock
    private CustomerAdministration customerAdmin;

    @Mock
    private CreditAdministration creditAdmin;

    @Mock
    private StockAdministration stockAdmin;

    @Mock
    private RagDirectory ragDirectory;

    private DemoSeedingService service;

    @BeforeEach
    void setUp() {
        service = newService(true);
    }

    private DemoSeedingService newService(boolean mockEmbeddingsEnabled) {
        return new DemoSeedingService(
                "demo-tenant",
                mockEmbeddingsEnabled,
                customerAdmin,
                creditAdmin,
                stockAdmin,
                ragDirectory
        );
    }

    @Test
    void seedDemoData_populatesAllDepartments() {
        when(customerAdmin.save(any())).thenReturn(
                new CustomerDirectory.CustomerSummary(UUID.randomUUID(), "Customer", true)
        );
        when(creditAdmin.save(any())).thenReturn(
                new CreditAdministration.SavedCreditProfile(UUID.randomUUID(), "Customer", CreditLookup.CreditStanding.GOOD, 30)
        );
        when(stockAdmin.setAvailability(any())).thenReturn(
                new StockDirectory.StockSummary("SKU", 10)
        );
        when(ragDirectory.ingestDocument(any(), any(), any(), any(), any(), any())).thenReturn(
                new RagDocument(UUID.randomUUID(), "demo-tenant", "gs://uri", "file.pdf", "app/pdf", "hash", 1, RagDocument.Status.INDEXED, 2, Instant.now(), Instant.now())
        );

        DemoSeedingService.SeedResult result = service.seedDemoData("demo-tenant", "tester");

        assertThat(result.tenantId()).isEqualTo("demo-tenant");
        assertThat(result.customers()).hasSize(2);
        assertThat(result.creditProfiles()).hasSize(2);
        assertThat(result.inventory()).hasSize(3);
        assertThat(result.knowledgeDocuments()).hasSize(2);

        verify(customerAdmin, times(2)).save(any());
        verify(creditAdmin, times(2)).save(any());
        verify(stockAdmin, times(3)).setAvailability(any());
        verify(ragDirectory, times(2)).ingestDocument(eq("demo-tenant"), any(), any(), any(), any(), any());
    }

    @Test
    void seedDemoData_skipsKnowledgeDocumentsWhenMockEmbeddingsAreDisabled() {
        service = newService(false);

        when(customerAdmin.save(any())).thenReturn(
                new CustomerDirectory.CustomerSummary(UUID.randomUUID(), "Customer", true)
        );
        when(creditAdmin.save(any())).thenReturn(
                new CreditAdministration.SavedCreditProfile(UUID.randomUUID(), "Customer", CreditLookup.CreditStanding.GOOD, 30)
        );
        when(stockAdmin.setAvailability(any())).thenReturn(
                new StockDirectory.StockSummary("SKU", 10)
        );

        DemoSeedingService.SeedResult result = service.seedDemoData("demo-tenant", "tester");

        // Mock vectors in an environment whose agents embed with Vertex would be
        // unreachable chunks, so the seeder writes none of them.
        assertThat(result.knowledgeDocuments()).isEmpty();
        assertThat(result.customers()).hasSize(2);
        verifyNoInteractions(ragDirectory);
    }

    @Test
    void seededChunksDeclareTheMockEmbeddingSpace() {
        when(customerAdmin.save(any())).thenReturn(
                new CustomerDirectory.CustomerSummary(UUID.randomUUID(), "Customer", true)
        );
        when(creditAdmin.save(any())).thenReturn(
                new CreditAdministration.SavedCreditProfile(UUID.randomUUID(), "Customer", CreditLookup.CreditStanding.GOOD, 30)
        );
        when(stockAdmin.setAvailability(any())).thenReturn(
                new StockDirectory.StockSummary("SKU", 10)
        );
        when(ragDirectory.ingestDocument(any(), any(), any(), any(), any(), any())).thenReturn(
                new RagDocument(UUID.randomUUID(), "demo-tenant", "gs://uri", "file.pdf", "app/pdf", "hash", 1, RagDocument.Status.INDEXED, 2, Instant.now(), Instant.now())
        );

        service.seedDemoData("demo-tenant", "tester");

        ArgumentCaptor<List<RagChunkInput>> chunks = ArgumentCaptor.captor();
        verify(ragDirectory, times(2))
                .ingestDocument(any(), any(), any(), any(), any(), chunks.capture());

        assertThat(chunks.getAllValues())
                .flatExtracting(list -> list)
                .isNotEmpty()
                .allSatisfy(chunk -> assertThat(chunk.embeddingSpace())
                        .isEqualTo(DemoSeedingService.MOCK_EMBEDDING_SPACE));
    }

    @Test
    void generateDeterministicMockVector_producesNormalizedVector() {
        List<Double> vec = DemoSeedingService.generateDeterministicMockVector("Sample query text", 768);
        assertThat(vec).hasSize(768);

        double norm = Math.sqrt(vec.stream().mapToDouble(d -> d * d).sum());
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
    }
}
