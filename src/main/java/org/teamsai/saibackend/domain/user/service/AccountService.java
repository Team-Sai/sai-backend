package org.teamsai.saibackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.user.controller.MockBankClient;
import org.teamsai.saibackend.domain.user.dto.UserKeyResponse;
import org.teamsai.saibackend.domain.user.entity.User;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserMapper userMapper;
    private final MockBankClient mockBankClient;

    public UserKeyResponse issueOrGetUserKey(Long userId, String name, String email) {
        User user = userMapper.findById(userId).orElseThrow();
        if (user.getUserKey() != null) {
            return new UserKeyResponse(user.getUserKey());
        }
        String newKey = mockBankClient.requestUserKey(name, email);
        userMapper.updateUserKey(userId,newKey);
        return new UserKeyResponse(newKey);
    }
}
