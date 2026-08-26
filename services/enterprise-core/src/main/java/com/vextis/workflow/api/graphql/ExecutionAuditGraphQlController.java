package com.vextis.workflow.api.graphql;

import com.vextis.agentregistry.AgentDirectory;
import com.vextis.audit.AuditTrail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Controller
class ExecutionAuditGraphQlController {

    private final AuditTrail auditTrail;
    private final AgentDirectory agentDirectory;
    private final String demoTenantId;

    ExecutionAuditGraphQlController(
            AuditTrail auditTrail,
            AgentDirectory agentDirectory,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId
    ) {
        this.auditTrail = auditTrail;
        this.agentDirectory = agentDirectory;
        this.demoTenantId = demoTenantId;
    }

    @SchemaMapping(typeName = "Execution", field = "auditTrail")
    List<AuditEntryView> auditTrail(PurchaseOrderGraphQlController.ExecutionView execution) {
        List<AuditTrail.AuditRecord> records = auditTrail
                .findByCorrelation(demoTenantId, execution.correlationId());
        boolean hasAgentRecords = records.stream().anyMatch(entry -> "AGENT".equals(entry.actorType()));
        Map<String, AgentDirectory.AgentRegistration> approvedAgents = hasAgentRecords
                ? activeAgentsById()
                : Map.of();
        return records.stream()
                .map(entry -> AuditEntryView.from(entry, approvedAgents.get(entry.actorId())))
                .toList();
    }

    private Map<String, AgentDirectory.AgentRegistration> activeAgentsById() {
        Map<String, AgentDirectory.AgentRegistration> agentsByIdentity = new HashMap<>();
        agentDirectory.findAll(demoTenantId).stream()
                .filter(agent -> "ACTIVE".equals(agent.status()))
                .forEach(agent -> {
                    agentsByIdentity.put(agent.agentId(), agent);
                    agentsByIdentity.merge(
                            agent.serviceIdentity(),
                            agent,
                            ExecutionAuditGraphQlController::preferCoordinator);
                });
        return Map.copyOf(agentsByIdentity);
    }

    private static AgentDirectory.AgentRegistration preferCoordinator(
            AgentDirectory.AgentRegistration current,
            AgentDirectory.AgentRegistration candidate
    ) {
        return "CROSS_DEPARTMENT".equals(candidate.department()) ? candidate : current;
    }

    record AuditEntryView(
            UUID id,
            String correlationId,
            String actorType,
            String actorId,
            String action,
            String toolName,
            String resourceType,
            UUID resourceId,
            String result,
            String occurredAt,
            AgentIdentityView approvedAgent
    ) {
        static AuditEntryView from(
                AuditTrail.AuditRecord entry,
                AgentDirectory.AgentRegistration approvedAgent
        ) {
            return new AuditEntryView(
                    entry.id(), entry.correlationId(), entry.actorType(), entry.actorId(), entry.action(),
                    "AGENT".equals(entry.actorType()) ? entry.action().toLowerCase(Locale.ROOT) : null,
                    entry.resourceType(), entry.resourceId(), entry.result(), entry.occurredAt().toString(),
                    approvedAgent == null ? null : AgentIdentityView.from(approvedAgent));
        }
    }

    record AgentIdentityView(
            String agentId,
            String version,
            String displayName,
            String modelId,
            String promptVersion,
            String serviceIdentity
    ) {
        static AgentIdentityView from(AgentDirectory.AgentRegistration agent) {
            return new AgentIdentityView(
                    agent.agentId(), agent.version(), agent.displayName(), agent.modelId(),
                    agent.promptVersion(), agent.serviceIdentity());
        }
    }
}
