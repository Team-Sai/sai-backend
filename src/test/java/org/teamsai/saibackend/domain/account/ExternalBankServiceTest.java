package org.teamsai.saibackend.domain.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.teamsai.saibackend.domain.account.dto.response.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.response.LinkableAccountResponse;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.account.service.ExternalBankService;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.client.MockBankClient;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExternalBankServiceTest {

    @Mock
    private MockBankClient mockBankClient;

    @Mock
    private UserService userService;

    @Mock
    private LinkedBankAccountMapper linkedBankAccountMapper;

    @InjectMocks
    private ExternalBankService externalBankService;

    private static final Long USER_ID = 1L;
    private static final Long ACCOUNT_ID = 1L;
    private static final String USER_TOKEN = "token";
    private static final String USER_KEY = "userKey";

    private LinkableAccountResponse account1;
    private LinkableAccountResponse account2;

    @BeforeEach
    void setUp() {
        account1 = new LinkableAccountResponse(
                1L, "1234567890", "사이 입출금통장", "088",
                BigDecimal.valueOf(100_000), "홍길동"
        );
        account2 = new LinkableAccountResponse(
                2L, "9876543210", "사이 저축통장", "004",
                BigDecimal.valueOf(500_000), "홍길동"
        );
    }

    @Nested
    @DisplayName("fetchAvailableAccountsFromBank(userId)")
    class FetchAvailableAccountsFromBank {

        @Test
        @DisplayName("연동 가능한 계좌 목록을 정상적으로 반환한다")
        void returnsAvailableAccounts() {
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(mockBankClient.getAccountsByUserKey(USER_KEY)).willReturn(List.of(account1, account2));
            given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID)).willReturn(List.of());

            List<LinkableAccountResponse> result =
                    externalBankService.fetchAvailableAccountsFromBank(USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(account1, account2);
            verify(userService).getUserKeyByUserId(USER_ID);
            verify(mockBankClient).getAccountsByUserKey(USER_KEY);
        }

        @Test
        @DisplayName("사이은행에 연동 가능한 계좌가 없으면 빈 목록을 그대로 반환한다")
        void returnsEmptyListWhenNoAccountsAvailable() {
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(mockBankClient.getAccountsByUserKey(USER_KEY)).willReturn(List.of());
            given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID)).willReturn(List.of());

            List<LinkableAccountResponse> result =
                    externalBankService.fetchAvailableAccountsFromBank(USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("이미 연동된 계좌는 목록에서 제외한다")
        void excludesAlreadyLinkedAccounts() {
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(mockBankClient.getAccountsByUserKey(USER_KEY)).willReturn(List.of(account1, account2));

            LinkedBankAccountDTO linked = LinkedBankAccountDTO.builder()
                    .accountId(account1.accountId())
                    .build();
            given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID)).willReturn(List.of(linked));

            List<LinkableAccountResponse> result =
                    externalBankService.fetchAvailableAccountsFromBank(USER_ID);

            assertThat(result).containsExactly(account2);
        }

        @Test
        @DisplayName("사이은행 계좌 목록 조회 실패 시 BANK_SERVER_UNAVAILABLE 예외를 던진다")
        void throwsExceptionWhenBankServerUnavailable() {
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(mockBankClient.getAccountsByUserKey(USER_KEY))
                    .willThrow(new RestClientException("연결 실패"));

            assertThatThrownBy(() ->
                    externalBankService.fetchAvailableAccountsFromBank(USER_ID)
            ).isInstanceOf(RuntimeException.class);

            verify(linkedBankAccountMapper, never()).selectLinkedAccountsByUserId(USER_ID);
        }
    }

    @Nested
    @DisplayName("getAccountDetail(accountId, userId)")
    class GetAccountDetail {

        @Test
        @DisplayName("본인이 연동한 계좌면 상세 정보를 정상적으로 반환한다")
        void returnsAccountDetailWhenOwned() {
            LinkedBankAccountDTO linked = LinkedBankAccountDTO.builder()
                    .accountId(ACCOUNT_ID)
                    .build();
            given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID)).willReturn(List.of(linked));
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);

            AccountDetailResponse detail = new AccountDetailResponse(
                    ACCOUNT_ID, "088", "1111111111", "사이 입출금통장",
                    "홍길동", BigDecimal.valueOf(100_000), "ACTIVE",
                    LocalDateTime.now(), LocalDateTime.now()
            );
            given(mockBankClient.getAccountDetail(ACCOUNT_ID, USER_KEY)).willReturn(detail);

            AccountDetailResponse result = externalBankService.getAccountDetail(ACCOUNT_ID, USER_ID);

            assertThat(result).isEqualTo(detail);
            verify(mockBankClient).getAccountDetail(ACCOUNT_ID, USER_KEY);
        }

        @Test
        @DisplayName("본인이 연동하지 않은 계좌면 ACCOUNT_ACCESS_DENIED 예외를 던지고 조회하지 않는다")
        void throwsExceptionWhenNotOwned() {
            given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID)).willReturn(List.of());

            assertThatThrownBy(() ->
                    externalBankService.getAccountDetail(ACCOUNT_ID, USER_ID)
            )
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.ACCOUNT_ACCESS_DENIED);

            verify(userService, never()).getUserKeyByUserId(USER_ID);
            verify(mockBankClient, never()).getAccountDetail(anyLong(), anyString());
        }

        @Test
        @DisplayName("계좌 상세 조회 실패 시 BANK_SERVER_UNAVAILABLE 예외를 던진다")
        void throwsExceptionWhenBankServerUnavailable() {
            LinkedBankAccountDTO linked = LinkedBankAccountDTO.builder()
                    .accountId(ACCOUNT_ID)
                    .build();
            given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID)).willReturn(List.of(linked));
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(mockBankClient.getAccountDetail(ACCOUNT_ID, USER_KEY))
                    .willThrow(new RestClientException("연결 실패"));

            assertThatThrownBy(() ->
                    externalBankService.getAccountDetail(ACCOUNT_ID, USER_ID)
            )
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.BANK_SERVER_UNAVAILABLE);
        }
    }
}