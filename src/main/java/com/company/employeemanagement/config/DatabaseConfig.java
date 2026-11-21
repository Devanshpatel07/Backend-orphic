package com.company.employeemanagement.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("!test")
@Slf4j
public class DatabaseConfig {

    private final Environment env;

    public DatabaseConfig(Environment env) {
        this.env = env;
    }

    public static class ResolvedDbInfo {
        public String jdbcUrl;
        public String username;
        public String password;
        public String driverClassName;
        public String dialect;
    }

    /**
     * Builds the DataSource directly from the resolved DB info.
     * NOTE: do not expose a DataSourceProperties bean here — Spring re-binds
     * "spring.datasource.*" onto it after creation, which would overwrite the
     * resolved URL/driver (e.g. a raw postgres:// URL with the MySQL driver).
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource() {
        ResolvedDbInfo info = resolveDbInfo();
        log.info("Initialized Database Configuration - Driver: [{}], User: [{}], Target: [{}]",
                info.driverClassName, info.username, maskJdbcUrl(info.jdbcUrl));

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(info.jdbcUrl);
        ds.setUsername(info.username);
        ds.setPassword(info.password);
        ds.setDriverClassName(info.driverClassName);
        ds.setConnectionTimeout(30000);
        return ds;
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties -> {
            ResolvedDbInfo info = resolveDbInfo();
            if (info.dialect != null && !info.dialect.isBlank()) {
                hibernateProperties.put("hibernate.dialect", info.dialect);
            }
        };
    }

    public ResolvedDbInfo resolveDbInfo() {
        ResolvedDbInfo info = new ResolvedDbInfo();

        // Check common database URL environment variables (Render, Heroku, Railway, standard Spring)
        String rawUrl = getFirstNonBlank(
                env.getProperty("SPRING_DATASOURCE_URL"),
                env.getProperty("DATABASE_URL"),
                env.getProperty("MYSQL_URL"),
                env.getProperty("MYSQL_PRIVATE_URL"),
                env.getProperty("MYSQL_PUBLIC_URL"),
                env.getProperty("DB_URL"),
                env.getProperty("spring.datasource.url")
        );

        String dbHost = getFirstNonBlank(
                env.getProperty("DB_HOST"),
                env.getProperty("MYSQLHOST")
        );

        // In case someone passed the full URL into DB_HOST
        if ((rawUrl == null || rawUrl.isBlank()) && dbHost != null && (dbHost.contains("://") || dbHost.contains("@"))) {
            rawUrl = dbHost;
            dbHost = null;
        }

        String fallbackUsername = getFirstNonBlank(
                env.getProperty("DB_USERNAME"),
                env.getProperty("DB_USER"),
                env.getProperty("MYSQLUSER"),
                "root"
        );

        String fallbackPassword = getFirstNonBlank(
                env.getProperty("DB_PASSWORD"),
                env.getProperty("MYSQLPASSWORD"),
                "Devansh@2004"
        );

        String fallbackPort = getFirstNonBlank(
                env.getProperty("DB_PORT"),
                env.getProperty("MYSQLPORT"),
                "3306"
        );

        String fallbackDbName = getFirstNonBlank(
                env.getProperty("DB_NAME"),
                env.getProperty("MYSQLDATABASE"),
                "employee_management"
        );

        if (rawUrl != null && !rawUrl.isBlank()) {
            parseDatabaseUrl(rawUrl.trim(), fallbackUsername, fallbackPassword, fallbackDbName, info);
        } else {
            String host = (dbHost != null && !dbHost.isBlank()) ? dbHost.trim() : "localhost";
            int port;
            try {
                port = Integer.parseInt(fallbackPort.trim());
            } catch (Exception e) {
                port = 3306;
            }
            String db = (fallbackDbName != null && !fallbackDbName.isBlank()) ? fallbackDbName.trim() : "employee_management";

            info.username = fallbackUsername;
            info.password = fallbackPassword;
            info.driverClassName = "com.mysql.cj.jdbc.Driver";
            info.dialect = "org.hibernate.dialect.MySQLDialect";

            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
                info.jdbcUrl = String.format(
                        "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true",
                        host, port, db
                );
            } else {
                info.jdbcUrl = String.format(
                        "jdbc:mysql://%s:%d/%s?serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true",
                        host, port, db
                );
            }
        }

        return info;
    }

    public static void parseDatabaseUrl(String rawUrl, String defaultUser, String defaultPass, String defaultDb, ResolvedDbInfo info) {
        String cleanUrl = rawUrl;
        boolean wasJdbc = cleanUrl.startsWith("jdbc:");
        if (wasJdbc) {
            cleanUrl = cleanUrl.substring(5);
        }

        boolean isPostgres = cleanUrl.startsWith("postgres://") || cleanUrl.startsWith("postgresql://");

        if (isPostgres) {
            info.driverClassName = "org.postgresql.Driver";
            info.dialect = "org.hibernate.dialect.PostgreSQLDialect";
        } else {
            info.driverClassName = "com.mysql.cj.jdbc.Driver";
            info.dialect = "org.hibernate.dialect.MySQLDialect";
        }

        if (cleanUrl.contains("://")) {
            int schemeIdx = cleanUrl.indexOf("://");
            String afterScheme = cleanUrl.substring(schemeIdx + 3);

            String userInfo = null;
            String hostPortAndPath = afterScheme;

            int lastAt = afterScheme.lastIndexOf('@');
            if (lastAt != -1) {
                userInfo = afterScheme.substring(0, lastAt);
                hostPortAndPath = afterScheme.substring(lastAt + 1);
            }

            if (userInfo != null && !userInfo.isBlank()) {
                int colonIdx = userInfo.indexOf(':');
                if (colonIdx != -1) {
                    info.username = safeDecode(userInfo.substring(0, colonIdx));
                    info.password = safeDecode(userInfo.substring(colonIdx + 1));
                } else {
                    info.username = safeDecode(userInfo);
                    info.password = defaultPass;
                }
            } else {
                info.username = defaultUser;
                info.password = defaultPass;
            }

            String hostPort;
            String pathAndQuery = "";
            int slashIdx = hostPortAndPath.indexOf('/');
            if (slashIdx != -1) {
                hostPort = hostPortAndPath.substring(0, slashIdx);
                pathAndQuery = hostPortAndPath.substring(slashIdx + 1);
            } else {
                hostPort = hostPortAndPath;
            }

            String host = hostPort;
            int defaultPort = isPostgres ? 5432 : 3306;
            int port = defaultPort;
            int portColon = hostPort.lastIndexOf(':');
            if (portColon != -1) {
                host = hostPort.substring(0, portColon);
                try {
                    port = Integer.parseInt(hostPort.substring(portColon + 1));
                } catch (Exception ignored) {
                }
            }

            String dbName = defaultDb;
            String query = null;
            int qIdx = pathAndQuery.indexOf('?');
            if (qIdx != -1) {
                dbName = pathAndQuery.substring(0, qIdx);
                query = pathAndQuery.substring(qIdx + 1);
            } else if (!pathAndQuery.isBlank()) {
                dbName = pathAndQuery;
            }

            if (dbName == null || dbName.isBlank()) {
                dbName = defaultDb;
            }

            if (isPostgres) {
                info.jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
                if (query != null && !query.isBlank()) {
                    info.jdbcUrl += "?" + query;
                }
            } else {
                info.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName;
                if (query != null && !query.isBlank()) {
                    info.jdbcUrl += "?" + query;
                    if (!query.contains("serverTimezone")) {
                        info.jdbcUrl += "&serverTimezone=UTC";
                    }
                    if (!query.contains("allowPublicKeyRetrieval")) {
                        info.jdbcUrl += "&allowPublicKeyRetrieval=true";
                    }
                } else {
                    if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
                        info.jdbcUrl += "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true";
                    } else {
                        info.jdbcUrl += "?serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true";
                    }
                }
            }
        } else {
            info.username = defaultUser;
            info.password = defaultPass;
            if (rawUrl.startsWith("jdbc:")) {
                info.jdbcUrl = rawUrl;
            } else {
                info.jdbcUrl = (isPostgres ? "jdbc:postgresql://" : "jdbc:mysql://") + rawUrl;
            }
        }
    }

    private static String safeDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private static String getFirstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) return "null";
        return jdbcUrl.replaceAll("(?i)(password=)[^&;]+", "$1*****");
    }
}
