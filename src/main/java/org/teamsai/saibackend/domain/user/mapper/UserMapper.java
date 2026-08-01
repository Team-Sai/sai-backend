package org.teamsai.saibackend.domain.user.mapper;

import org.apache.catalina.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import java.util.Optional;

@Mapper
public interface UserMapper {
    Optional<UserDTO> findById(@Param("userId") Long userId);
    void updateUserKey(@Param("userId")Long userId, @Param("userKey") String userKey);

    int insert(UserDTO user);

    boolean existsByEmail(
            @Param("email") String email
    );

    Optional<UserDTO> findByEmail(
            @Param("email") String email
    );

    Optional<UserDTO> findByUserKey(
            @Param("userKey") String userKey
    );

    int deleteByUserKey(String userKey);

    boolean existsByUserKey(
            @Param("userKey") String userKey
    );
}