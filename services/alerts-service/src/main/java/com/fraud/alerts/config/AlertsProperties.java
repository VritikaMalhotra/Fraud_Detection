package com.fraud.alerts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "app.alerts")
public class AlertsProperties {

    private Set<String> decisions = Set.of("REVIEW", "BLOCK");
    private SlackConfig slack = new SlackConfig();

    @Data
    public static class SlackConfig {
        private String webhookUrl;
        private Duration dedupeWindow = Duration.ofMinutes(5);
    }
}
