package org.tanzu.mcpclient.memgpt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.tanzu.mcpclient.chat.ChatService;

@RestController
public class MemGPTController {

    private final MemGPTConfiguration memGPTConfiguration;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final Logger logger = LoggerFactory.getLogger(MemGPTController.class);
    private static final String METRICS_PATH = "/metrics/";

    public MemGPTController(MemGPTConfiguration memGPTConfiguration) {
        this.memGPTConfiguration = memGPTConfiguration;
    }

    @GetMapping("/memory/metrics")
    public ResponseEntity<Object> getMetrics() {
        if ( memGPTConfiguration.memGPTUrl() == null ) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).
                    body("No available memGPT service");
        }

        String targetURL = UriComponentsBuilder
                .fromUriString(memGPTConfiguration.memGPTUrl())
                .path(METRICS_PATH)
                .path(ChatService.CONVERSATION_NAME)
                .build()
                .toString();
        try {
            ResponseEntity<Object> responseEntity = restTemplate.getForEntity(targetURL, Object.class);
            return ResponseEntity
                    .status(responseEntity.getStatusCode())
                    .body(responseEntity.getBody());
        }
        catch (RestClientException ex) {
            logger.error("Error retrieving metrics: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
    }
}
