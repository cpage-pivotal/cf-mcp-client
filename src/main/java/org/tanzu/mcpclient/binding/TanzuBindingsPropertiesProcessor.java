package org.tanzu.mcpclient.binding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.bindings.Binding;
import org.springframework.cloud.bindings.Bindings;
import org.springframework.cloud.bindings.boot.BindingsPropertiesProcessor;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.tanzu.mcpclient.chat.ChatConfiguration;

import java.util.Arrays;
import java.util.Map;

/**
 * An implementation of {@link BindingsPropertiesProcessor} that detects {@link Binding}s
 * of type: {@value GENAI_TYPE}.
 *
 * @author Stuart Charlton
 */
public class TanzuBindingsPropertiesProcessor implements BindingsPropertiesProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TanzuBindingsPropertiesProcessor.class);

    /**
     * The {@link Binding} type that this processor is interested in: {@value}.
     **/
    public static final String GENAI_TYPE = "genai";
    public static final String USER_PROVIDED_TYPE = "user-provided";

    public static final String MODEL_CAPABILITIES_CREDENTIAL = "model-capabilities";
    public static final String MCP_SERVICE_CREDENTIAL = "mcpServiceURL";

    @Override
    public void process(@NonNull Environment environment, Bindings bindings, @NonNull Map<String, Object> properties) {
        bindings.filterBindings(GENAI_TYPE).forEach(binding -> {
            if (binding.getSecret().get(MODEL_CAPABILITIES_CREDENTIAL) != null) {
                String[] capabilities = binding.getSecret().get("model-capabilities").trim().split("\\s*,\\s*");
                if (Arrays.asList(capabilities).contains("chat")) {
                    properties.put("spring.ai.openai.chat.api-key", binding.getSecret().get("api-key"));
                    properties.put("spring.ai.openai.chat.base-url", binding.getSecret().get("uri"));
                    properties.put("spring.ai.openai.chat.options.model", binding.getSecret().get("model-name"));
                }
                if (Arrays.asList(capabilities).contains("embedding")) {
                    properties.put("spring.ai.openai.embedding.api-key", binding.getSecret().get("api-key"));
                    properties.put("spring.ai.openai.embedding.base-url", binding.getSecret().get("uri"));
                    properties.put("spring.ai.openai.embedding.options.model", binding.getSecret().get("model-name"));
                }
            }
        });

        bindings.filterBindings(USER_PROVIDED_TYPE).forEach(binding -> {
            if (binding.getSecret().get(MCP_SERVICE_CREDENTIAL) != null) {
                logger.info( "MCP Service Credential: " + binding.getSecret().get(MCP_SERVICE_CREDENTIAL));
            }
        });
    }
}