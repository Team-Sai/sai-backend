package org.teamsai.saibackend.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.notification.dto.NotificationDTO;
import org.teamsai.saibackend.domain.notification.dto.response.NotificationResponse;
import org.teamsai.saibackend.domain.notification.mapper.NotificationMapper;
import org.teamsai.saibackend.domain.notification.type.NotificationType;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    public void create(Long userId, NotificationType type, String title, String content, Long referenceId){
        create(userId, type, title, content, referenceId, null);
    }

    public void create(Long userId, NotificationType type, String title, String content, Long referenceId, Long secondaryReferenceId){
        NotificationDTO notification = NotificationDTO.builder()
                .userId(userId)
                .notificationType(type)
                .title(title)
                .content(content)
                .referenceId(referenceId)
                .secondaryReferenceId(secondaryReferenceId)
                .createdAt(LocalDateTime.now())
                .build();

        notificationMapper.insert(notification);
    }

    @Transactional
    public void createIfAbsent(
            Long userId,
            NotificationType type,
            String title,
            String content,
            Long referenceId,
            Long secondaryReferenceId
    ) {
        if (notificationMapper.existsByUserIdAndTypeAndReferenceId(
                userId,
                type,
                referenceId
        )) {
            return;
        }

        create(
                userId,
                type,
                title,
                content,
                referenceId,
                secondaryReferenceId
        );
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationMapper.findAllByUserId(userId);
    }
}
