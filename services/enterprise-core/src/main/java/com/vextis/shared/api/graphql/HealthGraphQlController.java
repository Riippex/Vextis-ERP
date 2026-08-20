package com.vextis.shared.api.graphql;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
final class HealthGraphQlController {

    @QueryMapping
    Health health() {
        return new Health(ServiceStatus.UP);
    }

    record Health(ServiceStatus status) {
    }

    enum ServiceStatus {
        UP
    }
}
