package com.vextis.shared.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "vextis.exposure", havingValue = "PUBLIC")
class FirebaseConfiguration {

    @Bean
    FirebaseAuth firebaseAuth(@Value("${vextis.firebase.project-id}") String projectId) throws IOException {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("vextis.firebase.project-id is required for PUBLIC exposure.");
        }
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(projectId)
                .build();
        return FirebaseAuth.getInstance(FirebaseApp.initializeApp(options, "vextis-public-api"));
    }
}
