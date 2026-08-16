package org.teamsai.saibackend.domain.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.notification.dto.NotificationDTO;
import org.teamsai.saibackend.domain.notification.dto.response.NotificationResponse;
import org.teamsai.saibackend.domain.notification.type.NotificationType;

import java.util.List;

@Mapper
public interface NotificationMapper {

    int insert(NotificationDTO notification);

    List<NotificationResponse> findAllByUserId(
            @Param("userId") Long userId
    );

    boolean existsByUserIdAndTypeAndReferenceId(
            @Param("userId") Long userId,
            @Param("notificationType") NotificationType notificationType,
            @Param("referenceId") Long referenceId
    );

}
