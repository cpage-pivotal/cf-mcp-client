package org.tanzu.mcpclient.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Utility factory for creating MCP clients with consistent configuration.
 * This factory centralizes the MCP client creation logic to ensure
 * all parts of the application use the same client configuration.
 */
@Component
public class McpClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(McpClientFactory.class);

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(10);

    private final SSLContext sslContext;

    public McpClientFactory(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Creates a new MCP synchronous client for the specified server URL with default timeouts.
     */
    public McpSyncClient createMcpSyncClient(String serverUrl) {
        return createMcpSyncClient(serverUrl, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Creates a new MCP synchronous client optimized for health checks (shorter timeouts).
     */
    public McpSyncClient createHealthCheckClient(String serverUrl) {
        return createMcpSyncClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT);
    }

    /**
     * Creates a new MCP synchronous client for health checks with specified protocol.
     */
    public McpSyncClient createHealthCheckClient(String serverUrl, ProtocolType protocol) {
        return switch (protocol) {
            case ProtocolType.StreamableHttp streamableHttp ->
                    createStreamableClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT);
            case ProtocolType.SSE sse ->
                    createSseClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT);
            case ProtocolType.Legacy legacy ->
                    createSseClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT);
        };
    }

    /**
     * Creates a new MCP synchronous client with custom timeout configuration using SSE protocol.
     * @deprecated Use createSseClient instead for clarity
     */
    @Deprecated
    public McpSyncClient createMcpSyncClient(String serverUrl, Duration connectTimeout, Duration requestTimeout) {
        return createSseClient(serverUrl, connectTimeout, requestTimeout);
    }

    /**
     * Creates a new MCP synchronous client using SSE protocol with custom timeout configuration.
     */
    public McpSyncClient createSseClient(String serverUrl, Duration connectTimeout, Duration requestTimeout) {
        HttpClient.Builder clientBuilder = createHttpClientBuilder(connectTimeout);

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(serverUrl)
                .clientBuilder(clientBuilder)
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .build();

        return McpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .build();
    }

    /**
     * Creates a new MCP synchronous client using Streamable HTTP protocol with custom timeout configuration.
     */
    public McpSyncClient createStreamableClient(String serverUrl, Duration connectTimeout, Duration requestTimeout) {
        HttpClient.Builder clientBuilder = createHttpClientBuilder(connectTimeout);

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(serverUrl)
                .clientBuilder(clientBuilder)
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .resumableStreams(true)
                .build();

        return McpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .build();
    }

    /**
     * Creates an SSE client with automatic endpoint detection.
     * First tries without suffix, then falls back to /sse if that fails.
     */
    public McpSyncClient createSseClientWithFallback(String serverUrl,
                                                     Duration connectTimeout,
                                                     Duration requestTimeout) {
        // First attempt: Try without suffix (empty string endpoint)
        McpSyncClient client = tryCreateSseClient(serverUrl, "", connectTimeout, requestTimeout);
        if (client != null) {
            logger.info("Successfully connected to SSE server without suffix: {}", serverUrl);
            return client;
        }

        // Second attempt: Try with default /sse suffix
        client = tryCreateSseClient(serverUrl, "/sse", connectTimeout, requestTimeout);
        if (client != null) {
            logger.info("Successfully connected to SSE server with /sse suffix: {}", serverUrl);
            return client;
        }

        // Both attempts failed - throw exception
        throw new McpConnectionException(
            String.format("Failed to connect to SSE server at %s (tried with and without /sse suffix)", serverUrl)
        );
    }

    /**
     * Creates a Streamable HTTP client with automatic endpoint detection.
     * First tries without suffix, then falls back to /mcp if that fails.
     */
    public McpSyncClient createStreamableClientWithFallback(String serverUrl,
                                                           Duration connectTimeout,
                                                           Duration requestTimeout) {
        // First attempt: Try without suffix (empty string endpoint)
        McpSyncClient client = tryCreateStreamableClient(serverUrl, "", connectTimeout, requestTimeout);
        if (client != null) {
            logger.info("Successfully connected to Streamable server without suffix: {}", serverUrl);
            return client;
        }

        // Second attempt: Try with default /mcp suffix
        client = tryCreateStreamableClient(serverUrl, "/mcp", connectTimeout, requestTimeout);
        if (client != null) {
            logger.info("Successfully connected to Streamable server with /mcp suffix: {}", serverUrl);
            return client;
        }

        // Both attempts failed - throw exception
        throw new McpConnectionException(
            String.format("Failed to connect to Streamable HTTP server at %s (tried with and without /mcp suffix)", serverUrl)
        );
    }

    /**
     * Attempts to create an SSE client with the specified endpoint suffix.
     * Returns null if connection fails.
     */
    private McpSyncClient tryCreateSseClient(String serverUrl, String endpoint,
                                            Duration connectTimeout, Duration requestTimeout) {
        try {
            HttpClient.Builder clientBuilder = createHttpClientBuilder(connectTimeout);

            HttpClientSseClientTransport.Builder transportBuilder =
                HttpClientSseClientTransport.builder(serverUrl)
                    .clientBuilder(clientBuilder)
                    .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()));

            // Set the endpoint - empty string means no suffix
            if (endpoint != null && !endpoint.isEmpty()) {
                transportBuilder.sseEndpoint(endpoint);
            } else {
                // Use empty endpoint to connect directly to base URL
                transportBuilder.sseEndpoint("");
            }

            HttpClientSseClientTransport transport = transportBuilder.build();

            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(requestTimeout)
                    .build();

            // Test the connection by attempting initialization
            if (testConnection(client)) {
                return client;
            } else {
                client.closeGracefully();
                return null;
            }
        } catch (Exception e) {
            logger.debug("Failed to create SSE client with endpoint '{}': {}", endpoint, e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to create a Streamable HTTP client with the specified endpoint suffix.
     * Returns null if connection fails.
     */
    private McpSyncClient tryCreateStreamableClient(String serverUrl, String endpoint,
                                                   Duration connectTimeout, Duration requestTimeout) {
        try {
            HttpClient.Builder clientBuilder = createHttpClientBuilder(connectTimeout);

            HttpClientStreamableHttpTransport.Builder transportBuilder =
                HttpClientStreamableHttpTransport.builder(serverUrl)
                    .clientBuilder(clientBuilder)
                    .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                    .resumableStreams(true);

            // Set the endpoint - empty string means no suffix
            if (endpoint != null && !endpoint.isEmpty()) {
                transportBuilder.endpoint(endpoint);
            } else {
                // Use empty endpoint to connect directly to base URL
                transportBuilder.endpoint("");
            }

            HttpClientStreamableHttpTransport transport = transportBuilder.build();

            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(requestTimeout)
                    .build();

            // Test the connection by attempting initialization
            if (testConnection(client)) {
                return client;
            } else {
                client.closeGracefully();
                return null;
            }
        } catch (Exception e) {
            logger.debug("Failed to create Streamable client with endpoint '{}': {}", endpoint, e.getMessage());
            return null;
        }
    }

    /**
     * Tests if a client can successfully connect by attempting initialization.
     */
    private boolean testConnection(McpSyncClient client) {
        try {
            McpSchema.InitializeResult result = client.initialize();
            return result != null && result.protocolVersion() != null;
        } catch (Exception e) {
            logger.debug("Connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private HttpClient.Builder createHttpClientBuilder(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(connectTimeout);
    }
}