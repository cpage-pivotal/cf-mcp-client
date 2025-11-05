package org.tanzu.mcpclient.a2a;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Java records for A2A protocol message structures.
 * Supports JSON-RPC 2.0 over HTTP for agent communication.
 */
public class A2AModels {

    /**
     * JSON-RPC 2.0 request wrapper
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JsonRpcRequest(
        String jsonrpc,
        Object id,
        String method,
        Map<String, Object> params
    ) {
        public JsonRpcRequest(Object id, String method, Map<String, Object> params) {
            this("2.0", id, method, params);
        }
    }

    /**
     * JSON-RPC 2.0 response wrapper
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JsonRpcResponse(
        String jsonrpc,
        Object id,
        Object result,
        JsonRpcError error
    ) {}

    /**
     * JSON-RPC 2.0 error structure
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JsonRpcError(
        int code,
        String message,
        Object data
    ) {}

    /**
     * A2A message structure
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
        String role,
        List<Part> parts,
        String messageId,
        String taskId,
        String contextId
    ) {
        public Message(String role, List<Part> parts, String messageId) {
            this(role, parts, messageId, null, null);
        }
    }

    /**
     * Message send configuration
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessageSendConfiguration(
        List<String> acceptedOutputModes,
        boolean blocking
    ) {}

    /**
     * Sealed interface for message parts to ensure type safety.
     * Uses Jackson @JsonTypeInfo for polymorphic deserialization.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = TextPart.class, name = "text"),
        @JsonSubTypes.Type(value = FilePart.class, name = "file"),
        @JsonSubTypes.Type(value = DataPart.class, name = "data")
    })
    public sealed interface Part permits TextPart, FilePart, DataPart {
        String kind();
    }

    /**
     * Text content part
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextPart(
        String kind,
        String text
    ) implements Part {
        public TextPart(String text) {
            this("text", text);
        }
    }

    /**
     * File content part (can contain bytes or URI)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FilePart(
        String kind,
        String name,
        String mimeType,
        byte[] bytes,
        String uri
    ) implements Part {
        public FilePart(String name, String mimeType, byte[] bytes) {
            this("file", name, mimeType, bytes, null);
        }

        public FilePart(String name, String mimeType, String uri) {
            this("file", name, mimeType, null, uri);
        }
    }

    /**
     * Structured data part
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataPart(
        String kind,
        Object data
    ) implements Part {
        public DataPart(Object data) {
            this("data", data);
        }
    }

    /**
     * Task representing a stateful unit of work
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Task(
        String kind,
        String id,
        String contextId,
        TaskStatus status,
        List<Message> history,
        List<Artifact> artifacts
    ) {}

    /**
     * Task status information
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskStatus(
        String state,
        Message message,
        Instant timestamp
    ) {}

    /**
     * Task artifact
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artifact(
        String id,
        String name,
        String mimeType,
        String uri,
        byte[] bytes
    ) {}
}
