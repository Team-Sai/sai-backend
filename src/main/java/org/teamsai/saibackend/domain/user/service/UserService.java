package org.teamsai.saibackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.user.dto.response.UserResponse;
import org.teamsai.saibackend.domain.user.entity.User;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserMapper userMapper;

    public UserResponse getMyInfo(String userKey) {
        User user = getUser(userKey);

        return UserResponse.from(user);
    }

    @Transactional
    public void withdraw(String userKey) {
        User user = getUser(userKey);

        int deletedCount = userMapper.deleteById(user.getUserId());

        if (deletedCount == 0) {
            throw UserErrorCode.USER_NOT_FOUND.toException();
        }
    }

    private User getUser(String userKey) {
        return userMapper.findByUserKey(userKey)
                .orElseThrow(
                        UserErrorCode.USER_NOT_FOUND::toException
                );
    }
}