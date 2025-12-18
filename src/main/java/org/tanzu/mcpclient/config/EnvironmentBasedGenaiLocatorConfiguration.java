package org.tanzu.mcpclient.config;

import io.pivotal.cfenv.boot.genai.DefaultGenaiLocator;
import io.pivotal.cfenv.boot.genai.GenaiLocator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Environment-based configuration for GenaiLocator beans
 * If you can set environment variables, this approach works too
 *
 * Environment variables example:
 * GENAI_EMBEDDING_CONFIG_URL=https://genai-proxy.sys.tas-ndc.kuhn-labs.com/prod-embedding-nomic-text-97b9b92/config/v1/endpoint
 * GENAI_EMBEDDING_API_KEY=eyJhbGciOiJIUzI1NiJ9...
 * GENAI_EMBEDDING_API_BASE=https://genai-proxy.sys.tas-ndc.kuhn-labs.com/prod-embedding-nomic-text-97b9b92
 *
 * GENAI_CHAT_CONFIG_URL=https://genai-proxy.sys.tas-ndc.kuhn-labs.com/local-mistral-nemo-instruct-2407-5c3c88c/config/v1/endpoint
 * GENAI_CHAT_API_KEY=eyJhbGciOiJIUzI1NiJ9...
 * GENAI_CHAT_API_BASE=https://genai-proxy.sys.tas-ndc.kuhn-labs.com/local-mistral-nemo-instruct-2407-5c3c88c
 */
@Configuration
public class EnvironmentBasedGenaiLocatorConfiguration {

    @Bean
    @ConditionalOnProperty("genai.embedding.config-url")
    public GenaiLocator embeddingGenaiLocator(
            RestClient.Builder builder,
            @Value("${genai.embedding.config-url}") String configUrl,
            @Value("${genai.embedding.api-key}") String apiKey,
            @Value("${genai.embedding.api-base}") String apiBase) {
        return new DefaultGenaiLocator(builder, configUrl, apiKey, apiBase);
    }

    @Bean
    @ConditionalOnProperty("genai.chat.config-url")
    public GenaiLocator chatGenaiLocator(
            RestClient.Builder builder,
            @Value("${genai.chat.config-url}") String configUrl,
            @Value("${genai.chat.api-key}") String apiKey,
            @Value("${genai.chat.api-base}") String apiBase) {
        return new DefaultGenaiLocator(builder, configUrl, apiKey, apiBase);
    }
}
