package org.teamsai.saibackend.domain.account.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.account.dto.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.LinkableAccountResponse;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
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

    @Test
    @DisplayName("연동 가능한 계좌 목록을 정상적으로 반환한다")
    void returnsAvailableAccounts() {
        given(userService.getUserKeyByUserToken(USER_TOKEN)).willReturn(USER_KEY);
        given(mockBankClient.getAccountsByUserKey(USER_KEY)).willReturn(List.of(account1, account2));
        given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID)).willReturn(List.of());

        List<LinkableAccountResponse> result =
                externalBankService.fetchAvailableAccountsFromBank(USER_ID, USER_TOKEN);

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
        given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID)).willReturn(List.of());

        List<LinkableAccountResponse> result =
                externalBankService.fetchAvailableAccountsFromBank(USER_ID, USER_TOKEN);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("이미 연동된 계좌는 목록에서 제외한다")
    void excludesAlreadyLinkedAccounts() {
        given(userService.getUserKeyByUserToken(USER_TOKEN)).willReturn(USER_KEY);
        given(mockBankClient.getAccountsByUserKey(USER_KEY)).willReturn(List.of(account1, account2));

        LinkedBankAccountDTO linked = LinkedBankAccountDTO.builder()
                .accountId(account1.accountId())
                .build();
        given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID)).willReturn(List.of(linked));

        List<LinkableAccountResponse> result =
                externalBankService.fetchAvailableAccountsFromBank(USER_ID, USER_TOKEN);

        assertThat(result).containsExactly(account2);
    }
}
