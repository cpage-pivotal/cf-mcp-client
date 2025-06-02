package org.tanzu.mcpclient.zantu;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
public class ZantuChatController {

    private final ZantuService zantuService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public ZantuChatController(ZantuService zantuService) {
        this.zantuService = zantuService;
    }

    @GetMapping(value = "/zantu/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter zantuChatStream(@RequestParam("chat") String chat,
                                      HttpServletRequest request) {

        String conversationId = request.getSession().getId();
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout

        // Store the emitter for this conversation
        activeEmitters.put(conversationId, emitter);

        // Clean up when connection closes
        emitter.onCompletion(() -> activeEmitters.remove(conversationId));
        emitter.onTimeout(() -> activeEmitters.remove(conversationId));
        emitter.onError((ex) -> activeEmitters.remove(conversationId));

        // Send the message to Zantu
        zantuService.sendMessageToZantu(conversationId, chat);

        // Send an immediate acknowledgment
        try {
            Map<String, String> ackPayload = Map.of("content", "Message sent to Zantu...");
            String jsonData = objectMapper.writeValueAsString(ackPayload);
            emitter.send(SseEmitter.event()
                    .data(jsonData)
                    .name("acknowledgment"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @EventListener
    public void handleZantuResponse(ZantuResponseEvent event) {
        ZantuChatMessage message = event.getMessage();
        SseEmitter emitter = activeEmitters.get(message.conversationId());

        if (emitter != null) {
            try {
                // Send the Zantu response
                Map<String, String> payload = Map.of("content", message.content());
                String jsonData = objectMapper.writeValueAsString(payload);

                emitter.send(SseEmitter.event()
                        .data(jsonData)
                        .name("zantu_message"));

                // Close the connection
                emitter.send(SseEmitter.event()
                        .name("close")
                        .data(""));
                emitter.complete();

                activeEmitters.remove(message.conversationId());

            } catch (IOException e) {
                emitter.completeWithError(e);
                activeEmitters.remove(message.conversationId());
            }
        }
    }
}