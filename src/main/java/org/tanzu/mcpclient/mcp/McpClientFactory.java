package org.tanzu.mcpclient.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
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

    private HttpClient.Builder createHttpClientBuilder(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(connectTimeout);
    }
}