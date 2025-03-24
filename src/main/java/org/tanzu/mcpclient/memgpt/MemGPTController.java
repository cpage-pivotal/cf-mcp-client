package org.tanzu.mcpclient.memgpt;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.tanzu.mcpclient.chat.ChatService;

@RestController
public class MemGPTController {

    private final MemGPTConfiguration memGPTConfiguration;
    private final RestTemplate restTemplate = new RestTemplate();

    public MemGPTController(MemGPTConfiguration memGPTConfiguration) {
        this.memGPTConfiguration = memGPTConfiguration;
    }

    @GetMapping("/memory/metrics")
    public ResponseEntity<Object> getMetrics() {
        if ( memGPTConfiguration.memGPTUrl() == null ) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).
                    body("No available memGPT service");
        }

        String targetURL = memGPTConfiguration.memGPTUrl() + "/metrics/" + ChatService.CONVERSATION_NAME;
        return restTemplate.getForEntity(targetURL, Object.class);
    }
}
