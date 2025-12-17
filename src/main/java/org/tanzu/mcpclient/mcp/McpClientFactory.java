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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
        return createHealthCheckClient(serverUrl, protocol, Map.of());
    }

    /**
     * Creates a new MCP synchronous client for health checks with specified protocol and headers.
     */
    public McpSyncClient createHealthCheckClient(String serverUrl, ProtocolType protocol, Map<String, String> headers) {
        return switch (protocol) {
            case ProtocolType.StreamableHttp streamableHttp ->
                    createStreamableClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT, headers);
            case ProtocolType.SSE sse ->
                    createSseClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT, headers);
            case ProtocolType.Legacy legacy ->
                    createSseClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT, headers);
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
        return createSseClient(serverUrl, connectTimeout, requestTimeout, Map.of());
    }

    /**
     * Creates a new MCP synchronous client using SSE protocol with custom timeout configuration and headers.
     * Registers a tools change consumer to publish McpToolsChangedEvent for cache invalidation.
     */
    public McpSyncClient createSseClient(String serverUrl, Duration connectTimeout, Duration requestTimeout, Map<String, String> headers) {
        HttpClient.Builder clientBuilder = createHttpClientBuilder(connectTimeout, headers);

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
        return createStreamableClient(serverUrl, connectTimeout, requestTimeout, Map.of());
    }

    /**
     * Creates a new MCP synchronous client using Streamable HTTP protocol with custom timeout configuration and headers.
     * Registers a tools change consumer to publish McpToolsChangedEvent for cache invalidation.
     */
    public McpSyncClient createStreamableClient(String serverUrl, Duration connectTimeout, Duration requestTimeout, Map<String, String> headers) {
        HttpClient.Builder clientBuilder = createHttpClientBuilder(connectTimeout, headers);

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
        return createHttpClientBuilder(connectTimeout, Map.of());
    }

    /**
     * Creates an HttpClient.Builder with headers support.
     * Since HttpClient.Builder doesn't support default headers directly, we create a custom
     * builder that wraps the base builder and produces a client that adds headers to all requests.
     */
    private HttpClient.Builder createHttpClientBuilder(Duration connectTimeout, Map<String, String> headers) {
        HttpClient.Builder baseBuilder = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(connectTimeout);

        if (headers.isEmpty()) {
            return baseBuilder;
        }

        // Create a custom builder that wraps the base builder and produces a client with headers
        return new HttpClient.Builder() {
            @Override
            public HttpClient build() {
                HttpClient baseClient = baseBuilder.build();
                return createHttpClientWithHeaders(baseClient, headers);
            }

            @Override
            public HttpClient.Builder sslContext(SSLContext sslContext) {
                baseBuilder.sslContext(sslContext);
                return this;
            }

            @Override
            public HttpClient.Builder sslParameters(javax.net.ssl.SSLParameters sslParameters) {
                baseBuilder.sslParameters(sslParameters);
                return this;
            }

            @Override
            public HttpClient.Builder executor(java.util.concurrent.Executor executor) {
                baseBuilder.executor(executor);
                return this;
            }

            @Override
            public HttpClient.Builder followRedirects(HttpClient.Redirect policy) {
                baseBuilder.followRedirects(policy);
                return this;
            }

            @Override
            public HttpClient.Builder version(HttpClient.Version version) {
                baseBuilder.version(version);
                return this;
            }

            @Override
            public HttpClient.Builder priority(int priority) {
                baseBuilder.priority(priority);
                return this;
            }

            @Override
            public HttpClient.Builder proxy(java.net.ProxySelector proxySelector) {
                baseBuilder.proxy(proxySelector);
                return this;
            }

            @Override
            public HttpClient.Builder authenticator(java.net.Authenticator authenticator) {
                baseBuilder.authenticator(authenticator);
                return this;
            }

            @Override
            public HttpClient.Builder connectTimeout(Duration duration) {
                baseBuilder.connectTimeout(duration);
                return this;
            }

            @Override
            public HttpClient.Builder cookieHandler(java.net.CookieHandler cookieHandler) {
                baseBuilder.cookieHandler(cookieHandler);
                return this;
            }
        };
    }

    /**
     * Creates an HttpClient that wraps requests to add custom headers.
     * This is a workaround since HttpClient.Builder doesn't support default headers.
     */
    private HttpClient createHttpClientWithHeaders(HttpClient baseClient, Map<String, String> headers) {
        return new HttpClient() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws java.io.IOException, InterruptedException {
                HttpRequest requestWithHeaders = addHeadersToRequest(request, headers);
                return baseClient.send(requestWithHeaders, responseBodyHandler);
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
                HttpRequest requestWithHeaders = addHeadersToRequest(request, headers);
                return baseClient.sendAsync(requestWithHeaders, responseBodyHandler);
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
                HttpRequest requestWithHeaders = addHeadersToRequest(request, headers);
                return baseClient.sendAsync(requestWithHeaders, responseBodyHandler, pushPromiseHandler);
            }

            @Override
            public java.net.http.HttpClient.Version version() {
                return baseClient.version();
            }

            @Override
            public java.util.Optional<java.net.ProxySelector> proxy() {
                return baseClient.proxy();
            }

            @Override
            public java.util.Optional<java.net.CookieHandler> cookieHandler() {
                return baseClient.cookieHandler();
            }

            @Override
            public java.util.Optional<java.util.concurrent.Executor> executor() {
                return baseClient.executor();
            }

            @Override
            public java.util.Optional<java.net.Authenticator> authenticator() {
                return baseClient.authenticator();
            }

            @Override
            public java.net.http.HttpClient.Redirect followRedirects() {
                return baseClient.followRedirects();
            }

            @Override
            public javax.net.ssl.SSLContext sslContext() {
                return baseClient.sslContext();
            }

            @Override
            public javax.net.ssl.SSLParameters sslParameters() {
                return baseClient.sslParameters();
            }

            @Override
            public java.util.Optional<java.time.Duration> connectTimeout() {
                return baseClient.connectTimeout();
            }
        };
    }

    /**
     * Adds headers to an HttpRequest, creating a new request with the headers merged.
     */
    private HttpRequest addHeadersToRequest(HttpRequest originalRequest, Map<String, String> headers) {
        if (headers.isEmpty()) {
            return originalRequest;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(originalRequest.uri())
                .method(originalRequest.method(), originalRequest.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        
        // Only set version if present (version() doesn't accept null)
        originalRequest.version().ifPresent(requestBuilder::version);
        
        // Set timeout if present
        originalRequest.timeout().ifPresent(requestBuilder::timeout);
        
        // Set expectContinue
        if (originalRequest.expectContinue()) {
            requestBuilder.expectContinue(true);
        }

        // Copy existing headers
        originalRequest.headers().map().forEach((name, values) -> {
            for (String value : values) {
                requestBuilder.header(name, value);
            }
        });

        // Add new headers (only if not already present)
        headers.forEach((name, value) -> {
            if (!originalRequest.headers().firstValue(name).isPresent()) {
                requestBuilder.header(name, value);
            }
        });

        return requestBuilder.build();
    }
}