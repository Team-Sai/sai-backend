package org.teamsai.saibackend.domain.link.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.teamsai.saibackend.global.client.UserKeyRevoker;

@Component
@RequiredArgsConstructor
public class PreviousUserKeyRevokeListener {

    private static final String CALLER = "PreviousUserKeyRevokeListener";

    private final UserKeyRevoker userKeyRevoker;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PreviousUserKeyRevokedEvent event) {
        userKeyRevoker.revokeBestEffort(CALLER, event.userId(), event.previousUserKey());
    }
}
