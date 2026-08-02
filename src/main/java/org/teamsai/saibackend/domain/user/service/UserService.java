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

    public UserResponse getMyInfo(Long userId) {
        UserDTO user = getUser(userId);

        return UserResponse.from(user);
    }

    @Transactional
    public void withdraw(Long userId) {
        int deletedCount = userMapper.deleteByUserId(userId);

        if (deletedCount == 0) {
            throw UserErrorCode.USER_NOT_FOUND.toException();
        }
    }

    private UserDTO getUser(Long userId) {
        return userMapper.findById(userId)
                .orElseThrow(
                        UserErrorCode.USER_NOT_FOUND::toException
                );
    }

    @Transactional(readOnly = true)
    public String getUserKeyByUserToken(String userToken) {
        return userMapper.findUserKeyByUserToken(userToken);
    }

    @Transactional(readOnly = true)
    public String getUserKeyByUserId(Long userId){
        return userMapper.findUserKeyByUserId(userId);
    }
}