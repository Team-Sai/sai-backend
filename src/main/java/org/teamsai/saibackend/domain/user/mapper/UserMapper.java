package org.teamsai.saibackend.domain.user.mapper;


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

    Optional<UserDTO> findByEmail(
            @Param("email") String email
    );

    Optional<UserDTO> findByUserKey(
            @Param("userKey") String userKey
    );

    int deleteById(
            @Param("userId") Long userId
    );

    boolean existsByUserKey(
            @Param("userKey") String userKey
    );
}