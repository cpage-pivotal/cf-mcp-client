package org.tanzu.mcpclient.a2a;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Event published when A2A configuration is complete.
 * Contains the list of initialized A2A agent services.
 */
public class A2AConfigurationEvent extends ApplicationEvent {

    private final List<A2AAgentService> agentServices;

    public A2AConfigurationEvent(Object source, List<A2AAgentService> agentServices) {
        super(source);
        this.agentServices = agentServices;
    }

    public List<A2AAgentService> getAgentServices() {
        return agentServices;
    }
}
