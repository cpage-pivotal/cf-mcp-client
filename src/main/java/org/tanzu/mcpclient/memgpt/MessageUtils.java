package org.tanzu.mcpclient.memgpt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.model.Media;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class MessageUtils {

    public static List<Message> deserializeMessagesFromJSONString(String content) throws JsonProcessingException {

        // The returned content is a list of JSON objects.  Each object represents
        // an individual message which is in the form a Map<String, Object>
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<Object> retObjects = objectMapper.readValue(content, new TypeReference<List<Object>>(){});

        return retObjects.stream().map(ob -> {

            Map<String, Object> map = (Map<String, Object>)ob;

            try {
                // find the message type
                MessageType type = MessageType.valueOf(map.get("messageType").toString());

                // SpringAI Message type cannot be directly deserialized (as of writing) with the
                // ObjectMapper.  The xxxWrapper classes facilitate the deserialization of each
                // message type.
                Class<? extends Message> messageClassType = switch(type) {
                    case SYSTEM -> SystemWrapper.class;
                    case TOOL -> ToolResponseWrapper.class;
                    case ASSISTANT -> AssistantWrapper.class;
                    case USER -> UserWrapper.class;
                };

                // deserialize the message
                return (Message)objectMapper.readValue(objectMapper.writeValueAsString(ob), messageClassType);

            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }).toList();

    }

    /////////////////////////////////////////
    //
    //
    // Wrapper classes for message deserialization
    //

    public static class ToolResponseWrapper extends ToolResponseMessage {

        @JsonCreator
        public ToolResponseWrapper(@JsonProperty("responses") List<ToolResponse> responses, @JsonProperty("metadata") Map<String, Object> metadata) {

            super(responses, metadata);
        }
    }

    public static class AssistantWrapper extends AssistantMessage {

        @JsonCreator
        public AssistantWrapper(@JsonProperty("text") String content, @JsonProperty("metadata") Map<String, Object> properties, @JsonProperty("toolCalls") List<ToolCall> toolCalls, @JsonProperty("media") List<Media> media) {

            super(content, properties, toolCalls, media);
        }
    }

    public static class UserWrapper extends UserMessage {

        @JsonCreator
        public UserWrapper(@JsonProperty("messageType") MessageType messageType, @JsonProperty("text") String textContent, @JsonProperty("media") Collection<Media> media, @JsonProperty("metadata") Map<String, Object> metadata) {
            super(messageType, textContent, media, metadata);
        }
    }

    public static class SystemWrapper extends SystemMessage {

        @JsonCreator
        public SystemWrapper(@JsonProperty("text") String textContent) {
            super(textContent);
        }
    }

}
