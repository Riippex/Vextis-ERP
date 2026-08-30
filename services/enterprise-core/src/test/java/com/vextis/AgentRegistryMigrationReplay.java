package com.vextis;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replays every Flyway migration's effect on {@code agent_registry_entries} in
 * version order and reports the resulting rows.
 *
 * <p>Tool allowlists are the authorization boundary Enterprise Core enforces on
 * {@code /internal/agent-tools/**}, and they are built up across several
 * migrations. V15 rewrote them with absolute assignments and silently revoked
 * grants made by V11 and V14, which reviewing one migration at a time did not
 * catch. This replay asserts the state that actually results from applying all
 * migrations, so the next absolute rewrite fails the build instead of shipping.
 *
 * <p>The parser deliberately understands only the statement shapes this
 * repository uses. Any other statement touching the table raises, so a new
 * migration cannot silently escape the check.
 */
public final class AgentRegistryMigrationReplay {

    private static final Pattern MIGRATION_FILE = Pattern.compile("^V(\\d+)__.*\\.sql$");
    private static final Pattern INSERT_STATEMENT = Pattern.compile(
            "(?is)^INSERT\\s+INTO\\s+agent_registry_entries\\s*\\((?<cols>[^)]*)\\)\\s*VALUES\\s*(?<vals>.*)$");
    private static final Pattern UPDATE_STATEMENT = Pattern.compile(
            "(?is)^UPDATE\\s+agent_registry_entries\\s+SET\\s+(?<set>.*?)\\s+WHERE\\s+(?<where>.*)$");
    private static final Pattern PREDICATE = Pattern.compile("(?is)\\b(?<column>\\w+)\\s*=\\s*'(?<value>[^']*)'");
    private static final Pattern IGNORABLE_STATEMENT = Pattern.compile(
            "(?is)^(CREATE\\s+(UNIQUE\\s+)?INDEX|CREATE\\s+TABLE|ALTER\\s+TABLE|DROP\\s+INDEX|COMMENT\\s+ON)\\b.*");

    private AgentRegistryMigrationReplay() {
    }

    /** One {@code agent_registry_entries} row as it exists after the replay. */
    public record Registration(String tenantId, String agentId, String version, String status,
                               String serviceIdentity, List<String> capabilities,
                               List<String> allowedTools) {
    }

    /** Applies every migration in version order and returns the surviving rows. */
    public static List<Registration> replay() {
        List<MutableRow> rows = new ArrayList<>();
        for (Resource migration : loadMigrationsInVersionOrder()) {
            String fileName = migration.getFilename() == null ? "<unknown>" : migration.getFilename();
            for (String statement : splitStatements(stripComments(read(migration)))) {
                applyStatement(fileName, statement, rows);
            }
        }
        return rows.stream()
                .map(row -> new Registration(row.tenantId, row.agentId, row.version, row.status,
                        row.serviceIdentity, List.copyOf(row.capabilities), List.copyOf(row.allowedTools)))
                .toList();
    }

    /** Active rows for one tenant, keyed by agent id, with their final tool allowlist. */
    public static Map<String, List<String>> activeAllowedToolsByAgent(String tenantId) {
        Map<String, List<String>> byAgent = new LinkedHashMap<>();
        for (Registration registration : replay()) {
            if (registration.tenantId().equals(tenantId) && "ACTIVE".equals(registration.status())) {
                byAgent.put(registration.agentId(), registration.allowedTools());
            }
        }
        return byAgent;
    }

    private static List<Resource> loadMigrationsInVersionOrder() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:db/migration/V*__*.sql");
            List<Resource> ordered = new ArrayList<>(List.of(resources));
            ordered.removeIf(resource -> versionOf(resource) < 0);
            ordered.sort(Comparator.comparingInt(AgentRegistryMigrationReplay::versionOf));
            if (ordered.isEmpty()) {
                throw new IllegalStateException("No Flyway migrations were found on the test classpath");
            }
            return ordered;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static int versionOf(Resource resource) {
        String name = resource.getFilename();
        if (name == null) {
            return -1;
        }
        Matcher matcher = MIGRATION_FILE.matcher(name);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static String read(Resource resource) {
        try (var stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void applyStatement(String fileName, String statement, List<MutableRow> rows) {
        String trimmed = statement.trim();
        if (trimmed.isEmpty() || !trimmed.toLowerCase().contains("agent_registry_entries")) {
            return;
        }
        if (IGNORABLE_STATEMENT.matcher(trimmed).matches()) {
            return;
        }

        Matcher insert = INSERT_STATEMENT.matcher(trimmed);
        if (insert.matches()) {
            applyInsert(insert.group("cols"), insert.group("vals"), rows);
            return;
        }

        Matcher update = UPDATE_STATEMENT.matcher(trimmed);
        if (update.matches()) {
            applyUpdate(update.group("set"), update.group("where"), rows);
            return;
        }

        throw new IllegalStateException(
                "Migration " + fileName + " changes agent_registry_entries with a statement this replay "
                        + "cannot interpret; extend AgentRegistryMigrationReplay so the permission test still "
                        + "covers it: " + abbreviate(trimmed));
    }

    private static void applyInsert(String columnList, String valuesClause, List<MutableRow> rows) {
        List<String> columns = new ArrayList<>();
        for (String column : columnList.split(",")) {
            columns.add(column.trim().toLowerCase());
        }
        for (String tuple : splitTopLevelGroups(valuesClause)) {
            List<String> values = splitTopLevelCommas(tuple);
            if (values.size() != columns.size()) {
                throw new IllegalStateException("INSERT column/value arity mismatch in agent_registry_entries");
            }
            Map<String, String> raw = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                raw.put(columns.get(i), values.get(i).trim());
            }
            rows.add(new MutableRow(
                    scalar(raw.get("tenant_id")),
                    scalar(raw.get("agent_id")),
                    scalar(raw.get("version")),
                    scalar(raw.get("status")),
                    scalar(raw.get("service_identity")),
                    array(raw.get("capabilities")),
                    array(raw.get("allowed_tools"))));
        }
    }

    private static void applyUpdate(String setClause, String whereClause, List<MutableRow> rows) {
        Map<String, String> assignments = new LinkedHashMap<>();
        for (String assignment : splitTopLevelCommas(setClause)) {
            int separator = assignment.indexOf('=');
            if (separator < 0) {
                throw new IllegalStateException("Unsupported SET fragment: " + abbreviate(assignment));
            }
            assignments.put(assignment.substring(0, separator).trim().toLowerCase(),
                    assignment.substring(separator + 1).trim());
        }

        Map<String, String> predicates = new LinkedHashMap<>();
        Matcher matcher = PREDICATE.matcher(whereClause);
        while (matcher.find()) {
            predicates.put(matcher.group("column").toLowerCase(), matcher.group("value"));
        }
        if (predicates.isEmpty()) {
            throw new IllegalStateException("Unsupported WHERE clause: " + abbreviate(whereClause));
        }

        for (MutableRow row : rows) {
            if (!row.matches(predicates)) {
                continue;
            }
            if (assignments.containsKey("allowed_tools")) {
                row.allowedTools = array(assignments.get("allowed_tools"));
            }
            if (assignments.containsKey("capabilities")) {
                row.capabilities = array(assignments.get("capabilities"));
            }
            if (assignments.containsKey("status")) {
                row.status = scalar(assignments.get("status"));
            }
            if (assignments.containsKey("service_identity")) {
                row.serviceIdentity = scalar(assignments.get("service_identity"));
            }
        }
    }

    private static String scalar(String expression) {
        if (expression == null) {
            return null;
        }
        String value = expression.trim();
        if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
    }

    private static List<String> array(String expression) {
        if (expression == null) {
            return new ArrayList<>();
        }
        String value = expression.trim();
        int open = value.indexOf('[');
        int close = value.lastIndexOf(']');
        if (!value.regionMatches(true, 0, "ARRAY", 0, 5) || open < 0 || close < open) {
            throw new IllegalStateException("Unsupported array expression: " + abbreviate(value));
        }
        String inner = value.substring(open + 1, close).trim();
        if (inner.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> elements = new ArrayList<>();
        for (String element : splitTopLevelCommas(inner)) {
            elements.add(scalar(element));
        }
        return elements;
    }

    private static String stripComments(String sql) {
        StringBuilder cleaned = new StringBuilder(sql.length());
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            if (current == '\'') {
                inString = !inString;
            }
            if (!inString && current == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') {
                    i++;
                }
                cleaned.append('\n');
                continue;
            }
            cleaned.append(current);
        }
        return cleaned.toString();
    }

    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char character = sql.charAt(i);
            if (character == '\'') {
                inString = !inString;
            }
            if (character == ';' && !inString) {
                statements.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        statements.add(current.toString());
        return statements;
    }

    private static List<String> splitTopLevelGroups(String text) {
        List<String> groups = new ArrayList<>();
        boolean inString = false;
        int depth = 0;
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\'') {
                inString = !inString;
            }
            if (inString) {
                continue;
            }
            if (character == '(') {
                if (depth == 0) {
                    start = i + 1;
                }
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth == 0 && start >= 0) {
                    groups.add(text.substring(start, i));
                    start = -1;
                }
            }
        }
        return groups;
    }

    private static List<String> splitTopLevelCommas(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\'') {
                inString = !inString;
            }
            if (!inString) {
                if (character == '(' || character == '[') {
                    depth++;
                } else if (character == ')' || character == ']') {
                    depth--;
                } else if (character == ',' && depth == 0) {
                    parts.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(character);
        }
        if (!current.toString().isBlank()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    private static String abbreviate(String text) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= 160 ? single : single.substring(0, 160) + "...";
    }

    private static final class MutableRow {
        private final String tenantId;
        private final String agentId;
        private final String version;
        private String status;
        private String serviceIdentity;
        private List<String> capabilities;
        private List<String> allowedTools;

        private MutableRow(String tenantId, String agentId, String version, String status,
                           String serviceIdentity, List<String> capabilities, List<String> allowedTools) {
            this.tenantId = tenantId;
            this.agentId = agentId;
            this.version = version;
            this.status = status;
            this.serviceIdentity = serviceIdentity;
            this.capabilities = capabilities;
            this.allowedTools = allowedTools;
        }

        private boolean matches(Map<String, String> predicates) {
            for (Map.Entry<String, String> predicate : predicates.entrySet()) {
                String actual = switch (predicate.getKey()) {
                    case "tenant_id" -> tenantId;
                    case "agent_id" -> agentId;
                    case "version" -> version;
                    case "status" -> status;
                    case "service_identity" -> serviceIdentity;
                    default -> throw new IllegalStateException(
                            "Unsupported WHERE column for agent_registry_entries: " + predicate.getKey());
                };
                if (!predicate.getValue().equals(actual)) {
                    return false;
                }
            }
            return true;
        }
    }
}
