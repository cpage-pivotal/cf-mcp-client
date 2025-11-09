package org.tanzu.mcpclient.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolsChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Utility factory for creating MCP clients with consistent configuration.
 * This factory centralizes the MCP client creation logic to ensure
 * all parts of the application use the same client configuration.
 *
 * Supports Spring AI 1.1.0-RC1 event-driven tool callback caching by
 * publishing McpToolsChangedEvent when MCP server tools change.
 */
@Component
public class McpClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(McpClientFactory.class);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(10);

    private final SSLContext sslContext;
    private final ApplicationEventPublisher eventPublisher;

    public McpClientFactory(SSLContext sslContext, ApplicationEventPublisher eventPublisher) {
        this.sslContext = sslContext;
        this.eventPublisher = eventPublisher;
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
     * Registers a tools change consumer to publish McpToolsChangedEvent for cache invalidation.
     */
    public McpSyncClient createSseClient(String serverUrl, Duration connectTimeout, Duration requestTimeout) {
        HttpClient.Builder clientBuilder = createHttpClientBuilder(connectTimeout);

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(serverUrl)
                .clientBuilder(clientBuilder)
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .build();

        return McpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .toolsChangeConsumer(tools -> {
                    logger.info("MCP server {} tools changed, publishing event (new tool count: {})",
                            serverUrl, tools.size());
                    eventPublisher.publishEvent(new McpToolsChangedEvent(serverUrl, tools));
                })
                .build();
    }

    /**
     * Creates a new MCP synchronous client using Streamable HTTP protocol with custom timeout configuration.
     * Registers a tools change consumer to publish McpToolsChangedEvent for cache invalidation.
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
                .toolsChangeConsumer(tools -> {
                    logger.info("MCP server {} tools changed, publishing event (new tool count: {})",
                            serverUrl, tools.size());
                    eventPublisher.publishEvent(new McpToolsChangedEvent(serverUrl, tools));
                })
                .build();
    }

    private HttpClient.Builder createHttpClientBuilder(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(connectTimeout);
    }
}