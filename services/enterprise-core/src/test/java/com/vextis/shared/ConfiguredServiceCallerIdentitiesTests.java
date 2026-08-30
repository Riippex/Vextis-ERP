package com.vextis.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredServiceCallerIdentitiesTests {

    private static ConfiguredServiceCallerIdentities identities(String agentToolsToken, String liveGatewayToken) {
        return new ConfiguredServiceCallerIdentities(
                agentToolsToken, "coordinator-agent", liveGatewayToken, "live-gateway-agent");
    }

    @Test
    void resolvesEachCredentialToItsOwnServiceIdentity() {
        ConfiguredServiceCallerIdentities callers = identities("runtime-secret", "gateway-secret");

        assertThat(callers.resolve("runtime-secret")).contains("coordinator-agent");
        assertThat(callers.resolve("gateway-secret")).contains("live-gateway-agent");
    }

    @Test
    void oneCredentialNeverResolvesToTheOtherIdentity() {
        // The regression this guards: a single shared token made the public
        // gateway and the private runtime the same principal.
        ConfiguredServiceCallerIdentities callers = identities("runtime-secret", "gateway-secret");

        assertThat(callers.resolve("gateway-secret")).isNotEqualTo(callers.resolve("runtime-secret"));
    }

    @Test
    void rejectsAnUnknownCredential() {
        assertThat(identities("runtime-secret", "gateway-secret").resolve("something-else")).isEmpty();
        assertThat(identities("runtime-secret", "gateway-secret").resolve("")).isEmpty();
        assertThat(identities("runtime-secret", "gateway-secret").resolve(null)).isEmpty();
    }

    @Test
    void refusesToStartWhenTheTwoCredentialsShareAValue() {
        // Sharing the value would silently collapse the identities and hand the
        // public gateway the private runtime authority, which is the exact
        // outcome the separation exists to prevent.
        assertThatThrownBy(() -> identities("same-secret", "same-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ");
    }

    @Test
    void worksWithOnlyThePrivateRuntimeCredentialConfigured() {
        ConfiguredServiceCallerIdentities callers = identities("runtime-secret", "");

        assertThat(callers.isConfigured()).isTrue();
        assertThat(callers.resolve("runtime-secret")).contains("coordinator-agent");
        assertThat(callers.resolve("gateway-secret")).isEmpty();
    }

    @Test
    void reportsNoCredentialConfiguredWhenBothAreBlank() {
        ConfiguredServiceCallerIdentities callers = identities("", "");

        assertThat(callers.isConfigured()).isFalse();
        assertThat(callers.resolve("anything")).isEmpty();
    }
}
