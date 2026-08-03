package org.teamsai.saibackend.domain.link.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LinkMapper {
    void updateUserKey(@Param("userId")Long userId, @Param("userKey") String userKey);
}
