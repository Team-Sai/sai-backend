package org.teamsai.saibackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.link.mapper.LinkMapper;
import org.teamsai.saibackend.domain.link.dto.response.UserKeyResponse;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.client.MockBankClient;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserMapper userMapper;
    private final LinkMapper linkMapper;
    private final MockBankClient mockBankClient;

    public UserKeyResponse issueOrGetUserKey(String userToken) {
        UserDTO user = userMapper.findByUserToken(userToken).orElseThrow();
        if (user.getUserKey() != null) {
            return new UserKeyResponse(user.getUserKey());
        }
        String newKey = mockBankClient.requestUserKey(user.getName(), userToken);
        user.setUserKey(newKey);
        linkMapper.updateUserKey(user.getUserId(),newKey);
        return new UserKeyResponse(newKey);
    }
}
