package org.tanzu.mcpclient.memgpt;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * A MemMPT custom memory advisor that delegates to a MemGPT MCP service.  This allows for a virtually
 * infinite context window.  It encapsulates a custom ChatMemory implementation that handles the
 * communication with the underlying MemGPT service.
 * <br></>
 * Context memory is identified by the conversation ID; i.e. a new conversation ID will result in a
 * unique content memory.
 * <br></>
 * This advisor requires a previously created McpSyncClient that is configured to connect and
 * communicate with the MemGPT MCP service.
 *
 */
public class MemGPTMessageChatMemoryAdvisor extends MessageChatMemoryAdvisor  {

    public MemGPTMessageChatMemoryAdvisor(MemGPTChatMemory chatMemory, String conversationId) {
        // Because MemGPT context windows are virtually and theoretically limitless,
        // a max number of messages to store is somewhat moot.
        // TODO: possibly control the number of previous messages that are returned
        // from the MemGPT service.  As of now, the MemGPT service can hold as many messages
        // as it's threshold limit allows, but this might be a different size than the model
        // that the ChatClient is targeting.
        super(chatMemory, conversationId, Integer.MAX_VALUE);
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {

        // we need the user message so the recallContext tool can examine it and
        // potentially perform operations such as modifying core memory or recalling
        // older conversations
        UserMessage userMessage = new UserMessage(advisedRequest.userText(), advisedRequest.media());

        ((MemGPTChatMemory)chatMemoryStore).setUserMessage(userMessage);

        return super.aroundCall(advisedRequest, chain);
    }
}


