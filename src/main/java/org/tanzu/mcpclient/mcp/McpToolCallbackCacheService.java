package org.tanzu.mcpclient.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolsChangedEvent;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service that manages cached MCP tool callback providers with event-driven invalidation.
 * Implements Spring AI 1.1.0-RC1's event-driven tool callback caching pattern for MCP integration.
 *
 * This service:
 * - Caches ToolCallbackProvider instances to avoid repeated tool discovery
 * - Listens for McpToolsChangedEvent to invalidate cache when tools change
 * - Uses thread-safe double-checked locking for cache management
 * - Improves performance by reducing redundant MCP server operations
 */
@Service
public class McpToolCallbackCacheService implements ApplicationListener<McpToolsChangedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(McpToolCallbackCacheService.class);

    private final List<McpServerService> mcpServerServices;
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final AtomicBoolean cacheInvalidated = new AtomicBoolean(true);

    private volatile ToolCallbackProvider[] cachedToolCallbacks = null;

    public McpToolCallbackCacheService(List<McpServerService> mcpServerServices) {
        this.mcpServerServices = mcpServerServices;
        logger.info("McpToolCallbackCacheService initialized with {} MCP server services",
                mcpServerServices.size());
    }

    /**
     * Gets cached tool callback providers, refreshing if cache is invalidated.
     * Uses double-checked locking pattern for thread-safe lazy initialization.
     *
     * @return Array of ToolCallbackProvider instances
     */
    public ToolCallbackProvider[] getToolCallbackProviders() {
        // Fast path: return cached value if valid
        if (!cacheInvalidated.get() && cachedToolCallbacks != null) {
            logger.debug("Returning cached tool callbacks ({} providers)", cachedToolCallbacks.length);
            return cachedToolCallbacks;
        }

        // Slow path: acquire write lock and refresh cache
        cacheLock.writeLock().lock();
        try {
            // Double-check after acquiring lock
            if (!cacheInvalidated.get() && cachedToolCallbacks != null) {
                logger.debug("Cache was refreshed by another thread, returning cached callbacks");
                return cachedToolCallbacks;
            }

            logger.info("Refreshing MCP tool callback cache for {} server(s)", mcpServerServices.size());

            // Create MCP clients and wrap them in tool callback providers
            List<ToolCallbackProvider> providers = mcpServerServices.stream()
                    .map(this::createToolCallbackProvider)
                    .toList();

            cachedToolCallbacks = providers.toArray(new ToolCallbackProvider[0]);
            cacheInvalidated.set(false);

            logger.info("MCP tool callback cache refreshed with {} provider(s)", cachedToolCallbacks.length);

            return cachedToolCallbacks;

        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Creates a tool callback provider for the given MCP server service.
     * Initializes the MCP client and wraps it in a SyncMcpToolCallbackProvider.
     *
     * @param serverService The MCP server service
     * @return ToolCallbackProvider instance
     */
    private ToolCallbackProvider createToolCallbackProvider(McpServerService serverService) {
        try {
            logger.debug("Creating tool callback provider for {} ({})",
                    serverService.getName(), serverService.getProtocol().displayName());

            McpSyncClient client = serverService.createMcpSyncClient();
            client.initialize();

            // Wrap in SyncMcpToolCallbackProvider which provides built-in caching in Spring AI 1.1.0-RC1
            return new SyncMcpToolCallbackProvider(client);

        } catch (Exception e) {
            logger.error("Failed to create tool callback provider for {}: {}",
                    serverService.getName(), e.getMessage(), e);
            throw new RuntimeException("Failed to create MCP client for " + serverService.getName(), e);
        }
    }

    /**
     * Event listener that invalidates the cache when MCP tools change.
     * Implements ApplicationListener<McpToolsChangedEvent> from Spring AI 1.1.0-RC1.
     *
     * @param event The McpToolsChangedEvent fired when tool configurations change
     */
    @Override
    public void onApplicationEvent(McpToolsChangedEvent event) {
        logger.info("Received McpToolsChangedEvent, invalidating tool callback cache");
        invalidateCache();
    }

    /**
     * Manually invalidates the cache, forcing a refresh on next access.
     * Thread-safe operation using atomic boolean.
     */
    public void invalidateCache() {
        cacheLock.writeLock().lock();
        try {
            cacheInvalidated.set(true);
            logger.debug("Tool callback cache invalidated");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Checks if the cache is currently valid.
     *
     * @return true if cache is valid, false if invalidated
     */
    public boolean isCacheValid() {
        return !cacheInvalidated.get() && cachedToolCallbacks != null;
    }
}
