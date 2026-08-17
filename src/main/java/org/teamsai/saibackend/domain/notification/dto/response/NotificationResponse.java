package org.teamsai.saibackend.domain.notification.dto.response;

import org.teamsai.saibackend.domain.notification.type.NotificationType;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType notificationType,
        String title,
        String content,
        Long referenceId,
        Long secondaryReferenceId,
        BankTransactionProcessingStatus relatedTransactionStatus,
        boolean resolved,
        LocalDateTime createdAt
) {
}
