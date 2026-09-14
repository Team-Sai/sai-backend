package org.teamsai.saibackend.global.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserKeyRevoker 단위 테스트")
class UserKeyRevokerTest {

    @Mock
    private MockBankClient mockBankClient;
    @InjectMocks
    private UserKeyRevoker userKeyRevoker;

    private static final String CALLER = "TestCaller";
    private static final Long USER_ID = 1L;
    private static final String USER_KEY = "mb_somekey12345678";

    @Test
    @DisplayName("revoke에 성공하면 mock-bank에 revokeUserKey를 요청한다")
    void revokesUserKeyOnSuccess() {
        userKeyRevoker.revokeBestEffort(CALLER, USER_ID, USER_KEY);

        verify(mockBankClient).revokeUserKey(USER_KEY);
    }

    @Test
    @DisplayName("revoke 요청이 실패해도 예외를 전파하지 않고 삼킨다")
    void doesNotPropagateExceptionWhenRevokeFails() {
        willThrow(new RuntimeException("mock-bank 다운"))
                .given(mockBankClient).revokeUserKey(USER_KEY);

        assertThatCode(() -> userKeyRevoker.revokeBestEffort(CALLER, USER_ID, USER_KEY))
                .doesNotThrowAnyException();

        verify(mockBankClient).revokeUserKey(USER_KEY);
    }
}
