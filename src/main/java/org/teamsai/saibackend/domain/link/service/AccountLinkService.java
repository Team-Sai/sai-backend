package org.teamsai.saibackend.domain.link.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.link.event.PreviousUserKeyRevokedEvent;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.client.MockBankClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLinkService {

    private final UserMapper userMapper;
    private final LinkedBankAccountService linkedBankAccountService;
    private final ApplicationEventPublisher eventPublisher;
    private final MockBankClient mockBankClient;

    @Transactional
    public void completeLink(Long userId, String userKey, List<Long> accountIds) {
        String previousUserKey = userMapper.findUserKeyByUserId(userId);

        int updated = userMapper.updateUserKeyByUserId(userId, userKey, previousUserKey);
        if (updated == 0) {
            log.warn(
                    "[AccountLinkService] userKey 갱신 실패(동시 요청 경합 가능) - userId: {}",
                    userId
            );
            revokeOrphanedKey(userId, userKey);
            throw UserErrorCode.LINK_KEY_UPDATE_CONFLICT.toException();
        }

        linkedBankAccountService.linkAccountsByIds(userId, userKey, accountIds);

        if (previousUserKey != null && !previousUserKey.equals(userKey)) {
            eventPublisher.publishEvent(new PreviousUserKeyRevokedEvent(userId, previousUserKey));
        }
    }

    private void revokeOrphanedKey(Long userId, String userKey) {
        try {
            mockBankClient.revokeUserKey(userKey);
            log.info("[AccountLinkService] 경합으로 저장 실패한 키 revoke 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error(
                    "[AccountLinkService] 경합으로 저장 실패한 키의 revoke마저 실패 - userId: {}. "
                            + "mock-bank에 이 키가 ACTIVE 상태로 남아있을 수 있어 수동 확인이 필요합니다.",
                    userId, e
            );
        }
    }
}