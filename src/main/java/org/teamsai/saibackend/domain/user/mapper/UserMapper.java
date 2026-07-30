package org.teamsai.saibackend.domain.user.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.user.entity.User;

import java.util.Optional;

@Mapper
public interface UserMapper {

    int insert(User user);

    boolean existsByEmail(
            @Param("email") String email
    );

    Optional<User> findByEmail(
            @Param("email") String email
    );

    Optional<User> findByUserKey(
            @Param("userKey") String userKey
    );

    int deleteById(
            @Param("userId") Long userId
    );

    boolean existsByUserKey(
            @Param("userKey") String userKey
    );
}