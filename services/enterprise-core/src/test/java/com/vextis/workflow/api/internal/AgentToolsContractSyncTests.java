package com.vextis.workflow.api.internal;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps {@code contracts/openapi/agent-tools-api.yaml} and the implemented
 * {@code /internal/agent-tools/v1/**} surface in step, in both directions.
 *
 * <p>The contract had drifted both ways at once: the RAG ingestion endpoint
 * existed in code and not in the contract, while a quote-asset operation
 * existed in the contract and had never been implemented. Neither is visible
 * from reading either artifact alone, which is what this test is for.
 */
class AgentToolsContractSyncTests {

    private static final String SERVER_PREFIX = "/internal/agent-tools/v1";
    private static final Path CONTRACT = Path.of("../../contracts/openapi/agent-tools-api.yaml");

    private final Map<String, Object> contract = loadContract();
    private final Map<String, Set<String>> implementedOperations = scanImplementedOperations();

    @Test
    void contractDocumentsEveryImplementedOperation() {
        assertThat(contractOperations())
                .as("operations declared in agent-tools-api.yaml")
                .containsExactlyInAnyOrderElementsOf(flatten(implementedOperations));
    }

    @Test
    void contractDeclaresNoOperationThatIsNotImplemented() {
        Set<String> implemented = flatten(implementedOperations);
        assertThat(contractOperations())
                .as("a documented operation with no handler answers 404 to anyone who trusts the contract")
                .allSatisfy(operation -> assertThat(implemented).contains(operation));
    }

    @Test
    void everyDeclaredToolPolicyIsEnforceable() {
        Set<String> policyNames = Arrays.stream(AgentTool.values())
                .map(AgentTool::policyName)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        assertThat(declaredToolPolicies().values())
                .allSatisfy(policy -> assertThat(policyNames).contains(policy));
    }

    @Test
    void eachControllerDeclaresTheToolPoliciesItActuallyEnforces() {
        Map<String, Set<String>> declared = policiesByController();
        Map<String, Set<String>> enforced = enforcedPoliciesByController();

        assertThat(declared)
                .as("x-vextis-tool-policy per controller must match the AgentTool constants it checks")
                .containsExactlyInAnyOrderEntriesOf(enforced);
    }

    @Test
    void everyWorkflowDenialConstantResolvesToAnEnforceableTool() {
        // AgentTool.valueOf(tool.name()) turns a naming drift between the two
        // enums into a 500 on a live request; this turns it into a build failure.
        assertThat(AgentAuthorizationDenialRecorder.WorkflowTool.values())
                .allSatisfy(tool -> assertThat(AgentTool.valueOf(tool.name())).isNotNull());
    }

    @Test
    void knowledgeSearchAndIngestionRequireAnEmbeddingSpace() {
        // Regression: a query or a document without a space is comparable to
        // anything, which is exactly how mock and Vertex vectors got mixed.
        assertThat(requiredFields("SearchKnowledgeRequest")).contains("embeddingSpace");
        assertThat(requiredFields("IngestKnowledgeDocumentRequest")).contains("embeddingSpace");
    }

    @Test
    void knowledgeSearchDeclaresANonZeroSimilarityFloor() {
        @SuppressWarnings("unchecked")
        Map<String, Object> minScore = (Map<String, Object>) properties("SearchKnowledgeRequest").get("minScore");
        assertThat(((Number) minScore.get("default")).doubleValue()).isGreaterThan(0.0);
    }

    // --- contract -------------------------------------------------------

    private static Map<String, Object> loadContract() {
        if (!Files.isRegularFile(CONTRACT)) {
            throw new IllegalStateException(
                    "Expected the agent tools contract at " + CONTRACT.toAbsolutePath()
                            + "; tests run from the service directory");
        }
        try (InputStream stream = Files.newInputStream(CONTRACT)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> loaded = new Yaml().loadAs(stream, Map.class);
            return loaded;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Set<String> contractOperations() {
        Set<String> operations = new TreeSet<>();
        paths().forEach((path, node) -> methodsOf(node).forEach(
                method -> operations.add(method + " " + path)));
        return operations;
    }

    private Map<String, String> declaredToolPolicies() {
        Map<String, String> policies = new TreeMap<>();
        paths().forEach((path, node) -> node.forEach((method, operation) -> {
            Object policy = operation.get("x-vextis-tool-policy");
            if (policy != null) {
                policies.put(method.toUpperCase(java.util.Locale.ROOT) + " " + path, policy.toString());
            }
        }));
        return policies;
    }

    private Map<String, Set<String>> policiesByController() {
        Map<String, Set<String>> byController = new TreeMap<>();
        declaredToolPolicies().forEach((operation, policy) ->
                byController.computeIfAbsent(controllerFor(operation), key -> new TreeSet<>()).add(policy));
        return byController;
    }

    private String controllerFor(String operation) {
        return implementedOperations.entrySet().stream()
                .filter(entry -> entry.getValue().contains(operation))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Contract declares " + operation + " but no controller implements it"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Map<String, Object>>> paths() {
        return (Map<String, Map<String, Map<String, Object>>>) contract.get("paths");
    }

    private static Set<String> methodsOf(Map<String, Map<String, Object>> pathNode) {
        Set<String> methods = new LinkedHashSet<>();
        for (String key : pathNode.keySet()) {
            if (List.of("get", "put", "post", "delete", "patch").contains(key)) {
                methods.add(key.toUpperCase(java.util.Locale.ROOT));
            }
        }
        return methods;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schema(String name) {
        Map<String, Object> components = (Map<String, Object>) contract.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> schema = (Map<String, Object>) schemas.get(name);
        if (schema == null) {
            throw new IllegalStateException("Contract has no schema named " + name);
        }
        return schema;
    }

    @SuppressWarnings("unchecked")
    private List<String> requiredFields(String schemaName) {
        return (List<String>) schema(schemaName).get("required");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> properties(String schemaName) {
        return (Map<String, Object>) schema(schemaName).get("properties");
    }

    // --- implementation -------------------------------------------------

    private static JavaClasses agentToolControllers() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.vextis");
    }

    /** Controller simple name to the {@code METHOD /path} operations it serves. */
    private static Map<String, Set<String>> scanImplementedOperations() {
        Map<String, Set<String>> byController = new LinkedHashMap<>();
        for (JavaClass javaClass : agentToolControllers()) {
            if (!javaClass.isAnnotatedWith(RestController.class)) {
                continue;
            }
            Class<?> reflected = javaClass.reflect();
            String typePath = pathOf(AnnotatedElementUtils.findMergedAnnotation(reflected, RequestMapping.class));
            for (Method method : reflected.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String fullPath = join(typePath, pathOf(mapping));
                if (!fullPath.startsWith(SERVER_PREFIX)) {
                    continue;
                }
                String contractPath = fullPath.substring(SERVER_PREFIX.length());
                for (RequestMethod verb : mapping.method()) {
                    byController
                            .computeIfAbsent(reflected.getSimpleName(), key -> new TreeSet<>())
                            .add(verb.name() + " " + contractPath);
                }
            }
        }
        return byController;
    }

    /**
     * Controller simple name to the tool policies its handlers actually enforce.
     *
     * <p>Follows two enums. Most controllers name an {@link AgentTool} constant
     * directly; the workflow controller names an
     * {@code AgentAuthorizationDenialRecorder.WorkflowTool} constant and converts
     * it with {@code AgentTool.valueOf(tool.name())} so a denial can be recorded,
     * which is the same policy by a different constant.
     */
    private static Map<String, Set<String>> enforcedPoliciesByController() {
        Map<String, Set<String>> byController = new TreeMap<>();
        for (JavaClass javaClass : agentToolControllers()) {
            if (!javaClass.isAnnotatedWith(RestController.class)) {
                continue;
            }
            for (JavaMethod method : javaClass.getMethods()) {
                method.getFieldAccesses().stream()
                        .filter(access -> isToolConstant(access.getTargetOwner()))
                        .map(access -> AgentTool.valueOf(access.getTarget().getName()).policyName())
                        .forEach(policy -> byController
                                .computeIfAbsent(javaClass.getSimpleName(), key -> new TreeSet<>())
                                .add(policy));
            }
        }
        return byController;
    }

    private static boolean isToolConstant(JavaClass owner) {
        return owner.isEquivalentTo(AgentTool.class)
                || owner.isEquivalentTo(AgentAuthorizationDenialRecorder.WorkflowTool.class);
    }

    private static String pathOf(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0) {
            return "";
        }
        return mapping.path()[0];
    }

    private static String join(String typePath, String methodPath) {
        if (typePath.isEmpty()) {
            return methodPath;
        }
        if (methodPath.isEmpty()) {
            return typePath;
        }
        return typePath + methodPath;
    }

    private static Set<String> flatten(Map<String, Set<String>> byController) {
        Set<String> all = new TreeSet<>();
        byController.values().forEach(all::addAll);
        return all;
    }
}
