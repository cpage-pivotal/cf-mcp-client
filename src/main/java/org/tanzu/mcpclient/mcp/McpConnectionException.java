package org.tanzu.mcpclient.mcp;

/**
 * Exception thrown when MCP client connection attempts fail.
 */
public class McpConnectionException extends RuntimeException {

    public McpConnectionException(String message) {
        super(message);
    }

    public McpConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
