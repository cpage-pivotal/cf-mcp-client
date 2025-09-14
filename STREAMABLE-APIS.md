# Spring AI & MCP Java SDK API Reference for Streamable HTTP Implementation

This document provides the essential API reference for implementing Streamable HTTP Protocol support in cf-mcp-client using Spring AI 1.1.0-M1 and MCP Java SDK 0.12.1.

## Table of Contents

1. [MCP Java SDK Transport APIs](#mcp-java-sdk-transport-apis)
2. [Spring AI MCP Client APIs](#spring-ai-mcp-client-apis)
3. [Builder Patterns and Configuration](#builder-patterns-and-configuration)
4. [Protocol Types and Enums](#protocol-types-and-enums)
5. [Error Handling](#error-handling)
6. [Usage Examples](#usage-examples)

## MCP Java SDK Transport APIs

### HttpClientStreamableHttpTransport

**Package**: `io.modelcontextprotocol.client.transport`

The new Streamable HTTP transport implementation that replaces SSE for the 2025-03-26 MCP protocol specification.

#### Key Features
- Supports stateful servers with session management
- Can communicate with stateless servers
- Uses HTTP POST and GET requests with optional SSE streaming
- Handles multiple client connections
- Session-based connection management

#### Builder Pattern

```java
public class HttpClientStreamableHttpTransport implements McpClientTransport {
    
    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }
    
    public static class Builder {
        public Builder clientBuilder(HttpClient.Builder clientBuilder);
        public Builder objectMapper(ObjectMapper objectMapper);
        public Builder endpoint(String endpoint); // Default: "/mcp"
        public Builder resumableStreams(boolean resumableStreams);
        public Builder httpRequestCustomizer(McpSyncHttpClientRequestCustomizer customizer);
        public HttpClientStreamableHttpTransport build();
    }
}
```

#### Usage Example

```java
// Create Streamable HTTP transport
HttpClientStreamableHttpTransport transport = 
    HttpClientStreamableHttpTransport.builder("https://server.example.com")
        .clientBuilder(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)))
        .objectMapper(new ObjectMapper())
        .resumableStreams(true)
        .build();
```

### HttpClientSseClientTransport

**Package**: `io.modelcontextprotocol.client.transport`

The existing SSE transport implementation for backward compatibility with 2024-11-05 MCP protocol.

#### Builder Pattern

```java
public class HttpClientSseClientTransport implements McpClientTransport {
    
    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }
    
    public static class Builder {
        public Builder clientBuilder(HttpClient.Builder clientBuilder);
        public Builder objectMapper(ObjectMapper objectMapper);
        public Builder sseEndpoint(String sseEndpoint); // Default: "/sse"
        public Builder httpRequestCustomizer(McpAsyncHttpClientRequestCustomizer customizer);
        public HttpClientSseClientTransport build();
    }
}
```

## Spring AI MCP Client APIs

### McpClient Factory Methods

**Package**: `io.modelcontextprotocol.client`

Main entry point for creating MCP clients with specific transports.

```java
public class McpClient {
    
    // Create synchronous client
    public static McpSyncClient.Builder sync(McpClientTransport transport);
    
    // Create asynchronous client  
    public static McpAsyncClient.Builder async(McpClientTransport transport);
}
```

### McpSyncClient

**Package**: `io.modelcontextprotocol.client`

Synchronous MCP client interface for blocking operations.

#### Key Methods

```java
public interface McpSyncClient extends AutoCloseable {
    
    // Core MCP operations
    McpSchema.InitializeResult initialize();
    McpSchema.ListToolsResult listTools();
    McpSchema.CallToolResult callTool(McpSchema.CallToolRequest request);
    
    // Resource operations
    McpSchema.ListResourcesResult listResources();
    McpSchema.ReadResourceResult readResource(McpSchema.ReadResourceRequest request);
    
    // Connection management
    void closeGracefully();
    @Override
    void close();
    
    // Builder for client configuration
    interface Builder {
        Builder requestTimeout(Duration timeout);
        Builder transportContextProvider(Supplier<McpTransportContext> provider);
        McpSyncClient build();
    }
}
```

### McpAsyncClient

**Package**: `io.modelcontextprotocol.client`

Asynchronous MCP client interface for reactive operations.

#### Key Methods

```java
public interface McpAsyncClient extends AutoCloseable {
    
    // Core MCP operations (returning Mono)
    Mono<McpSchema.InitializeResult> initialize();
    Mono<McpSchema.ListToolsResult> listTools();
    Mono<McpSchema.CallToolResult> callTool(McpSchema.CallToolRequest request);
    
    // Resource operations
    Mono<McpSchema.ListResourcesResult> listResources();
    Mono<McpSchema.ReadResourceResult> readResource(McpSchema.ReadResourceRequest request);
    
    // Connection management
    Mono<Void> closeGracefully();
    @Override
    void close();
}
```

## Builder Patterns and Configuration

### Common Builder Pattern

All transport implementations follow a consistent builder pattern:

```java
// SSE Transport
HttpClientSseClientTransport sseTransport = HttpClientSseClientTransport
    .builder("https://server.example.com")
    .clientBuilder(httpClientBuilder)
    .objectMapper(objectMapper)
    .sseEndpoint("/sse")
    .build();

// Streamable HTTP Transport  
HttpClientStreamableHttpTransport streamableTransport = HttpClientStreamableHttpTransport
    .builder("https://server.example.com")
    .clientBuilder(httpClientBuilder)
    .objectMapper(objectMapper)
    .endpoint("/mcp")
    .resumableStreams(true)
    .build();

// MCP Client
McpSyncClient client = McpClient.sync(transport)
    .requestTimeout(Duration.ofMinutes(5))
    .build();
```

### HttpClient Configuration

```java
private HttpClient.Builder createHttpClientBuilder(Duration connectTimeout) {
    return HttpClient.newBuilder()
        .sslContext(sslContext)
        .connectTimeout(connectTimeout);
}
```

## Protocol Types and Enums

### Protocol Type Enumeration

For distinguishing between transport protocols in your application:

```java
public enum ProtocolType {
    SSE("SSE"),
    STREAMABLE_HTTP("Streamable HTTP");
    
    private final String displayName;
    
    ProtocolType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
```

### MCP Protocol Versions

```java
public class ProtocolVersions {
    public static final String MCP_2024_11_05 = "2024-11-05"; // SSE Protocol
    public static final String MCP_2025_03_26 = "2025-03-26"; // Streamable HTTP Protocol
}
```

## Error Handling

### Transport Exceptions

```java
// Session-related exceptions (Streamable HTTP)
public class McpTransportSessionNotFoundException extends McpTransportException {
    public McpTransportSessionNotFoundException(String message);
}

// General transport exceptions
public class McpTransportException extends RuntimeException {
    public McpTransportException(String message);
    public McpTransportException(String message, Throwable cause);
}
```

### Exception Handling Pattern

```java
try {
    McpSchema.InitializeResult result = client.initialize();
    // Handle successful initialization
} catch (McpTransportSessionNotFoundException e) {
    // Handle session not found (invalidate session, retry)
    logger.warn("Session not found, creating new session: {}", e.getMessage());
} catch (McpTransportException e) {
    // Handle other transport errors
    logger.error("Transport error: {}", e.getMessage(), e);
} catch (Exception e) {
    // Handle unexpected errors
    logger.error("Unexpected error: {}", e.getMessage(), e);
}
```

## Usage Examples

### Factory Method Implementation

```java
@Component
public class McpClientFactory {
    
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(5);
    
    public McpSyncClient createSseClient(String serverUrl, Duration connectTimeout, Duration requestTimeout) {
        HttpClient.Builder clientBuilder = createHttpClientBuilder(connectTimeout);
        
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(serverUrl)
            .clientBuilder(clientBuilder)
            .objectMapper(new ObjectMapper())
            .build();
        
        return McpClient.sync(transport)
            .requestTimeout(requestTimeout)
            .build();
    }
    
    public McpSyncClient createStreamableClient(String serverUrl, Duration connectTimeout, Duration requestTimeout) {
        HttpClient.Builder clientBuilder = createHttpClientBuilder(connectTimeout);
        
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(serverUrl)
            .clientBuilder(clientBuilder)
            .objectMapper(new ObjectMapper())
            .resumableStreams(true)
            .build();
        
        return McpClient.sync(transport)
            .requestTimeout(requestTimeout)
            .build();
    }
    
    public McpSyncClient createHealthCheckClient(String serverUrl, ProtocolType protocol) {
        Duration healthCheckTimeout = Duration.ofSeconds(10);
        
        switch (protocol) {
            case SSE:
                return createSseClient(serverUrl, healthCheckTimeout, healthCheckTimeout);
            case STREAMABLE_HTTP:
                return createStreamableClient(serverUrl, healthCheckTimeout, healthCheckTimeout);
            default:
                throw new IllegalArgumentException("Unsupported protocol type: " + protocol);
        }
    }
    
    private HttpClient.Builder createHttpClientBuilder(Duration connectTimeout) {
        return HttpClient.newBuilder()
            .sslContext(sslContext)
            .connectTimeout(connectTimeout);
    }
}
```

### Service Layer Integration

```java
public class McpServerService {
    
    private final String name;
    private final String serverUrl;
    private final ProtocolType protocol;
    private final McpClientFactory clientFactory;
    
    public McpServerService(String name, String serverUrl, ProtocolType protocol, McpClientFactory clientFactory) {
        this.name = name;
        this.serverUrl = serverUrl;
        this.protocol = protocol;
        this.clientFactory = clientFactory;
    }
    
    public McpSyncClient createMcpSyncClient() {
        return switch (protocol) {
            case SSE -> clientFactory.createSseClient(serverUrl, 
                Duration.ofSeconds(30), Duration.ofMinutes(5));
            case STREAMABLE_HTTP -> clientFactory.createStreamableClient(serverUrl, 
                Duration.ofSeconds(30), Duration.ofMinutes(5));
        };
    }
    
    public McpServer getHealthyMcpServer() {
        try (McpSyncClient client = clientFactory.createHealthCheckClient(serverUrl, protocol)) {
            McpSchema.InitializeResult initResult = client.initialize();
            McpSchema.ListToolsResult toolsResult = client.listTools();
            
            List<Tool> tools = toolsResult.tools().stream()
                .map(this::convertToTool)
                .collect(Collectors.toList());
            
            return new McpServer(name, serverUrl, true, tools, protocol);
        } catch (Exception e) {
            logger.warn("Health check failed for MCP server {}: {}", name, e.getMessage());
            return new McpServer(name, serverUrl, false, Collections.emptyList(), protocol);
        }
    }
}
```

### Service Factory Pattern

```java
@Component
public class McpServiceFactory {
    
    private final McpClientFactory clientFactory;
    
    public McpServiceFactory(McpClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }
    
    public List<McpServerService> createMcpServices(VcapServices vcapServices) {
        return vcapServices.findServicesByTagOrServiceBrokerUrl("mcp")
            .stream()
            .map(this::createMcpService)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    private McpServerService createMcpService(CfService service) {
        Map<String, Object> credentials = service.getCredentials().getMap();
        
        // Check keys in priority order for protocol detection
        if (credentials.containsKey("mcpStreamableURL")) {
            String url = (String) credentials.get("mcpStreamableURL");
            return new McpServerService(service.getName(), url, ProtocolType.STREAMABLE_HTTP, clientFactory);
        } else if (credentials.containsKey("mcpSseURL")) {
            String url = (String) credentials.get("mcpSseURL");
            return new McpServerService(service.getName(), url, ProtocolType.SSE, clientFactory);
        } else if (credentials.containsKey("mcpServiceURL")) {
            // Legacy support - defaults to SSE
            String url = (String) credentials.get("mcpServiceURL");
            return new McpServerService(service.getName(), url, ProtocolType.SSE, clientFactory);
        }
        
        return null;
    }
}
```

### Data Model Updates

```java
public class McpServer {
    private String name;
    private String serverName;
    private boolean healthy;
    private List<Tool> tools;
    private ProtocolType protocol; // NEW FIELD
    
    // Constructor
    public McpServer(String name, String serverName, boolean healthy, List<Tool> tools, ProtocolType protocol) {
        this.name = name;
        this.serverName = serverName;
        this.healthy = healthy;
        this.tools = tools;
        this.protocol = protocol;
    }
    
    // Getters and setters
    public ProtocolType getProtocol() {
        return protocol;
    }
    
    public void setProtocol(ProtocolType protocol) {
        this.protocol = protocol;
    }
}
```

## Key Implementation Notes

### Transport Selection Logic

1. **Priority Order**: `mcpStreamableURL` > `mcpSseURL` > `mcpServiceURL` (legacy)
2. **Backward Compatibility**: `mcpServiceURL` maps to SSE protocol
3. **Health Checks**: Use appropriate transport for health check operations
4. **Error Handling**: Graceful degradation when transport fails

### Protocol-Specific Considerations

#### SSE Transport
- Uses `/sse` endpoint by default
- Supports MCP Protocol Version `2024-11-05`
- Bidirectional communication via SSE + HTTP POST
- Session management through SSE events

#### Streamable HTTP Transport
- Uses `/mcp` endpoint by default
- Supports MCP Protocol Version `2025-03-26`
- HTTP POST/GET with optional SSE streaming
- Enhanced session management
- Supports both stateful and stateless servers
- `resumableStreams` option for connection resilience

### Modern Java Constructs

When implementing this in the codebase, prefer:

- **Switch expressions** instead of switch statements
- **Records** for immutable data classes where appropriate
- **Pattern matching** for type checking and casting
- **Modern exception handling** with try-with-resources
- **Stream API** for collections processing
- **Duration** API for timeout configuration