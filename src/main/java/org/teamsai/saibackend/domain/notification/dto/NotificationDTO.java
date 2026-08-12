package org.teamsai.saibackend.domain.notification.dto;

import lombok.*;
import org.teamsai.saibackend.domain.notification.type.NotificationType;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long notificationId;
    private Long userId;
    private NotificationType notificationType;
    private String title;
    private String content;
    private Long referenceId;
    private boolean read;
    private LocalDateTime createdAt;
}
