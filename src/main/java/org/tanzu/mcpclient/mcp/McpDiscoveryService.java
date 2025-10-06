package org.tanzu.mcpclient.mcp;

import io.pivotal.cfenv.boot.genai.GenaiLocator;
import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for discovering and managing Model Context Protocol (MCP) service connections.
 * Handles MCP service discovery from both GenaiLocator and Cloud Foundry service bindings.
 */
@Service
public class McpDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(McpDiscoveryService.class);

    // Constants for service binding keys
    public static final String MCP_SERVICE_URL = "mcpServiceURL";           // Legacy SSE support
    public static final String MCP_SSE_URL = "mcpSseURL";                   // Explicit SSE support
    public static final String MCP_STREAMABLE_URL = "mcpStreamableURL";     // Streamable HTTP support

    // Constants for tag-based discovery
    public static final String TAG_MCP_SSE = "mcpSseURL";
    public static final String TAG_MCP_STREAMABLE = "mcpStreamableURL";
    public static final String CREDENTIALS_URI_KEY = "uri";

    private final CfEnv cfEnv;
    private final GenaiLocator genaiLocator; // Optional - may be null

    /**
     * Constructor with optional GenaiLocator injection.
     * GenaiLocator is only available when GenaiLocatorAutoConfiguration is active
     * (i.e., when genai.locator.config-url property is set by CfGenaiProcessor).
     */
    public McpDiscoveryService(@Nullable GenaiLocator genaiLocator) {
        this.cfEnv = new CfEnv();
        this.genaiLocator = genaiLocator;

        if (genaiLocator != null) {
            logger.debug("GenaiLocator bean detected - will check for dynamic MCP service discovery");
        } else {
            logger.debug("No GenaiLocator bean available - using CF service-based MCP discovery only");
        }
    }

    /**
     * Gets MCP server URLs from GenaiLocator if available, otherwise returns empty list.
     * This is the new approach for MCP service discovery.
     */
    public List<String> getMcpServiceUrlsFromLocator() {
        if (genaiLocator == null) {
            return List.of();
        }
        try {
            return genaiLocator.getMcpServers().stream()
                    .map(GenaiLocator.McpConnectivity::url)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.debug("Error getting MCP service URLs from GenaiLocator: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Gets the names of MCP services from Cloud Foundry service bindings.
     * This is the legacy approach for MCP service discovery.
     */
    public List<String> getMcpServiceNames() {
        try {
            return cfEnv.findAllServices().stream()
                    .filter(this::hasMcpServiceUrl)
                    .map(CfService::getName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Error getting MCP service names: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Gets the URLs of MCP services from Cloud Foundry service bindings.
     * This is the legacy approach - new approach uses GenaiLocator.
     */
    public List<String> getMcpServiceUrls() {
        try {
            return cfEnv.findAllServices().stream()
                    .filter(this::hasMcpServiceUrl)
                    .map(service -> service.getCredentials().getString(MCP_SERVICE_URL))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Error getting MCP service URLs: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Gets all MCP service URLs from both sources (GenaiLocator and CF services).
     * This ensures compatibility with both old and new approaches.
     */
    public List<String> getAllMcpServiceUrls() {
        List<String> locatorUrls = getMcpServiceUrlsFromLocator();
        List<String> cfUrls = getMcpServiceUrls();

        // Combine both sources, preferring GenaiLocator if available
        if (!locatorUrls.isEmpty()) {
            logger.debug("Using MCP service URLs from GenaiLocator: {}", locatorUrls);
            return locatorUrls;
        } else if (!cfUrls.isEmpty()) {
            logger.debug("Using MCP service URLs from CF services: {}", cfUrls);
            return cfUrls;
        } else {
            logger.debug("No MCP service URLs found from any source");
            return List.of();
        }
    }

    /**
     * Checks if a Cloud Foundry service has an MCP service URL configured (legacy method).
     */
    public boolean hasMcpServiceUrl(CfService service) {
        CfCredentials credentials = service.getCredentials();
        return credentials != null && credentials.getString(MCP_SERVICE_URL) != null;
    }

    /**
     * Gets MCP services with protocol information from Cloud Foundry service bindings.
     * Returns services with their URLs and detected protocol types.
     */
    public List<McpServiceConfiguration> getMcpServicesWithProtocol() {
        try {
            return cfEnv.findAllServices().stream()
                    .map(this::extractMcpServiceConfiguration)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Error getting MCP services with protocol information: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Extracts MCP service configuration from a Cloud Foundry service binding.
     * First tries tag-based discovery (new approach), then falls back to credential-based discovery (legacy).
     */
    private McpServiceConfiguration extractMcpServiceConfiguration(CfService service) {
        // First, try tag-based discovery (new approach)
        McpServiceConfiguration tagBasedConfig = extractFromTags(service);
        if (tagBasedConfig != null) {
            return tagBasedConfig;
        }

        // Fall back to credential-based discovery (legacy support)
        return extractFromCredentials(service);
    }

    /**
     * Extracts MCP service configuration using tag-based discovery.
     * Looks for mcpStreamableURL or mcpSseURL tags, then reads the 'uri' key from credentials.
     */
    private McpServiceConfiguration extractFromTags(CfService service) {
        CfCredentials credentials = service.getCredentials();
        if (credentials == null) {
            return null;
        }

        // Check for mcpStreamableURL tag
        if (service.existsByTagIgnoreCase(TAG_MCP_STREAMABLE)) {
            String uri = credentials.getString(CREDENTIALS_URI_KEY);
            if (isValidUrl(uri)) {
                logger.debug("Found MCP Streamable service '{}' via tag with URI: {}",
                    service.getName(), uri);
                return new McpServiceConfiguration(
                    service.getName(),
                    uri,
                    new ProtocolType.StreamableHttp()
                );
            }
        }

        // Check for mcpSseURL tag
        if (service.existsByTagIgnoreCase(TAG_MCP_SSE)) {
            String uri = credentials.getString(CREDENTIALS_URI_KEY);
            if (isValidUrl(uri)) {
                logger.debug("Found MCP SSE service '{}' via tag with URI: {}",
                    service.getName(), uri);
                return new McpServiceConfiguration(
                    service.getName(),
                    uri,
                    new ProtocolType.SSE()
                );
            }
        }

        return null;
    }

    /**
     * Extracts MCP service configuration using legacy credential-based discovery.
     * Checks keys in priority order: mcpStreamableURL > mcpSseURL > mcpServiceURL
     */
    private McpServiceConfiguration extractFromCredentials(CfService service) {
        CfCredentials credentials = service.getCredentials();
        if (credentials == null) {
            return null;
        }

        Map<String, Object> credentialsMap = credentials.getMap();

        // Check keys in priority order
        if (credentialsMap.containsKey(MCP_STREAMABLE_URL)) {
            String url = (String) credentialsMap.get(MCP_STREAMABLE_URL);
            if (isValidUrl(url)) {
                logger.debug("Found legacy MCP Streamable service '{}' via credentials",
                    service.getName());
                return new McpServiceConfiguration(service.getName(), url, new ProtocolType.StreamableHttp());
            }
        }

        if (credentialsMap.containsKey(MCP_SSE_URL)) {
            String url = (String) credentialsMap.get(MCP_SSE_URL);
            if (isValidUrl(url)) {
                logger.debug("Found legacy MCP SSE service '{}' via credentials",
                    service.getName());
                return new McpServiceConfiguration(service.getName(), url, new ProtocolType.SSE());
            }
        }

        if (credentialsMap.containsKey(MCP_SERVICE_URL)) {
            // Legacy support - defaults to Legacy protocol (which uses SSE)
            String url = (String) credentialsMap.get(MCP_SERVICE_URL);
            if (isValidUrl(url)) {
                logger.debug("Found legacy MCP service '{}' via credentials with mcpServiceURL",
                    service.getName());
                return new McpServiceConfiguration(service.getName(), url, new ProtocolType.Legacy());
            }
        }

        return null;
    }

    /**
     * Validates that a URL string is not null or empty.
     */
    private boolean isValidUrl(String url) {
        return url != null && !url.trim().isEmpty();
    }

    /**
     * Record representing MCP service configuration with protocol information.
     */
    public record McpServiceConfiguration(
            String serviceName,
            String serverUrl,
            ProtocolType protocol
    ) {}
}