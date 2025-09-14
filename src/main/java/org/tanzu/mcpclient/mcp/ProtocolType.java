package org.tanzu.mcpclient.mcp;

import java.util.Map;

public sealed interface ProtocolType
        permits ProtocolType.SSE, ProtocolType.StreamableHttp, ProtocolType.Legacy {

    record SSE() implements ProtocolType {
        public String displayName() { return "SSE"; }
        public String bindingKey() { return "mcpSseURL"; }
    }

    record StreamableHttp() implements ProtocolType {
        public String displayName() { return "Streamable HTTP"; }
        public String bindingKey() { return "mcpStreamableURL"; }
    }

    record Legacy() implements ProtocolType {
        public String displayName() { return "SSE"; }
        public String bindingKey() { return "mcpServiceURL"; }
    }

    // Default methods
    String displayName();
    String bindingKey();

    /**
     * Factory method for creating protocol from service credentials
     */
    static ProtocolType fromCredentials(Map<String, Object> credentials) {
        if (credentials.containsKey("mcpStreamableURL")) {
            return new StreamableHttp();
        } else if (credentials.containsKey("mcpSseURL")) {
            return new SSE();
        } else if (credentials.containsKey("mcpServiceURL")) {
            return new Legacy();
        }
        throw new IllegalArgumentException("No valid MCP binding key found in credentials");
    }
}