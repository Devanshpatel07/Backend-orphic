package com.company.employeemanagement.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class DbSchemaPatcher implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info("Executing DB Schema patch for leave_requests.leave_type column...");

        // Try MySQL syntax: MODIFY COLUMN to VARCHAR(50)
        boolean patched = false;
        try {
            jdbcTemplate.execute("ALTER TABLE leave_requests MODIFY COLUMN leave_type VARCHAR(50) NOT NULL");
            log.info("ALTER TABLE leave_requests MODIFY COLUMN leave_type VARCHAR(50) NOT NULL succeeded.");
            patched = true;
        } catch (Exception e) {
            log.debug("Notice on MySQL MODIFY COLUMN: {}", e.getMessage());
        }

        // If MySQL syntax didn't apply (e.g. PostgreSQL), try ALTER COLUMN
        if (!patched) {
            try {
                jdbcTemplate.execute("ALTER TABLE leave_requests ALTER COLUMN leave_type TYPE VARCHAR(50)");
                log.info("ALTER TABLE leave_requests ALTER COLUMN leave_type TYPE VARCHAR(50) succeeded.");
            } catch (Exception e) {
                log.debug("Notice on PostgreSQL ALTER COLUMN: {}", e.getMessage());
            }
        }

        // Try modifying ENUM if table column is MySQL ENUM
        try {
            jdbcTemplate.execute("ALTER TABLE leave_requests MODIFY COLUMN leave_type ENUM('CASUAL', 'SICK', 'EARNED', 'UNPAID', 'WORK_FROM_HOME', 'OTHER') NOT NULL");
            log.info("ALTER TABLE leave_requests MODIFY COLUMN leave_type ENUM(...) succeeded.");
        } catch (Exception e) {
            log.debug("Notice on ENUM alter: {}", e.getMessage());
        }
    }
}
