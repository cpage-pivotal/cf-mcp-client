# Design: Separate API Clients for Chat and Embedding Models

## Problem Statement

The current implementation creates a single `OpenAiApi` bean that is shared between chat and embedding models. This fails when:
- Multiple GenAI services are bound with different API endpoints
- The chat model and embedding model are served from different proxy URLs
- The shared API client is configured with one endpoint but used for both model types

### Current Failure Scenario
```
Chat Model: https://genai-proxy.../local-mistral-nemo-instruct.../openai
Embedding Model: https://genai-proxy.../prod-embedding-nomic-text.../openai
OpenAiApi configured with: Chat Model URL (causes 404 for embedding calls)
```

## Design Goals

1. **Separate API Clients**: Create independent OpenAiApi instances for chat and embedding
2. **Backward Compatibility**: Maintain existing behavior when models share endpoints
3. **Minimal Changes**: Focus changes on the model infrastructure layer
4. **Clear Separation**: Make the distinction between chat and embedding clear in the code

## Proposed Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────┐
│              ModelInfrastructureConfiguration           │
├─────────────────────────────────────────────────────────┤
│ + chatOpenAiApi(): OpenAiApi                           │
│ + embeddingOpenAiApi(): OpenAiApi                      │
│ - createOpenAiApi(GenaiModel): OpenAiApi               │
└─────────────────────────────────────────────────────────┘
                    ▲                ▲
                    │                │
        ┌───────────┴──────┐   ┌────┴──────────────┐
        │   Chat Model     │   │ Embedding Model   │
        │   Configuration  │   │  Configuration    │
        └──────────────────┘   └───────────────────┘
```

### Key Design Decisions

#### 1. Separate Bean Creation
- Create `chatOpenAiApi` bean specifically for chat models
- Create `embeddingOpenAiApi` bean specifically for embedding models
- Each bean is configured with its respective model's endpoint and credentials

#### 2. Shared Logic Extraction
- Extract common OpenAiApi creation logic to a private method
- Reduces code duplication while maintaining separation
- Makes it easy to add model-specific configurations if needed

#### 3. PropertyBasedModelProvider Updates
- Inject both API clients
- Use `chatOpenAiApi` for creating ChatModel
- Use `embeddingOpenAiApi` for creating EmbeddingModel
- Clear separation of concerns

#### 4. Optimization for Shared Endpoints
- When both models share the same endpoint, we still create two beans
- This simplifies the logic and avoids conditional complexity
- Minor memory overhead is acceptable for clarity

## Implementation Plan

### Phase 1: Infrastructure Changes ✅ COMPLETED

#### Step 1.1: Refactor ModelInfrastructureConfiguration ✅ COMPLETED
**File**: `src/main/java/org/tanzu/mcpclient/model/ModelInfrastructureConfiguration.java`

**Implemented Changes**:
- ✅ Removed the single `openAiApi()` bean method
- ✅ Added `chatOpenAiApi()` bean method that uses `getChatModelConfig()`
- ✅ Added `embeddingOpenAiApi()` bean method that uses `getEmbeddingModelConfig()`
- ✅ Added private `createOpenAiApi(GenaiModel config, String modelType)` helper method

**Implementation Details**:
- ✅ GenaiLocator case handled (returns minimal/non-functional API)
- ✅ Missing credentials handled gracefully with warning logs
- ✅ Maintained existing error handling patterns
- ✅ Added modelType parameter for better logging clarity

#### Step 1.2: Update PropertyBasedModelProvider ✅ COMPLETED
**File**: `src/main/java/org/tanzu/mcpclient/model/PropertyBasedModelProvider.java`

**Implemented Changes**:
- ✅ Updated constructor to accept both `OpenAiApi chatOpenAiApi` and `OpenAiApi embeddingOpenAiApi`
- ✅ Updated `createPropertyBasedChatModel()` to use `chatOpenAiApi`
- ✅ Updated `createPropertyBasedEmbeddingModel()` to use `embeddingOpenAiApi`
- ✅ Removed the single `openAiApi` field

**Build Status**: ✅ PASSED - Application compiles successfully

### Phase 2: Testing & Validation

#### Step 2.1: Unit Tests
- Test chat API client creation with various configurations
- Test embedding API client creation with various configurations
- Test handling of missing credentials
- Test GenaiLocator bypass scenario

#### Step 2.2: Integration Tests
- Test with single GenAI service
- Test with multiple GenAI services (different endpoints)
- Test with user-provided services
- Test with mixed service types

#### Step 2.3: Manual Testing Scenarios
1. **Scenario A**: Single GenAI service with both capabilities
2. **Scenario B**: Two GenAI services with different endpoints
3. **Scenario C**: User-provided chat + GenAI embedding
4. **Scenario D**: GenaiLocator-managed models

### Phase 3: Deployment & Rollback Plan

#### Deployment Steps
1. Build and test locally with various VCAP_SERVICES configurations
2. Deploy to staging environment
3. Test all scenarios in staging
4. Deploy to production with monitoring

#### Rollback Plan
- If issues arise, revert to single OpenAiApi bean approach
- The changes are isolated to two files, making rollback straightforward

## Alternative Considerations

### Alternative 1: Dynamic API Client Selection
- Create API clients on-demand based on model configuration
- Pro: More flexible, handles any number of endpoints
- Con: More complex, potential performance overhead

### Alternative 2: API Client Factory Pattern
- Implement a factory that manages API client instances
- Pro: Better encapsulation, easier to extend
- Con: Over-engineering for current needs

### Alternative 3: Single Multiplexing API Client
- Create a wrapper that routes to different endpoints based on operation
- Pro: Single point of configuration
- Con: Complex routing logic, harder to debug

## Risk Analysis

### Risks
1. **Memory Overhead**: Creating multiple API clients increases memory usage
   - Mitigation: Minimal impact, acceptable trade-off for correctness
   
2. **Configuration Complexity**: More beans to manage
   - Mitigation: Clear naming and documentation
   
3. **Breaking Changes**: Existing deployments might be affected
   - Mitigation: Thorough testing, gradual rollout

### Benefits
1. **Correctness**: Each model uses the correct endpoint
2. **Clarity**: Clear separation of concerns
3. **Flexibility**: Easy to add model-specific configurations
4. **Maintainability**: Simpler to debug endpoint issues

## Success Criteria

1. Application starts successfully with multiple GenAI services
2. Chat operations use the correct chat model endpoint
3. Embedding operations use the correct embedding model endpoint
4. No regression in single-service scenarios
5. Clear error messages when configuration is invalid

## Timeline Estimate

- **Design Review**: 1 hour
- **Implementation**: 2-3 hours
- **Testing**: 2-3 hours
- **Documentation**: 1 hour
- **Total**: ~1 day of development effort

## Conclusion

This design provides a clean solution to the multiple API endpoint problem by creating separate API clients for each model type. The approach is straightforward, maintains backward compatibility, and makes the system more robust when dealing with diverse service configurations. The implementation focuses on the minimum necessary changes while providing a solid foundation for future enhancements.