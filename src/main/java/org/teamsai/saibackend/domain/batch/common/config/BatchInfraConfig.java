package org.teamsai.saibackend.domain.batch.common.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        name = "sai.batch.enabled",
        havingValue = "true"
)
@EnableBatchProcessing
@EnableJdbcJobRepository
public class BatchInfraConfig {
}