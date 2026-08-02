package org.teamsai.saibackend.domain.account.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.account.dto.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.LinkableAccountResponse;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.client.MockBankClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalBankService 단위 테스트")
public class ExternalBankServiceTest {

    @Mock
    private MockBankClient mockBankClient;

    @Mock
    private UserService userService;

    @InjectMocks
    private ExternalBankService externalBankService;

    private static final String USER_TOKEN = "user-token-abc";
    private static final String USER_KEY = "mb_rawUserKey1234";
    private static final Long ACCOUNT_ID = 1L;

    @Nested
    @DisplayName("fetchAvailableAccountsFromBank(userToken)")
    class FetchAvailableAccountsFromBank {

        @Test
        @DisplayName("userToken으로 userKey를 조회한 뒤, 그 userKey로 사이은행의 연동 가능 계좌 목록을 가져온다")
        void returnsAccountsFromBank() {
            // given
            LinkableAccountResponse account1 = new LinkableAccountResponse(
                    1L, "1111111111", "테스트계좌1", 10000L, "홍길동", false
            );
            LinkableAccountResponse account2 = new LinkableAccountResponse(
                    2L, "2222222222", "테스트계좌2", 20000L, "홍길동", true
            );

            given(userService.getUserKeyByUserToken(USER_TOKEN)).willReturn(USER_KEY);
            given(mockBankClient.getAccountsByUserKey(USER_KEY))
                    .willReturn(List.of(account1, account2));

            // when
            List<LinkableAccountResponse> result =
                    externalBankService.fetchAvailableAccountsFromBank(USER_TOKEN);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(account1, account2);
            verify(userService).getUserKeyByUserToken(USER_TOKEN);
            verify(mockBankClient).getAccountsByUserKey(USER_KEY);
        }

        @Test
        @DisplayName("사이은행에 연동 가능한 계좌가 없으면 빈 목록을 그대로 반환한다")
        void returnsEmptyListWhenNoAccountsAvailable() {
            given(userService.getUserKeyByUserToken(USER_TOKEN)).willReturn(USER_KEY);
            given(mockBankClient.getAccountsByUserKey(USER_KEY)).willReturn(List.of());

            List<LinkableAccountResponse> result =
                    externalBankService.fetchAvailableAccountsFromBank(USER_TOKEN);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAccountDetail(accountId, userKey)")
    class GetAccountDetail {

        @Test
        @DisplayName("MockBankClient를 통해 조회한 계좌 상세 정보를 그대로 반환한다")
        void returnsAccountDetailFromBank() {
            AccountDetailResponse detail = new AccountDetailResponse(
                    ACCOUNT_ID,
                    10L,
                    "088",
                    "1234567890123",
                    "테스트계좌",
                    "홍길동",
                    BigDecimal.valueOf(50000),
                    "ACTIVE",
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );

            given(mockBankClient.getAccountDetail(ACCOUNT_ID, USER_KEY)).willReturn(detail);

            AccountDetailResponse result =
                    externalBankService.getAccountDetail(ACCOUNT_ID, USER_KEY);

            assertThat(result).isEqualTo(detail);
            verify(mockBankClient).getAccountDetail(ACCOUNT_ID, USER_KEY);
            verifyNoMoreInteractions(mockBankClient);
        }
    }
}
