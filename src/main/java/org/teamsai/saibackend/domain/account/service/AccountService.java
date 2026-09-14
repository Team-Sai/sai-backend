package org.teamsai.saibackend.domain.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.link.dto.response.UserKeyResponse;
import org.teamsai.saibackend.domain.link.mapper.LinkMapper;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.client.MockBankClient;
import org.teamsai.saibackend.global.client.UserKeyRevoker;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String CALLER = "AccountService";

    private final UserMapper userMapper;
    private final LinkMapper linkMapper;
    private final MockBankClient mockBankClient;
    private final UserKeyRevoker userKeyRevoker;

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

        int updatedRow;
        try {
            updatedRow = linkMapper.updateUserKey(userId, newKey);
        } catch (Exception e) {
            log.error("[AccountService] confirm 성공 후 로컬 저장 중 오류 - userId: {}. "
                    + "mock-bank에 이 userKey가 ACTIVE 상태로 남아있어 revoke를 시도합니다.", userId, e);
            userKeyRevoker.revokeBestEffort(CALLER, userId, newKey);
            throw AccountErrorCode.LOCAL_KEY_SAVE_FAILED.toException();
        }

        if (updatedRow == 0) {
            log.warn("[AccountService] 동시 요청으로 userKey 저장 충돌 - userId: {}. "
                    + "다른 요청이 이미 userKey를 저장한 것으로 추정되어 이 키는 revoke합니다.", userId);
            userKeyRevoker.revokeBestEffort(CALLER, userId, newKey);
            throw AccountErrorCode.USER_KEY_ALREADY_LINKED.toException();
        }

        return new UserKeyResponse(newKey);
    }
}