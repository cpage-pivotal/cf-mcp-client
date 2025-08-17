package org.tanzu.mcpclient.config;

import io.pivotal.cfenv.boot.genai.DefaultGenaiLocator;
import io.pivotal.cfenv.boot.genai.GenaiLocator;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Manual configuration for multiple GenaiLocator beans
 * This bypasses the java-cfenv limitation by directly reading VCAP_SERVICES
 * and creating GenaiLocator beans for each GenAI service found.
 */
@Configuration
public class MultiGenaiLocatorConfiguration {

    /**
     * Creates multiple GenaiLocator beans by directly parsing VCAP_SERVICES
     * This works with your existing MultiGenaiLocatorAggregator
     */
    @Bean
    @ConditionalOnProperty(name = "VCAP_SERVICES") // Only when running in CF
    public List<GenaiLocator> manualGenaiLocators(RestClient.Builder builder) {
        CfEnv cfEnv = new CfEnv();

        return cfEnv.findAllServices().stream()
                .filter(this::isGenaiService)
                .map(service -> createGenaiLocator(service, builder))
                .toList();
    }

    /**
     * Checks if a service is a GenAI service
     */
    private boolean isGenaiService(CfService service) {
        boolean hasGenaiTag = service.existsByTagIgnoreCase("genai") ||
                service.existsByLabelStartsWith("genai");
        boolean hasEndpoint = service.getCredentials().getMap().containsKey("endpoint");
        return hasGenaiTag && hasEndpoint;
    }

    /**
     * Creates a GenaiLocator from a CfService
     */
    private GenaiLocator createGenaiLocator(CfService service, RestClient.Builder builder) {
        Map<String, Object> credentials = service.getCredentials().getMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> endpoint = (Map<String, Object>) credentials.get("endpoint");

        String configUrl = (String) endpoint.get("config_url");
        String apiKey = (String) endpoint.get("api_key");
        String apiBase = (String) endpoint.get("api_base");

        return new DefaultGenaiLocator(builder, configUrl, apiKey, apiBase);
    }
}

/*
 * ALTERNATIVE APPROACH: Environment-based configuration
 * If you can set environment variables, this approach works too
 */
@Configuration
class EnvironmentBasedGenaiLocatorConfiguration {

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

/*
 * If you want to set these manually via environment variables or application.yml:
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