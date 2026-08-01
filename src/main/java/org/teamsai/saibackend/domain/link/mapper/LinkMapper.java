package org.teamsai.saibackend.domain.link.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.user.dto.UserDTO;

import java.util.Optional;

@Mapper
public interface LinkMapper {
    void updateUserKey(@Param("userId")Long userId, @Param("userKey") String userKey);
}
