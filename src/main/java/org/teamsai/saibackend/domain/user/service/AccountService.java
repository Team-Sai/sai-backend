package org.teamsai.saibackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.link.mapper.LinkMapper;
import org.teamsai.saibackend.domain.user.controller.MockBankClient;
import org.teamsai.saibackend.domain.link.dto.response.UserKeyResponse;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserMapper userMapper;
    private final LinkMapper linkMapper;
    private final MockBankClient mockBankClient;

    public UserKeyResponse issueOrGetUserKey(Long userId, String name, String userToken) {
        UserDTO user = userMapper.findById(userId).orElseThrow();
        if (user.getUserKey() != null) {
            return new UserKeyResponse(user.getUserKey());
        }
        String newKey = mockBankClient.requestUserKey(name, userToken);
        user.setUserKey(newKey);
        linkMapper.updateUserKey(userId,newKey);
        return new UserKeyResponse(newKey);
    }
}
