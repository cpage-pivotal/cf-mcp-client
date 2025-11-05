package org.tanzu.mcpclient.a2a;

import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for discovering A2A (Agent2Agent) agents from Cloud Foundry service bindings.
 *
 * Service Binding Pattern:
 * cf cups a2a-server -p '{"uri":"https://example.com/.well-known/agent.json"}' -t "a2a"
 */
@Service
public class A2ADiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(A2ADiscoveryService.class);

    // Constants for service binding keys
    public static final String A2A_TAG = "a2a";
    public static final String AGENT_CARD_URI_KEY = "uri";

    private final CfEnv cfEnv;

    /**
     * Constructor initializes Cloud Foundry environment access.
     */
    public A2ADiscoveryService() {
        this.cfEnv = new CfEnv();
        logger.debug("A2ADiscoveryService initialized");
    }

    /**
     * Gets the list of agent card URIs from Cloud Foundry service bindings.
     * Filters services by tag "a2a" and extracts the "uri" credential.
     *
     * @return List of agent card URLs (typically /.well-known/agent.json endpoints)
     */
    public List<String> getAgentCardUris() {
        try {
            List<String> uris = cfEnv.findAllServices().stream()
                    .filter(this::isA2AService)
                    .map(this::extractAgentCardUri)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            logger.debug("Found {} A2A agent card URIs: {}", uris.size(), uris);
            return uris;
        } catch (Exception e) {
            logger.warn("Error getting A2A agent card URIs: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Gets the names of A2A services from Cloud Foundry service bindings.
     * Returns service names for display purposes.
     *
     * @return List of A2A service names
     */
    public List<String> getA2AServiceNames() {
        try {
            List<String> names = cfEnv.findAllServices().stream()
                    .filter(this::isA2AService)
                    .map(CfService::getName)
                    .collect(Collectors.toList());

            logger.debug("Found {} A2A services: {}", names.size(), names);
            return names;
        } catch (Exception e) {
            logger.warn("Error getting A2A service names: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Gets both service names and agent card URIs together.
     * Useful for initialization when both pieces of information are needed.
     *
     * @return List of A2AServiceInfo records containing service name and agent card URI
     */
    public List<A2AServiceInfo> getA2AServices() {
        try {
            List<A2AServiceInfo> services = cfEnv.findAllServices().stream()
                    .filter(this::isA2AService)
                    .map(this::extractServiceInfo)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            logger.debug("Found {} A2A services", services.size());
            return services;
        } catch (Exception e) {
            logger.warn("Error getting A2A services: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Checks if a Cloud Foundry service is tagged as an A2A service.
     *
     * @param service Cloud Foundry service to check
     * @return true if service has "a2a" tag
     */
    private boolean isA2AService(CfService service) {
        return service.existsByTagIgnoreCase(A2A_TAG);
    }

    /**
     * Extracts the agent card URI from a Cloud Foundry service's credentials.
     *
     * @param service Cloud Foundry service
     * @return Agent card URI or null if not found
     */
    private String extractAgentCardUri(CfService service) {
        CfCredentials credentials = service.getCredentials();
        if (credentials == null) {
            logger.warn("Service '{}' has no credentials", service.getName());
            return null;
        }

        String uri = credentials.getString(AGENT_CARD_URI_KEY);
        if (!isValidUri(uri)) {
            logger.warn("Service '{}' has invalid or missing '{}' credential",
                service.getName(), AGENT_CARD_URI_KEY);
            return null;
        }

        logger.debug("Found A2A service '{}' with URI: {}", service.getName(), uri);
        return uri;
    }

    /**
     * Extracts service information including both name and agent card URI.
     *
     * @param service Cloud Foundry service
     * @return A2AServiceInfo record or null if URI not found
     */
    private A2AServiceInfo extractServiceInfo(CfService service) {
        String uri = extractAgentCardUri(service);
        if (uri == null) {
            return null;
        }
        return new A2AServiceInfo(service.getName(), uri);
    }

    /**
     * Validates that a URI string is not null or empty.
     *
     * @param uri URI to validate
     * @return true if URI is valid
     */
    private boolean isValidUri(String uri) {
        return uri != null && !uri.trim().isEmpty();
    }

    /**
     * Record representing A2A service information.
     *
     * @param serviceName Cloud Foundry service name
     * @param agentCardUri Agent card URL (typically /.well-known/agent.json)
     */
    public record A2AServiceInfo(
        String serviceName,
        String agentCardUri
    ) {}
}
