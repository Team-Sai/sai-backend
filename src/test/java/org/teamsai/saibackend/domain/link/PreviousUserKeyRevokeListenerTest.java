package org.teamsai.saibackend.domain.link;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.link.event.PreviousUserKeyRevokeListener;
import org.teamsai.saibackend.domain.link.event.PreviousUserKeyRevokedEvent;
import org.teamsai.saibackend.global.client.UserKeyRevoker;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PreviousUserKeyRevokeListener 단위 테스트")
class PreviousUserKeyRevokeListenerTest {

    @Mock
    private UserKeyRevoker userKeyRevoker;
    @InjectMocks
    private PreviousUserKeyRevokeListener listener;

    private static final Long USER_ID = 1L;
    private static final String OLD_KEY = "mb_oldkey";

    @Test
    @DisplayName("이벤트를 받으면 UserKeyRevoker에 이전 userKey revoke를 위임한다")
    void delegatesRevokeToUserKeyRevokerOnEvent() {
        listener.handle(new PreviousUserKeyRevokedEvent(USER_ID, OLD_KEY));

        verify(userKeyRevoker).revokeBestEffort("PreviousUserKeyRevokeListener", USER_ID, OLD_KEY);
    }
}
