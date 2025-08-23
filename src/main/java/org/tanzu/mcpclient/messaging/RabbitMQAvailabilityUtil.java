package org.tanzu.mcpclient.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for checking RabbitMQ server availability.
 * Uses modern Java patterns with records for connection info.
 */
public class RabbitMQAvailabilityUtil {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQAvailabilityUtil.class);

    private static volatile Boolean rabbitMQAvailable = null;

    /**
     * Connection information record for RabbitMQ.
     */
    public record RabbitMQConnectionInfo(
            String host,
            int port,
            String username,
            String password,
            String virtualHost
    ) {}

    /**
     * Checks if RabbitMQ server is available.
     * The result is cached after the first call.
     */
    public static boolean isRabbitMQAvailable(Environment env) {
        if (rabbitMQAvailable == null) {
            synchronized (RabbitMQAvailabilityUtil.class) {
                if (rabbitMQAvailable == null) {
                    rabbitMQAvailable = testRabbitMQConnection(env);
                    logger.info("RabbitMQ availability check result: {}", rabbitMQAvailable);
                }
            }
        }
        return rabbitMQAvailable;
    }

    /**
     * Tests if RabbitMQ server connection can be established.
     */
    private static boolean testRabbitMQConnection(Environment env) {
        var connectionInfo = extractConnectionInfo(env);

        if (connectionInfo.host() == null) {
            logger.warn("No RabbitMQ host found in environment properties");
            return false;
        }

        // First, try a simple socket connection to check if the port is open
        if (!isPortReachable(connectionInfo.host(), connectionInfo.port())) {
            logger.warn("RabbitMQ port {} is not reachable on host {}",
                    connectionInfo.port(), connectionInfo.host());
            return false;
        }

        // Then try to establish an actual RabbitMQ connection
        return testRabbitMQAuthentication(connectionInfo);
    }

    /**
     * Extracts RabbitMQ connection information from Spring environment.
     */
    private static RabbitMQConnectionInfo extractConnectionInfo(Environment env) {
        return new RabbitMQConnectionInfo(
                env.getProperty("spring.rabbitmq.host", "localhost"),
                env.getProperty("spring.rabbitmq.port", Integer.class, 5672),
                env.getProperty("spring.rabbitmq.username", "guest"),
                env.getProperty("spring.rabbitmq.password", "guest"),
                env.getProperty("spring.rabbitmq.virtual-host", "/")
        );
    }

    /**
     * Checks if the specified port is reachable on the host.
     */
    private static boolean isPortReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 3000); // 3 second timeout
            logger.debug("Successfully connected to {}:{}", host, port);
            return true;
        } catch (IOException e) {
            logger.debug("Cannot reach {}:{} - {}", host, port, e.getMessage());
            return false;
        }
    }

    /**
     * Tests RabbitMQ authentication and connection.
     */
    private static boolean testRabbitMQAuthentication(RabbitMQConnectionInfo info) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(info.host());
        factory.setPort(info.port());
        factory.setUsername(info.username());
        factory.setPassword(info.password());
        factory.setVirtualHost(info.virtualHost());
        factory.setConnectionTimeout(5000); // 5 second timeout
        factory.setHandshakeTimeout(5000);   // 5 second timeout

        try (Connection connection = factory.newConnection()) {
            boolean isOpen = connection.isOpen();
            logger.info("RabbitMQ connection test: {}", isOpen ? "PASSED" : "FAILED");
            return isOpen;
        } catch (IOException | TimeoutException e) {
            logger.error("RabbitMQ connection failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Resets the cached availability status (useful for testing).
     */
    public static void resetCachedStatus() {
        synchronized (RabbitMQAvailabilityUtil.class) {
            rabbitMQAvailable = null;
        }
        logger.debug("RabbitMQ availability cache reset");
    }
}