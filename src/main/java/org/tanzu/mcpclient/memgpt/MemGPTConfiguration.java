package org.tanzu.mcpclient.memgpt;

import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

@Configuration
public class MemGPTConfiguration {

    private static final String MEM_GPT = "memGPTUrl";
    private static final Logger logger = LoggerFactory.getLogger(MemGPTConfiguration.class);

    ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    public ChatMemory chatMemory(WebClient.Builder clientBuilder) {
        CfEnv cfEnv = new CfEnv();

        return cfEnv.findAllServices().stream()
                .map(CfService::getCredentials)
                .map(credentials -> credentials.getString(MEM_GPT))
                .filter(Objects::nonNull)
                .findFirst()
                .map(memGptUrl -> {
                    logger.info("Bound to MemGPT Service: {}", memGptUrl);
                    return (ChatMemory) new MemGPTRestChatMemory(clientBuilder, memGptUrl);
                })
                .orElse(new InMemoryChatMemory());
    }
}
