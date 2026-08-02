package org.teamsai.saibackend.domain.account.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.account.dto.ConnectionStatus;
import org.teamsai.saibackend.domain.account.dto.LinkAccountRequest;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.global.client.MockBankClient;

import java.time.LocalDateTime;
import java.util.List;

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

    @InjectMocks
    private LinkedBankAccountService linkedBankAccountService;

    private static final Long USER_ID = 1L;

    @Nested
    @DisplayName("linkSelectedAccounts(userId, request)")
    class LinkSelectedAccounts {

        @Test
        @DisplayName("선택된 계좌들을 DEFAULT_BANK_CODE로 일괄 저장하고 응답으로 매핑해 반환한다")
        void savesSelectedAccountsAndReturnsResponses() {
            // given
            LinkAccountRequest.SelectedAccount selected1 = new LinkAccountRequest.SelectedAccount(
                    1L, "088", "1111111111", "계좌1", "홍길동", "생활비통장", 10000L
            );
            LinkAccountRequest.SelectedAccount selected2 = new LinkAccountRequest.SelectedAccount(
                    2L, "004", "2222222222", "계좌2", "홍길동", "비상금통장", 20000L
            );
            LinkAccountRequest request = new LinkAccountRequest(List.of(selected1, selected2));

            // when
            List<LinkedBankAccountResponse> result =
                    linkedBankAccountService.linkSelectedAccounts(USER_ID, request);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).bankCode()).isEqualTo("SAI_001"); // DEFAULT_BANK_CODE
            assertThat(result.get(0).maskedAccountNumber()).isEqualTo("1111111111");
            assertThat(result.get(0).accountAlias()).isEqualTo("생활비통장");
            assertThat(result.get(0).accountHolderName()).isEqualTo("홍길동");
            assertThat(result.get(0).connectionStatus()).isEqualTo("AVAILABLE");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LinkedBankAccountDTO>> captor = ArgumentCaptor.forClass(List.class);
            verify(linkedBankAccountMapper).insertBatch(captor.capture());

            List<LinkedBankAccountDTO> savedList = captor.getValue();
            assertThat(savedList).hasSize(2);
            assertThat(savedList)
                    .allMatch(dto -> dto.getUserId().equals(USER_ID))
                    .allMatch(dto -> dto.getBankCode().equals("SAI_001"))
                    .allMatch(dto -> dto.getConnectionStatus() == ConnectionStatus.AVAILABLE)
                    .allMatch(dto -> dto.getCreatedAt() != null)
                    .allMatch(dto -> dto.getUpdatedAt() != null);
        }

        @Test
        @DisplayName("선택된 계좌가 없으면 빈 목록으로 저장을 시도하고 빈 응답을 반환한다")
        void returnsEmptyListWhenNoAccountsSelected() {
            LinkAccountRequest request = new LinkAccountRequest(List.of());

            List<LinkedBankAccountResponse> result =
                    linkedBankAccountService.linkSelectedAccounts(USER_ID, request);

            assertThat(result).isEmpty();
            verify(linkedBankAccountMapper).insertBatch(List.of());
        }

        @Test
        @DisplayName("MockBankClient는 사용하지 않는다")
        void doesNotInteractWithMockBankClient() {
            LinkAccountRequest.SelectedAccount selected = new LinkAccountRequest.SelectedAccount(
                    1L, "088", "1111111111", "계좌1", "홍길동", "생활비통장", 10000L
            );
            LinkAccountRequest request = new LinkAccountRequest(List.of(selected));

            linkedBankAccountService.linkSelectedAccounts(USER_ID, request);

            verifyNoInteractions(mockBankClient);
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
                    .bankCode("SAI_001")
                    .accountNumber("1111111111")
                    .accountAlias("생활비통장")
                    .accountHolderName("홍길동")
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
            verify(linkedBankAccountMapper, never()).selectLinkedAccountsByUserId(anyLong());
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
