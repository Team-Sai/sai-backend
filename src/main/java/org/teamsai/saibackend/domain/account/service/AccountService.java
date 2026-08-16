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

        try {
            linkMapper.updateUserKey(userId, newKey);
        } catch (Exception e) {
            log.error("[AccountService] confirm 성공 후 로컬 저장 실패 - userId: {}. "
                    + "mock-bank에 이 userKey가 ACTIVE 상태로 남아있어 revoke를 시도합니다.", userId, e);
            revokeConfirmedKey(userId, newKey);
            throw AccountErrorCode.BANK_SERVER_UNAVAILABLE.toException();
        }

        return new UserKeyResponse(newKey);
    }

    private void revokeConfirmedKey(Long userId, String userKey) {
        try {
            mockBankClient.revokeUserKey(userKey);
            log.info("[AccountService] 로컬 저장 실패로 mock-bank confirm 취소 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error(
                    "[AccountService] mock-bank confirm 취소마저 실패 - userId: {}. "
                            + "mock-bank에 이 userKey가 ACTIVE 상태로 남아있을 수 있어 수동 확인이 필요합니다.",
                    userId, e
            );
        }
    }
}