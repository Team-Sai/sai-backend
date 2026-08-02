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

    int updateUserKey(
            @Param("userToken") String userToken,
            @Param("userKey") String userKey
    );

    int deleteByUserKey(String userToken);

    boolean existsByUserToken(
            @Param("userToken") String userToken
    );

    String findUserKeyByUserToken(
            @Param("userToken") String userToken
    );
    
    String findUserKeyByUserId(
            @Param("userId") Long userId
    );
}