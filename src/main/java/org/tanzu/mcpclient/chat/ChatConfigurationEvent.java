package org.tanzu.mcpclient.chat;

import org.springframework.context.ApplicationEvent;
import org.tanzu.mcpclient.metrics.McpServer;

import java.util.List;

public class ChatConfigurationEvent extends ApplicationEvent {
    private final String chatModel;
    private final List<McpServer> mcpServersWithHealth;

    public ChatConfigurationEvent(Object source, String chatModel, List<McpServer> mcpServersWithHealth) {
        super(source);
        this.chatModel = chatModel;
        this.mcpServersWithHealth = mcpServersWithHealth;
    }

    public String getChatModel() {
        return chatModel;
    }

    public List<McpServer> getMcpServersWithHealth() {
        return mcpServersWithHealth;
    }
}