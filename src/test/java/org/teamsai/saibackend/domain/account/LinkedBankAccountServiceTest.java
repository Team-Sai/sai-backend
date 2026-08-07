package org.teamsai.saibackend.domain.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.teamsai.saibackend.domain.account.dto.*;
import org.teamsai.saibackend.domain.account.dto.request.LinkAccountRequest;
import org.teamsai.saibackend.domain.account.dto.response.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.dto.type.ConnectionStatus;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.client.MockBankClient;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LinkedBankAccountService 단위 테스트")
public class LinkedBankAccountServiceTest {
    @Mock
    private LinkedBankAccountMapper linkedBankAccountMapper;

    @Mock
    private MockBankClient mockBankClient;

    @Mock
    private UserService userService;

    @InjectMocks
    private LinkedBankAccountService linkedBankAccountService;

    private static final Long USER_ID = 1L;
    private static final String USER_KEY = "userKey";

    private AccountDetailResponse createDetail(Long accountId, String bankCode, String accountName) {
        return new AccountDetailResponse(
                accountId,
                bankCode,
                "1111111111",
                accountName,
                "홍길동",
                BigDecimal.valueOf(100_000),
                "ACTIVE",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
    @Nested
    @DisplayName("linkSelectedAccounts(userId, request)")
    class LinkSelectedAccounts {

        @Test
        @DisplayName("선택된 계좌들의 상세 정보를 조회해 저장하고 응답으로 매핑해 반환한다")
        void savesSelectedAccountsAndReturnsResponses() {
            LinkAccountRequest.SelectedAccount selected1 =
                    new LinkAccountRequest.SelectedAccount(1L, "생활비통장");
            LinkAccountRequest.SelectedAccount selected2 =
                    new LinkAccountRequest.SelectedAccount(2L, "비상금통장");
            LinkAccountRequest request = new LinkAccountRequest(List.of(selected1, selected2));

            AccountDetailResponse detail1 = createDetail(1L, "088", "사이 입출금통장");
            AccountDetailResponse detail2 = createDetail(2L, "004", "사이 저축통장");

            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(mockBankClient.getAccountDetail(1L, USER_KEY)).willReturn(detail1);
            given(mockBankClient.getAccountDetail(2L, USER_KEY)).willReturn(detail2);

            List<LinkedBankAccountResponse> result =
                    linkedBankAccountService.linkSelectedAccounts(USER_ID, request);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).bankCode()).isEqualTo("088");
            assertThat(result.get(0).accountAlias()).isEqualTo("생활비통장");
            assertThat(result.get(1).bankCode()).isEqualTo("004");
            assertThat(result.get(1).accountAlias()).isEqualTo("비상금통장");

            ArgumentCaptor<LinkedBankAccountDTO> captor = ArgumentCaptor.forClass(LinkedBankAccountDTO.class);
            verify(linkedBankAccountMapper, times(2)).insertOne(captor.capture());

            List<LinkedBankAccountDTO> savedList = captor.getAllValues();
            assertThat(savedList)
                    .allMatch(dto -> dto.getUserId().equals(USER_ID))
                    .allMatch(dto -> dto.getConnectionStatus() == ConnectionStatus.AVAILABLE)
                    .allMatch(dto -> dto.getCreatedAt() != null)
                    .allMatch(dto -> dto.getUpdatedAt() != null);

            verify(userService).getUserKeyByUserId(USER_ID);
        }

        @Test
        @DisplayName("선택된 계좌가 없으면 계좌 상세 조회 없이 빈 목록을 반환하고 저장하지 않는다")
        void returnsEmptyListWhenNoAccountsSelected() {
            LinkAccountRequest request = new LinkAccountRequest(List.of());

            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);

            List<LinkedBankAccountResponse> result =
                    linkedBankAccountService.linkSelectedAccounts(USER_ID, request);

            assertThat(result).isEmpty();
            verify(mockBankClient, never()).getAccountDetail(any(), any());
            verify(linkedBankAccountMapper, never()).insertOne(any());
        }

        @Test
        @DisplayName("계좌 상세 조회 실패 시 BANK_SERVER_UNAVAILABLE 예외를 던지고 저장하지 않는다")
        void throwsExceptionWhenBankServerUnavailable() {
            LinkAccountRequest.SelectedAccount selected =
                    new LinkAccountRequest.SelectedAccount(1L, "생활비통장");
            LinkAccountRequest request = new LinkAccountRequest(List.of(selected));

            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(mockBankClient.getAccountDetail(1L, USER_KEY))
                    .willThrow(new RestClientException("연결 실패"));

            assertThatThrownBy(() ->
                    linkedBankAccountService.linkSelectedAccounts(USER_ID, request)
            )
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.BANK_SERVER_UNAVAILABLE);

