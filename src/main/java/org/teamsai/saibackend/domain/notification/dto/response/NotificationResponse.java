package org.teamsai.saibackend.domain.notification.dto.response;

import org.teamsai.saibackend.domain.notification.type.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType notificationType,
        String title,
        String content,
        Long referenceId,
        LocalDateTime createdAt
) {
}
