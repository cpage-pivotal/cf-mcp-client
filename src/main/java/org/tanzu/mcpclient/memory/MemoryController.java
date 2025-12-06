package org.tanzu.mcpclient.memory;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing memory preferences per conversation.
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private static final Logger logger = LoggerFactory.getLogger(MemoryController.class);
    
    private final MemoryPreferenceService memoryPreferenceService;

    public MemoryController(MemoryPreferenceService memoryPreferenceService) {
        this.memoryPreferenceService = memoryPreferenceService;
    }

    /**
     * Get the current memory preference for a conversation.
     *
     * @param conversationId the conversation ID (optional, defaults to session ID)
     * @param request the HTTP request
     * @return the current memory type preference
     */
    @GetMapping("/preference")
    public ResponseEntity<MemoryPreferenceResponse> getPreference(
            @RequestParam(required = false) String conversationId,
            HttpServletRequest request) {
        
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = request.getSession().getId();
        }

        MemoryPreferenceService.MemoryType memoryType = memoryPreferenceService.getPreference(conversationId);
        
        return ResponseEntity.ok(new MemoryPreferenceResponse(conversationId, memoryType.name()));
    }

    /**
     * Set the memory preference for a conversation.
     *
     * @param preferenceRequest the preference request
     * @param request the HTTP request
     * @return the updated preference
     */
    @PostMapping("/preference")
    public ResponseEntity<MemoryPreferenceResponse> setPreference(
            @RequestBody MemoryPreferenceRequest preferenceRequest,
            HttpServletRequest request) {
        
        String conversationId = preferenceRequest.conversationId();
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = request.getSession().getId();
        }

        try {
            MemoryPreferenceService.MemoryType memoryType = 
                MemoryPreferenceService.MemoryType.valueOf(preferenceRequest.memoryType().toUpperCase());
            
            memoryPreferenceService.setPreference(conversationId, memoryType);
            
            logger.info("Updated memory preference for conversation {} to {}", conversationId, memoryType);
            
            return ResponseEntity.ok(new MemoryPreferenceResponse(conversationId, memoryType.name()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid memory type: {}", preferenceRequest.memoryType(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Request object for setting memory preference.
     */
    public record MemoryPreferenceRequest(String conversationId, String memoryType) {}

    /**
     * Response object for memory preference operations.
     */
    public record MemoryPreferenceResponse(String conversationId, String memoryType) {}
}

