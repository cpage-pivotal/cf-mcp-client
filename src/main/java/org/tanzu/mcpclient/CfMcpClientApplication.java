package org.tanzu.mcpclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CfMcpClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(CfMcpClientApplication.class, args);
	}


    private static final Logger logger = LoggerFactory.getLogger(CfMcpClientApplication.class);

    @Bean
    public CommandLineRunner vcapServicesDumper() {
        return args -> {
            String vcapServices = System.getenv("VCAP_SERVICES");

            logger.info("=== VCAP_SERVICES Environment Variable ===");

            if (vcapServices == null || vcapServices.trim().isEmpty()) {
                logger.info("VCAP_SERVICES is not set or is empty");
            } else {
                logger.info("VCAP_SERVICES content:");

                // Try to pretty-print JSON if possible
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    Object jsonObject = objectMapper.readValue(vcapServices, Object.class);
                    String prettyJson = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(jsonObject);
                    logger.info(prettyJson);
                } catch (Exception e) {
                    // If JSON parsing fails, just print raw content
                    logger.info("Raw content (not valid JSON):");
                    logger.info(vcapServices);
                }
            }

            logger.info("==========================================");
        };
    }
}
