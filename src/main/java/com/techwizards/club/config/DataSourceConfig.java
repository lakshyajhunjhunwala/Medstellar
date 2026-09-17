package com.techwizards.club.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url:}")
    private String configuredUrl;

    @Value("${spring.datasource.username:}")
    private String configuredUsername;

    @Value("${spring.datasource.password:}")
    private String configuredPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        // 1. Check environment variables: DATABASE_URL, SPRING_DATASOURCE_URL
        String envDbUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (envDbUrl == null || envDbUrl.trim().isEmpty()) {
            envDbUrl = System.getenv("DATABASE_URL");
        }

        String targetUrl = (envDbUrl != null && !envDbUrl.trim().isEmpty()) ? envDbUrl.trim() : configuredUrl;
        String username = configuredUsername;
        String password = configuredPassword;

        String envUser = System.getenv("SPRING_DATASOURCE_USERNAME");
        if (envUser != null && !envUser.trim().isEmpty()) username = envUser.trim();

        String envPass = System.getenv("SPRING_DATASOURCE_PASSWORD");
        if (envPass != null && !envPass.trim().isEmpty()) password = envPass.trim();

        // Check if targetUrl is Render postgres format: postgres://user:password@host:port/database
        if (targetUrl != null && (targetUrl.startsWith("postgres://") || targetUrl.startsWith("postgresql://"))) {
            try {
                String uriString = targetUrl;
                if (uriString.startsWith("postgres://")) {
                    uriString = "postgresql://" + uriString.substring(11);
                }
                URI uri = new URI(uriString);
                String userInfo = uri.getUserInfo();
                if (userInfo != null) {
                    String[] parts = userInfo.split(":", 2);
                    username = parts[0];
                    if (parts.length > 1) {
                        password = parts[1];
                    }
                }
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath();
                targetUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + path;
            } catch (Exception e) {
                System.err.println("Warning: Could not parse postgres DATABASE_URL as URI: " + e.getMessage());
            }
        }

        // Detect if running in cloud container (Render sets RENDER=true or PORT!=8081) and no cloud DB provided
        boolean isRender = "true".equalsIgnoreCase(System.getenv("RENDER"))
                || (System.getenv("PORT") != null && !"8081".equals(System.getenv("PORT")));

        boolean hasExplicitCloudDb = (envDbUrl != null && !envDbUrl.trim().isEmpty())
                || (configuredUrl != null && !configuredUrl.contains("localhost:3306"));

        // If in Render/cloud container and no remote DB URL configured, fall back to embedded file-based H2
        if (isRender && !hasExplicitCloudDb) {
            System.out.println("No external cloud database URL detected on Render. Falling back to persistent H2 database.");
            targetUrl = "jdbc:h2:file:./data/medstellar_db;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
            username = "sa";
            password = "";
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(targetUrl);
        if (username != null && !username.trim().isEmpty()) {
            config.setUsername(username);
        }
        if (password != null) {
            config.setPassword(password);
        }

        // Set driver class name explicitly if possible
        if (targetUrl != null) {
            if (targetUrl.startsWith("jdbc:postgresql:")) {
                config.setDriverClassName("org.postgresql.Driver");
            } else if (targetUrl.startsWith("jdbc:mysql:")) {
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            } else if (targetUrl.startsWith("jdbc:h2:")) {
                config.setDriverClassName("org.h2.Driver");
            }
        }

        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        return new HikariDataSource(config);
    }
}
