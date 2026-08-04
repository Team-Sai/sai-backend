package org.teamsai.saibackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.link.mapper.LinkMapper;
import org.teamsai.saibackend.domain.link.dto.response.UserKeyResponse;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.client.MockBankClient;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserMapper userMapper;
    private final LinkMapper linkMapper;
    private final MockBankClient mockBankClient;

    public UserKeyResponse issueOrGetUserKey(Long userId) {
        UserDTO user = userMapper.findById(userId)
                .orElseThrow(() -> UserErrorCode.USER_NOT_FOUND.toException());

        if (user.getUserKey() != null) {
            return new UserKeyResponse(user.getUserKey());
        }

        String newKey = mockBankClient.requestUserKey(user.getName(), user.getUserToken());
        user.setUserKey(newKey);
        linkMapper.updateUserKey(user.getUserId(), newKey);

        return new UserKeyResponse(newKey);
    }
}
