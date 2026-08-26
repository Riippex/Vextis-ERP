package com.vextis.agentregistry;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Repository
class JdbcAgentDirectory implements AgentDirectory {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAgentDirectory(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AgentRegistration> findAll(String tenantId) {
        return jdbc.query(
                """
                SELECT agent_id, version, display_name, department, purpose, framework,
                       model_id, prompt_version, service_identity, status, capabilities, allowed_tools
                FROM agent_registry_entries
                WHERE tenant_id = :tenantId
                ORDER BY CASE WHEN department = 'CROSS_DEPARTMENT' THEN 0 ELSE 1 END, display_name
                """,
                Map.of("tenantId", tenantId),
                (rs, row) -> new AgentRegistration(
                        rs.getString("agent_id"),
                        rs.getString("version"),
                        rs.getString("display_name"),
                        rs.getString("department"),
                        rs.getString("purpose"),
                        rs.getString("framework"),
                        rs.getString("model_id"),
                        rs.getString("prompt_version"),
                        rs.getString("service_identity"),
                        rs.getString("status"),
                        toList(rs.getArray("capabilities")),
                        toList(rs.getArray("allowed_tools"))
                )
        );
    }

    private static List<String> toList(Array sqlArray) throws SQLException {
        try {
            return List.copyOf(Arrays.asList((String[]) sqlArray.getArray()));
        } finally {
            sqlArray.free();
        }
    }
}
