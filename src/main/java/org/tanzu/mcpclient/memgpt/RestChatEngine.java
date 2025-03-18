package org.tanzu.mcpclient.memgpt;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Component
public class RestChatEngine implements ChatEngine {

    private final WebClient.Builder clientBuilder;

    private String userId;

    private WebClient webClient;

    public RestChatEngine(WebClient.Builder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }


    @Override
    public void initialize(String userId) {
        this.userId = userId;

        try {
            clientBuilder.build().post()
                    .uri("http://localhost:8080/agent")
                    .bodyValue(new AgentCreateRequest(userId, 4026))
                    .retrieve().bodyToMono(String.class).block();
            System.out.println("Welcome.  Let's start a conversation.  What is your name?");

        }
        catch (WebClientResponseException e) {
            if (e.getStatusCode() != HttpStatusCode.valueOf(409)) {
                throw new RuntimeException(e);
            }
            System.out.println("Welcome back.  Let's continue our conversation.");
        }

        webClient = clientBuilder.baseUrl("http://localhost:8080/chat/" + userId).build();

    }

    @Override
    public String chat(String message) {

        OpenAiApi.ChatCompletionMessage msg =
                new OpenAiApi.ChatCompletionMessage(message, OpenAiApi.ChatCompletionMessage.Role.USER);

        OpenAiApi.ChatCompletionRequest req = new OpenAiApi.ChatCompletionRequest(List.of(msg), "", .5);

        String completion = webClient.post().bodyValue(req).retrieve().bodyToMono(String.class).block();

        return completion;
    }

    public record AgentCreateRequest(String agentName, int contextWindowSize) {}

}
