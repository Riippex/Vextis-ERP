package com.vextis.rag.api.graphql;

import com.vextis.rag.RagDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
class KnowledgeGraphQlController {

    private final RagDirectory ragDirectory;
    private final String demoTenantId;

    KnowledgeGraphQlController(
            RagDirectory ragDirectory,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId
    ) {
        this.ragDirectory = ragDirectory;
        this.demoTenantId = demoTenantId;
    }

    @QueryMapping
    List<KnowledgeDocumentView> knowledgeDocuments() {
        return ragDirectory.listDocuments(demoTenantId).stream()
                .map(KnowledgeDocumentView::from)
                .toList();
    }
}
