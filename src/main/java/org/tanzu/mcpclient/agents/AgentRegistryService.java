package org.tanzu.mcpclient.agents;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Simplified agent registry with hardcoded agent definitions.
 * Much simpler than complex configuration - just define agents here.
 */
@Service
public class AgentRegistryService {

    // Define your agents here - add more as needed
    private static final List<AgentInfo> AVAILABLE_AGENTS = List.of(
            new AgentInfo(
                    "reviewer",
                    "Literary Critic",
                    "Authors book reviews and literary analysis",
                    AgentStatus.AVAILABLE,
                    "rate_review",
                    "#28A745"
            ),
            new AgentInfo(
                    "editor",
                    "Cloud Foundry Operator",
                    "Site Reliability Engineering for Tanzu Platform",
                    AgentStatus.AVAILABLE,
                    "cloud",
                    "#8dd1e4"
            )
    );

    /**
     * Gets all available agent types.
     */
    public List<AgentInfo> getAvailableAgents() {
        return AVAILABLE_AGENTS;
    }

    /**
     * Gets a specific agent by type.
     */
    public Optional<AgentInfo> getAgent(String agentType) {
        return AVAILABLE_AGENTS.stream()
                .filter(agent -> agent.id().equals(agentType))
                .findFirst();
    }

    /**
     * Checks if an agent type is available.
     */
    public boolean isAgentAvailable(String agentType) {
        return getAgent(agentType).isPresent();
    }
}