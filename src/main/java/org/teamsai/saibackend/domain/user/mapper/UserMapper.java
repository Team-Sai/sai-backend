package org.teamsai.saibackend.domain.user.mapper;

import org.apache.catalina.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import java.util.Optional;

@Mapper
public interface UserMapper {
    int insert(UserDTO user);

    boolean existsByEmail(
            @Param("email") String email
    );

    Optional<UserDTO> findById(@Param("userId") Long userId);

    Optional<UserDTO> findByEmail(
            @Param("email") String email
    );

    Optional<UserDTO> findByUserToken(
            @Param("userToken") String userToken
    );

    int deleteByUserToken(String userKey);

    boolean existsByUserToken(
            @Param("userToken") String userToken
    );
}