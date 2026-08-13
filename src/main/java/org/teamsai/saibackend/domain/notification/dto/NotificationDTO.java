package org.teamsai.saibackend.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
    private Long secondaryReferenceId;
    private boolean read;
    private LocalDateTime createdAt;
}
