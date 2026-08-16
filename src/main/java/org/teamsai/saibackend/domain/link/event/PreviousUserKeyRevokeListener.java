package org.teamsai.saibackend.domain.link.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.teamsai.saibackend.global.client.MockBankClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class PreviousUserKeyRevokeListener {

    private final MockBankClient mockBankClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PreviousUserKeyRevokedEvent event) {
        try {
            mockBankClient.revokeUserKey(event.previousUserKey());
            log.info("[PreviousUserKeyRevokeListener] 키 교체로 이전 userKey revoke 완료 - userId: {}", event.userId());
        } catch (Exception e) {
            log.error(
                    "[PreviousUserKeyRevokeListener] 이전 userKey revoke 실패 - userId: {}. "
                            + "mock-bank에 이전 키가 ACTIVE 상태로 남아있을 수 있어 수동 확인이 필요합니다.",
                    event.userId(), e
            );
        }
    }
}