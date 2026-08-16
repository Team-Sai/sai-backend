package org.teamsai.saibackend.domain.link;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.link.event.PreviousUserKeyRevokeListener;
import org.teamsai.saibackend.domain.link.event.PreviousUserKeyRevokedEvent;
import org.teamsai.saibackend.global.client.MockBankClient;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PreviousUserKeyRevokeListener 단위 테스트")
class PreviousUserKeyRevokeListenerTest {

    @Mock
    private MockBankClient mockBankClient;
    @InjectMocks
    private PreviousUserKeyRevokeListener listener;

    private static final Long USER_ID = 1L;
    private static final String OLD_KEY = "mb_oldkey";

    @Test
    @DisplayName("이벤트를 받으면 mock-bank에 이전 userKey revoke를 요청한다")
    void revokesPreviousUserKeyOnEvent() {
        listener.handle(new PreviousUserKeyRevokedEvent(USER_ID, OLD_KEY));

        verify(mockBankClient).revokeUserKey(OLD_KEY);
    }

    @Test
    @DisplayName("revoke 호출이 실패해도 예외를 전파하지 않고 삼킨다")
    void doesNotPropagateExceptionWhenRevokeFails() {
        willThrow(new RuntimeException("mock-bank 다운"))
                .given(mockBankClient).revokeUserKey(OLD_KEY);

        // 예외가 던져지지 않아야 함 (assertThatCode 대신 그냥 호출해서 확인)
        listener.handle(new PreviousUserKeyRevokedEvent(USER_ID, OLD_KEY));

        verify(mockBankClient).revokeUserKey(OLD_KEY);
    }
}