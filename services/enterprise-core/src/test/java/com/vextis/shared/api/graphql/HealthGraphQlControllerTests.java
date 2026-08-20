package com.vextis.shared.api.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;

@GraphQlTest(HealthGraphQlController.class)
class HealthGraphQlControllerTests {

    @Autowired
    private GraphQlTester graphQlTester;

    @Test
    void reportsServiceAsHealthy() {
        graphQlTester.document("{ health { status } }")
                .execute()
                .path("health.status")
                .entity(String.class)
                .isEqualTo("UP");
    }
}
