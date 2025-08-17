package org.tanzu.mcpclient.vectorstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.tanzu.mcpclient.model.ModelDiscoveryService;
import org.springframework.lang.NonNull;

import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
public class VectorStoreConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreConfiguration.class);

    private final ModelDiscoveryService modelDiscoveryService;

    public VectorStoreConfiguration(ModelDiscoveryService modelDiscoveryService) {
        this.modelDiscoveryService = modelDiscoveryService;
    }

    @Bean
    @Conditional(DatabaseAvailableCondition.class)
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        int dimensions = PgVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE;

        if (modelDiscoveryService.isEmbeddingModelAvailable()) {
            try {
                // Try to get dimensions from the embedding model
                dimensions = embeddingModel.dimensions();
                logger.info("Using embedding model dimensions: {}", dimensions);
            } catch (Exception e) {
                logger.warn("Could not determine embedding dimensions (this may be expected with GenaiLocator): {}. Using default: {}",
                        e.getMessage(), PgVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE);
                dimensions = PgVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE;

                // Log additional context for GenaiLocator case
                if (modelDiscoveryService.isEmbeddingModelAvailableFromLocators()) {
                    logger.info("Embedding model is available from aggregated GenaiLocators");
                }
            }
        } else {
            logger.info("No embedding model configured, using default dimensions: {}", dimensions);
        }

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("vector_store")
                .maxDocumentBatchSize(10000)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore fallbackVectorStore() {
        logger.info("Creating fallback vectorStore bean");
        return new EmptyVectorStore();
    }

    public static class EmptyVectorStore implements VectorStore {
        @Override
        public void add(@NonNull List<Document> documents) {
        }

        @Override
        public void delete(@NonNull List<String> idList) {
        }

        @Override
        public void delete(@NonNull Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(@NonNull SearchRequest request) {
            return List.of();
        }

        @Override
        @NonNull
        public String getName() {
            return "";
        }
    }
}