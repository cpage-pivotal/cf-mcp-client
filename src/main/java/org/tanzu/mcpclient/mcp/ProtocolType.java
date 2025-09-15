package org.tanzu.mcpclient.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProtocolType.SSE.class, name = "SSE"),
    @JsonSubTypes.Type(value = ProtocolType.StreamableHttp.class, name = "StreamableHttp"),
    @JsonSubTypes.Type(value = ProtocolType.Legacy.class, name = "Legacy")
})
public sealed interface ProtocolType
        permits ProtocolType.SSE, ProtocolType.StreamableHttp, ProtocolType.Legacy {

    record SSE() implements ProtocolType {
        @JsonProperty("displayName")
        public String displayName() { return "SSE"; }
        
        @JsonProperty("bindingKey")
        public String bindingKey() { return "mcpSseURL"; }
    }

    record StreamableHttp() implements ProtocolType {
        @JsonProperty("displayName")
        public String displayName() { return "Streamable HTTP"; }
        
        @JsonProperty("bindingKey")
        public String bindingKey() { return "mcpStreamableURL"; }
    }

    record Legacy() implements ProtocolType {
        @JsonProperty("displayName")
        public String displayName() { return "SSE"; }
        
        @JsonProperty("bindingKey")
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