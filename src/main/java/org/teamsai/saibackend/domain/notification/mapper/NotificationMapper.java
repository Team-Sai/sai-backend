package org.teamsai.saibackend.domain.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.notification.dto.NotificationDTO;
import org.teamsai.saibackend.domain.notification.dto.response.NotificationResponse;

import java.util.List;

@Mapper
public interface NotificationMapper {

    int insert(NotificationDTO notification);

    List<NotificationResponse> findAllByUserId(
            @Param("userId") Long userId
    );

}