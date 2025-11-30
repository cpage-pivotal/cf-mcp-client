package org.tanzu.mcpclient.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring Configuration for initializing A2A agents on startup.
 * Discovers A2A service bindings and creates agent service instances using the A2A Java SDK.
 */
@Configuration
public class A2AConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(A2AConfiguration.class);

    private final A2ADiscoveryService discoveryService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private final List<A2AAgentService> agentServices = new ArrayList<>();
    private final Map<String, String> agentNamesByUri = new ConcurrentHashMap<>();

    public A2AConfiguration(A2ADiscoveryService discoveryService,
                           ObjectMapper objectMapper,
                           ApplicationEventPublisher eventPublisher) {
        this.discoveryService = discoveryService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;

        logger.info("A2AConfiguration initialized (using A2A Java SDK 0.3.2.Final)");
    }

    /**
     * Handles ApplicationReadyEvent to initialize A2A agents after Spring context is fully loaded.
     * Discovers service bindings, creates agent services, and publishes configuration event.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application ready, initializing A2A agents...");

        // Get A2A services from Cloud Foundry
        List<A2ADiscoveryService.A2AServiceInfo> services = discoveryService.getA2AServices();

        if (services.isEmpty()) {
            logger.info("No A2A agents configured");
            // Publish empty configuration event
            eventPublisher.publishEvent(new A2AConfigurationEvent(this, List.of()));
            return;
        }

        logger.info("Found {} A2A service(s) to initialize", services.size());

        // Create A2AAgentService for each discovered service
        for (A2ADiscoveryService.A2AServiceInfo serviceInfo : services) {
            logger.debug("Initializing A2A agent: {} at {}", serviceInfo.serviceName(), serviceInfo.agentCardUri());

            try {
                A2AAgentService agentService = new A2AAgentService(
                        serviceInfo.serviceName(),
                        serviceInfo.agentCardUri(),
                        objectMapper
                );

                agentServices.add(agentService);

                // Store agent name mapping if agent card loaded successfully
                if (agentService.isHealthy() && agentService.getAgentCard() != null) {
                    agentNamesByUri.put(serviceInfo.agentCardUri(), agentService.getAgentCard().name());
                }

            } catch (Exception e) {
                logger.error("Failed to initialize A2A agent {}: {}", serviceInfo.serviceName(), e.getMessage(), e);
            }
        }

        // Log summary
        long healthyCount = agentServices.stream().filter(A2AAgentService::isHealthy).count();
        logger.info("A2A agent initialization complete: {}/{} agents healthy",
                healthyCount, agentServices.size());

        // Publish configuration event with agent services
        eventPublisher.publishEvent(new A2AConfigurationEvent(this, List.copyOf(agentServices)));
    }

    /**
     * Gets the list of initialized A2A agent services.
     *
     * @return Immutable copy of agent services list
     */
    public List<A2AAgentService> getAgentServices() {
        return List.copyOf(agentServices);
    }

    /**
     * Gets the mapping of agent card URIs to agent names.
     *
     * @return Immutable copy of agent names by URI map
     */
    public Map<String, String> getAgentNamesByUri() {
        return Map.copyOf(agentNamesByUri);
    }
}
