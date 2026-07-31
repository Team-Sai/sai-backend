package org.teamsai.saibackend.domain.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.user.entity.User;

import java.util.Optional;

@Mapper
public interface UserMapper {
    Optional<User> findById(@Param("userId") Long userId);
    Optional<User> findByUserKey(@Param("userKey") String userKey);
    void updateUserKey(@Param("userId")Long userId, @Param("userKey") String userKey);
}