            verify(linkedBankAccountMapper, never()).insertOne(any());
        }
    }

    @Nested
    @DisplayName("linkAccountsByIds(userId, userKey, accountIds)")
    class LinkAccountsByIds {

        @Test
        @DisplayName("전달받은 accountId 목록의 계좌 상세를 조회해 저장하고, 별칭은 사이은행 계좌명을 그대로 사용한다")
        void savesAccountsUsingBankAccountNameAsAlias() {
            AccountDetailResponse detail1 = createDetail(10L, "088", "주거래 입출금통장");
            AccountDetailResponse detail2 = createDetail(20L, "020", "비상금통장");

            given(mockBankClient.getAccountDetail(10L, USER_KEY)).willReturn(detail1);
            given(mockBankClient.getAccountDetail(20L, USER_KEY)).willReturn(detail2);

            List<LinkedBankAccountResponse> result =
                    linkedBankAccountService.linkAccountsByIds(USER_ID, USER_KEY, List.of(10L, 20L));

            assertThat(result).hasSize(2);
            assertThat(result.get(0).accountAlias()).isEqualTo("주거래 입출금통장");
            assertThat(result.get(1).accountAlias()).isEqualTo("비상금통장");

            ArgumentCaptor<LinkedBankAccountDTO> captor = ArgumentCaptor.forClass(LinkedBankAccountDTO.class);
            verify(linkedBankAccountMapper, times(2)).insertOne(captor.capture());

            List<LinkedBankAccountDTO> savedList = captor.getAllValues();
            assertThat(savedList)
                    .extracting(LinkedBankAccountDTO::getAccountId)
                    .containsExactly(10L, 20L);
            assertThat(savedList)
                    .allMatch(dto -> dto.getUserId().equals(USER_ID))
                    .allMatch(dto -> dto.getConnectionStatus() == ConnectionStatus.AVAILABLE);

            // userService는 이 메서드에서 쓰이지 않는다 (userKey를 이미 파라미터로 받으므로)
            verify(userService, never()).getUserKeyByUserId(any());
        }

        @Test
        @DisplayName("계좌 ID 목록이 비어있으면 조회 없이 빈 목록을 반환한다")
        void returnsEmptyListWhenNoAccountIds() {
            List<LinkedBankAccountResponse> result =
                    linkedBankAccountService.linkAccountsByIds(USER_ID, USER_KEY, List.of());

            assertThat(result).isEmpty();
            verify(mockBankClient, never()).getAccountDetail(any(), any());
            verify(linkedBankAccountMapper, never()).insertOne(any());
        }

        @Test
        @DisplayName("계좌 상세 조회 실패 시 BANK_SERVER_UNAVAILABLE 예외를 던지고 저장하지 않는다")
        void throwsExceptionWhenBankServerUnavailable() {
            given(mockBankClient.getAccountDetail(10L, USER_KEY))
                    .willThrow(new RestClientException("연결 실패"));

            assertThatThrownBy(() ->
                    linkedBankAccountService.linkAccountsByIds(USER_ID, USER_KEY, List.of(10L))
            )
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.BANK_SERVER_UNAVAILABLE);

            verify(linkedBankAccountMapper, never()).insertOne(any());
        }

        @Test
        @DisplayName("일부 계좌 조회가 실패하면, 그 이전에 처리된 계좌도 저장되지 않는다 (전체 실패)")
        void doesNotSavePartiallyWhenOneAccountFails() {
            AccountDetailResponse detail1 = createDetail(10L, "088", "주거래 입출금통장");

            given(mockBankClient.getAccountDetail(10L, USER_KEY)).willReturn(detail1);
            given(mockBankClient.getAccountDetail(20L, USER_KEY))
                    .willThrow(new RestClientException("연결 실패"));

            assertThatThrownBy(() ->
                    linkedBankAccountService.linkAccountsByIds(USER_ID, USER_KEY, List.of(10L, 20L))
            ).isInstanceOf(DomainException.class);

            // stream 매핑 도중 예외가 나면 toList()까지 도달하지 못하므로
            // insertOne 자체가 한 번도 호출되지 않아야 한다 (부분 저장 없음)
            verify(linkedBankAccountMapper, never()).insertOne(any());
        }
    }

    @Nested
    @DisplayName("getLinkedAccounts(userId)")
    class GetLinkedAccounts {

        @Test
        @DisplayName("연동된 계좌 목록을 응답으로 매핑해 반환한다")
        void returnsLinkedAccounts() {
            LinkedBankAccountDTO linked = LinkedBankAccountDTO.builder()
                    .linkedAccountId(100L)
                    .userId(USER_ID)
                    .accountId(10L)
                    .bankCode("088")
                    .accountNumber("1111111111")
                    .accountAlias("생활비통장")
                    .accountHolderName("홍길동")
                    .balance(BigDecimal.valueOf(100_000))
                    .connectionStatus(ConnectionStatus.AVAILABLE)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID))
                    .willReturn(List.of(linked));

            List<LinkedBankAccountResponse> result =
                    linkedBankAccountService.getLinkedAccounts(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).linkedAccountId()).isEqualTo(100L);
            assertThat(result.get(0).connectionStatus()).isEqualTo("AVAILABLE");
            verify(linkedBankAccountMapper).selectLinkedAccountsByUserId(USER_ID);
        }

        @Test
        @DisplayName("userId가 null이면 조회 없이 빈 목록을 반환한다")
        void returnsEmptyListWhenUserIdIsNull() {
            List<LinkedBankAccountResponse> result =
                    linkedBankAccountService.getLinkedAccounts(null);

            assertThat(result).isEmpty();
            verify(linkedBankAccountMapper, never()).selectLinkedAccountsByUserId(any());
        }

        @Test
        @DisplayName("매퍼가 null을 반환하면 빈 목록을 반환한다")
        void returnsEmptyListWhenMapperReturnsNull() {
            given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID)).willReturn(null);

            List<LinkedBankAccountResponse> result =
                    linkedBankAccountService.getLinkedAccounts(USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("연동된 계좌가 없으면 빈 목록을 반환한다")
        void returnsEmptyListWhenNoLinkedAccounts() {
            given(linkedBankAccountMapper.selectLinkedAccountsByUserId(USER_ID))
                    .willReturn(List.of());

            List<LinkedBankAccountResponse> result =
                    linkedBankAccountService.getLinkedAccounts(USER_ID);

            assertThat(result).isEmpty();
        }
    }
}