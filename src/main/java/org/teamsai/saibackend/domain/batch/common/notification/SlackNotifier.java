package org.teamsai.saibackend.domain.batch.common.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SlackNotifier {

    private final RestClient restClient = RestClient.create();

    @Value("${slack.batch-notification.webhook-url:}")
    private String webhookUrl;

    @Value("${slack.batch-notification.enabled:false}")
    private boolean enabled;

    public void send(String message) {
        if (!enabled || webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("[SlackNotifier] Slack 알림 비활성화 또는 webhook URL 없음, 전송 스킵");  // warn → debug로 복구
            return;
        }
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("text", message))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[SlackNotifier] Slack 알림 전송 성공");
        } catch (Exception e) {
            log.warn("[SlackNotifier] Slack 알림 전송 실패", e);
        }
    }
}