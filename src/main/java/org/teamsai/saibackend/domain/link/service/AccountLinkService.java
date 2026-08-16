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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLinkService {

    private final UserMapper userMapper;
    private final LinkedBankAccountService linkedBankAccountService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void completeLink(Long userId, String userKey, List<Long> accountIds) {
        String previousUserKey = userMapper.findUserKeyByUserId(userId);

        int updated = userMapper.updateUserKeyByUserId(userId, userKey);
        if (updated == 0) {
            log.warn("[AccountLinkService] userKey 갱신 실패 - userId: {}", userId);
            throw UserErrorCode.LINK_KEY_UPDATE_CONFLICT.toException();
        }

        linkedBankAccountService.linkAccountsByIds(userId, userKey, accountIds);

        if (previousUserKey != null && !previousUserKey.equals(userKey)) {
            eventPublisher.publishEvent(new PreviousUserKeyRevokedEvent(userId, previousUserKey));
        }
    }
}