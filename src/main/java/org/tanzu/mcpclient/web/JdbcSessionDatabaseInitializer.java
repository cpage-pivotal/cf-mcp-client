package org.tanzu.mcpclient.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.tanzu.mcpclient.vectorstore.DatabaseAvailableCondition;

@Component
@Order(1) // Ensure this runs early in the startup process
@Conditional(DatabaseAvailableCondition.class)
public class JdbcSessionDatabaseInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(JdbcSessionDatabaseInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcSessionDatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        if (sessionTableExists()) {
            logger.info("Spring Session tables already exist, skipping initialization");
            return;
        }

        logger.info("Initializing Spring Session tables");

        try {
            // Create SPRING_SESSION table
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION (" +
                    "PRIMARY_ID CHAR(36) NOT NULL, " +
                    "SESSION_ID CHAR(36) NOT NULL, " +
                    "CREATION_TIME BIGINT NOT NULL, " +
                    "LAST_ACCESS_TIME BIGINT NOT NULL, " +
                    "MAX_INACTIVE_INTERVAL INT NOT NULL, " +
                    "EXPIRY_TIME BIGINT NOT NULL, " +
                    "PRINCIPAL_NAME VARCHAR(100), " +
                    "CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID))");

            // Create indexes
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME)");

            // Create SPRING_SESSION_ATTRIBUTES table
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (" +
                    "SESSION_PRIMARY_ID CHAR(36) NOT NULL, " +
                    "ATTRIBUTE_NAME VARCHAR(200) NOT NULL, " +
                    "ATTRIBUTE_BYTES BYTEA NOT NULL, " +
                    "CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME), " +
                    "CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) " +
                    "REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE)");

            logger.info("Spring Session tables created successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize database schema", e);
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    private boolean sessionTableExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'spring_session'",
                    Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            logger.warn("Could not check if Spring Session tables exist: {}", e.getMessage());
            return false;
        }
    }
}