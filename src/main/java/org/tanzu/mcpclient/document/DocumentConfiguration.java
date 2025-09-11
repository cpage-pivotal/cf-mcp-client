package org.tanzu.mcpclient.document;

import jakarta.servlet.MultipartConfigElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.util.unit.DataSize;
import org.tanzu.mcpclient.model.ModelDiscoveryService;

@Configuration
public class DocumentConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DocumentConfiguration.class);

    private final VectorStore vectorStore;
    private final ModelDiscoveryService modelDiscoveryService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentConfiguration(@Lazy VectorStore vectorStore, @Lazy ModelDiscoveryService modelDiscoveryService,
                                 ApplicationEventPublisher eventPublisher) {
        this.vectorStore = vectorStore;
        this.modelDiscoveryService = modelDiscoveryService;
        this.eventPublisher = eventPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishConfigurationEvent() {
        String embeddingModel = modelDiscoveryService.getEmbeddingModelName();
        String vectorDatabase = vectorStore.getName();
        logger.debug("Publishing DocumentConfigurationEvent: embeddingModel={}, vectorDatabase={}",
                embeddingModel, vectorDatabase);
        eventPublisher.publishEvent(new DocumentConfigurationEvent(this, embeddingModel, vectorDatabase));
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        
        // Set maximum file size
        factory.setMaxFileSize(DataSize.ofMegabytes(50));
        
        // Set maximum request size (total file size)
        factory.setMaxRequestSize(DataSize.ofMegabytes(50));
        
        // Set location for temporary files
        factory.setLocation("");
        
        return factory.createMultipartConfig();
    }
}
