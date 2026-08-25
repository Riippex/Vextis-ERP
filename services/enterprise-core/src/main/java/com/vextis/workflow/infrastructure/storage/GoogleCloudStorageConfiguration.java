package com.vextis.workflow.infrastructure.storage;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GoogleCloudStorageConfiguration {
    @Bean
    Storage googleCloudStorage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}
