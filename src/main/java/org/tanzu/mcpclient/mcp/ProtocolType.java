package org.tanzu.mcpclient.mcp;

/**
 * Enumeration of supported MCP transport protocols.
 * Used to distinguish between different transport mechanisms for MCP servers.
 */
public enum ProtocolType {
    SSE("SSE"),
    STREAMABLE_HTTP("Streamable HTTP");
    
    private final String displayName;
    
    ProtocolType(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Returns the human-readable display name for this protocol type.
     */
    public String getDisplayName() {
        return displayName;
    }
}