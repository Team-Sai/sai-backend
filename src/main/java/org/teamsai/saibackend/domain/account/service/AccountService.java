package org.teamsai.saibackend.domain.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.link.mapper.LinkMapper;
import org.teamsai.saibackend.domain.link.dto.response.UserKeyResponse;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.client.MockBankClient;

@Slf4j
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

        try {
            mockBankClient.confirmUserKey(newKey);
        } catch (Exception e) {
            log.warn("[AccountService] userKey confirm 실패 - userId: {}", userId, e);
            throw AccountErrorCode.BANK_SERVER_UNAVAILABLE.toException();
        }

        linkMapper.updateUserKey(userId, newKey);

        return new UserKeyResponse(newKey);
    }
}
