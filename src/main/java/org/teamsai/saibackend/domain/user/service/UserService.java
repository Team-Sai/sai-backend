package org.teamsai.saibackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.user.dto.response.UserResponse;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserMapper userMapper;

    public UserResponse getMyInfo(String userToken) {
        UserDTO user = getUser(userToken);

        return UserResponse.from(user);
    }

    @Transactional
    public void withdraw(String userToken) {
        int deletedCount = userMapper.deleteByUserKey(userToken);

        if (deletedCount == 0) {
            throw UserErrorCode.USER_NOT_FOUND.toException();
        }
    }

    private UserDTO getUser(String userToken) {
        return userMapper.findByUserToken(userToken)
                .orElseThrow(
                        UserErrorCode.USER_NOT_FOUND::toException
                );
    }

    @Transactional(readOnly = true)
    public String getUserKeyByUserToken(String userToken) {
        // user 테이블에서 userId로 userKey(연동키) 조회
        return userMapper.findUserKeyByUserToken(userToken);
    }
}