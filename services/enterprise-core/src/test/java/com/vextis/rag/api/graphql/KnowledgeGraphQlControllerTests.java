package com.vextis.rag.api.graphql;

import com.vextis.rag.RagDirectory;
import com.vextis.rag.RagDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

@GraphQlTest(KnowledgeGraphQlController.class)
class KnowledgeGraphQlControllerTests {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private RagDirectory ragDirectory;

    @Test
    void queriesKnowledgeDocuments() {
        UUID docId = UUID.fromString("44cc63cc-3c91-4d80-a918-605b7f231cf8");
        when(ragDirectory.listDocuments("demo-tenant")).thenReturn(List.of(
                new RagDocument(
                        docId,
                        "demo-tenant",
                        "gs://vextis-demo/docs/catalog.pdf",
                        "catalog.pdf",
                        "application/pdf",
                        "hash_abc",
                        1,
                        RagDocument.Status.INDEXED,
                        5,
                        Instant.parse("2026-08-27T10:00:00Z"),
                        Instant.parse("2026-08-27T10:00:00Z")
                )
        ));

        graphQlTester.document("""
                {
                    knowledgeDocuments {
                        id
                        documentUri
                        fileName
                        status
                        chunkCount
                        version
                    }
                }
                """)
                .execute()
                .path("knowledgeDocuments[0].id")
                .entity(String.class)
                .isEqualTo(docId.toString())
                .path("knowledgeDocuments[0].fileName")
                .entity(String.class)
                .isEqualTo("catalog.pdf")
                .path("knowledgeDocuments[0].chunkCount")
                .entity(Integer.class)
                .isEqualTo(5);
    }
}
