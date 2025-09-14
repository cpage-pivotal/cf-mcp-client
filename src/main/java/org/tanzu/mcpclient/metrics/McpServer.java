package org.tanzu.mcpclient.metrics;

import org.tanzu.mcpclient.mcp.ProtocolType;

import java.util.List;

public record McpServer(
        String name,
        String serverName,
        boolean healthy,
        List<Tool> tools,
        ProtocolType protocol
) {

    public record Tool(String name, String description) {
    }

    /**
     * Returns the display name for the MCP server (serverName if available, otherwise name).
     */
    public String getDisplayName() {
        return serverName != null && !serverName.trim().isEmpty() ? serverName : name;
    }
}