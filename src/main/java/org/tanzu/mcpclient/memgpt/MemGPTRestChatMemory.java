package org.tanzu.mcpclient.memgpt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Component
public class MemGPTRestChatMemory implements MemGPTChatMemory {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemGPTChatMemory.class);

    private final WebClient webClient;

    private UserMessage userMessage;

    public MemGPTRestChatMemory(WebClient.Builder clientBuilder, @Value("${memgpt.rest.baseurl:http://localhost:8080}") String baseUrl) {
        this.webClient = clientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {

        try {
            webClient.post()
                    .uri("agent")
                    .bodyValue(new RestChatEngine.AgentCreateRequest(conversationId, 4026))
                    .retrieve().bodyToMono(String.class).block();
        }
        catch (WebClientResponseException e) {
            if (e.getStatusCode() != HttpStatusCode.valueOf(409)) {
                LOGGER.warn("Error recalling context; could not create agent:, {}", e.getStatusCode());
                return List.of();
            }
        }

        try {
            String messageContent = webClient.post().uri("recallContext/" + conversationId)
                    .bodyValue(userMessage == null ? "" : userMessage.getText())
                    .retrieve().bodyToMono(String.class).block();

            return  MessageUtils.deserializeMessagesFromJSONString(messageContent);
        }
        catch (Exception e) {
            LOGGER.warn("Error recalling context: {}", e.getMessage());
            return List.of();
        }

    }

    @Override
    public void add(String conversationId, List<Message> messages) {

        try {
            webClient.post().uri("appendContext/" + conversationId)
                    .bodyValue(messages)
                    .retrieve().toBodilessEntity().block();
        }
        catch (Exception e) {
            LOGGER.warn("Error appending context: {}", e.getMessage());
        }
    }

    @Override
    public void clear(String conversationId) {
        try {
            webClient.delete().uri("clearContext/" + conversationId)
                    .retrieve().toBodilessEntity().block();
        }
        catch (Exception e) {
            LOGGER.warn("Error clearing context: {}", e.getMessage());
        }
    }

    @Override
    public void setUserMessage(UserMessage userMessage) {
        this.userMessage = userMessage;
    }
}
