package org.teamsai.saibackend.domain.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.dto.type.ConnectionStatus;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.contract.dto.ContractAccountDTO;
import org.teamsai.saibackend.domain.contract.dto.ContractAccountStatus;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
import org.teamsai.saibackend.domain.contract.mapper.ContractAccountMapper;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractMapper;
import org.teamsai.saibackend.domain.contract.service.ContractAccountService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContractAccountService 단위 테스트")
class ContractAccountServiceTest {

    private static final Long CONTRACT_ID = 1L;
    private static final Long CREDITOR_ID = 1L;
    private static final Long OTHER_USER_ID = 999L;
    private static final Long LINKED_ACCOUNT_ID = 10L;
    private static final Long OTHER_LINKED_ACCOUNT_ID = 20L;

    @Mock
    private ContractAccountMapper contractAccountMapper;

    @Mock
    private LoanContractMapper loanContractMapper;

    @Mock
    private LinkedBankAccountService linkedBankAccountService;

    @InjectMocks
    private ContractAccountService contractAccountService;

    @Nested
    @DisplayName("선택 가능한 계좌 조회")
    class GetSelectableAccounts {

        @Test
        @DisplayName("연동 계좌 중 AVAILABLE 상태인 계좌만 반환한다")
        void getSelectableAccountsFiltersAvailableOnly() {
            given(linkedBankAccountService.getLinkedAccounts(CREDITOR_ID)).willReturn(List.of(
                    createLinkedAccount(LINKED_ACCOUNT_ID, ConnectionStatus.AVAILABLE),
                    createLinkedAccount(OTHER_LINKED_ACCOUNT_ID, ConnectionStatus.UNAVAILABLE)
            ));

            List<LinkedBankAccountResponse> result = contractAccountService.getSelectableAccounts(CREDITOR_ID);

            assertThat(result).extracting(LinkedBankAccountResponse::linkedAccountId)
                    .containsExactly(LINKED_ACCOUNT_ID);
        }

        @Test
        @DisplayName("연동 계좌가 없으면 빈 목록을 반환한다")
        void getSelectableAccountsReturnsEmptyWhenNoLinkedAccounts() {
            given(linkedBankAccountService.getLinkedAccounts(CREDITOR_ID)).willReturn(List.of());

            List<LinkedBankAccountResponse> result = contractAccountService.getSelectableAccounts(CREDITOR_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("계약 계좌 최초 설정")
    class SetupContractAccount {

        @Test
        @DisplayName("연동 계좌를 선택하면 활성 계좌로 등록한다")
        void setupContractAccountSuccess() {
            given(linkedBankAccountService.getLinkedAccounts(CREDITOR_ID)).willReturn(List.of(
                    createLinkedAccount(LINKED_ACCOUNT_ID, ConnectionStatus.AVAILABLE)
            ));

            contractAccountService.createContractAccount(CONTRACT_ID, CREDITOR_ID, LINKED_ACCOUNT_ID);

            ArgumentCaptor<ContractAccountDTO> captor = ArgumentCaptor.forClass(ContractAccountDTO.class);
            verify(contractAccountMapper).insertContractAccount(captor.capture());
            ContractAccountDTO saved = captor.getValue();
            assertThat(saved.getContractId()).isEqualTo(CONTRACT_ID);
            assertThat(saved.getLinkedAccountId()).isEqualTo(LINKED_ACCOUNT_ID);
            assertThat(saved.getAccountStatus()).isEqualTo(ContractAccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("연동 계좌 ID가 null이면 아무 것도 하지 않는다")
        void setupContractAccountDoesNothingWhenLinkedAccountIdIsNull() {
            contractAccountService.createContractAccount(CONTRACT_ID, CREDITOR_ID, null);

            verify(linkedBankAccountService, never()).getLinkedAccounts(any());
            verify(contractAccountMapper, never()).insertContractAccount(any());
        }

        @Test
        @DisplayName("선택 불가능한 계좌면 예외가 발생하고 등록하지 않는다")
        void setupContractAccountFailsWhenAccountNotSelectable() {
            given(linkedBankAccountService.getLinkedAccounts(CREDITOR_ID)).willReturn(List.of(
                    createLinkedAccount(LINKED_ACCOUNT_ID, ConnectionStatus.UNAVAILABLE)
            ));

            assertThatThrownBy(() ->
                    contractAccountService.createContractAccount(CONTRACT_ID, CREDITOR_ID, LINKED_ACCOUNT_ID)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.INVALID_LINKED_ACCOUNT)
                    );

            verify(contractAccountMapper, never()).insertContractAccount(any());
        }
    }

    @Nested
    @DisplayName("계약 계좌 변경")
    class ChangeContractAccount {

        @Test
        @DisplayName("기존 계좌를 REPLACED 처리하고 새 계좌를 활성 계좌로 등록한다")
        void changeContractAccountSuccess() {
            given(loanContractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract(ContractStatus.PENDING)));
            given(linkedBankAccountService.getLinkedAccounts(CREDITOR_ID)).willReturn(List.of(
                    createLinkedAccount(OTHER_LINKED_ACCOUNT_ID, ConnectionStatus.AVAILABLE)
            ));
            given(contractAccountMapper.updateContractAccountStatus(CONTRACT_ID, ContractAccountStatus.REPLACED))
                    .willReturn(1);

            contractAccountService.changeContractAccount(CONTRACT_ID, CREDITOR_ID, OTHER_LINKED_ACCOUNT_ID);

            verify(contractAccountMapper).updateContractAccountStatus(CONTRACT_ID, ContractAccountStatus.REPLACED);

            ArgumentCaptor<ContractAccountDTO> captor = ArgumentCaptor.forClass(ContractAccountDTO.class);
            verify(contractAccountMapper).insertContractAccount(captor.capture());
            assertThat(captor.getValue().getLinkedAccountId()).isEqualTo(OTHER_LINKED_ACCOUNT_ID);
            assertThat(captor.getValue().getAccountStatus()).isEqualTo(ContractAccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("계약서를 찾을 수 없으면 예외가 발생하고 변경하지 않는다")
        void changeContractAccountFailsWhenContractNotFound() {
            given(loanContractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    contractAccountService.changeContractAccount(CONTRACT_ID, CREDITOR_ID, OTHER_LINKED_ACCOUNT_ID)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_NOT_FOUND)
                    );

            verify(contractAccountMapper, never()).updateContractAccountStatus(any(), any());
            verify(contractAccountMapper, never()).insertContractAccount(any());
        }

        @Test
        @DisplayName("계약 당사자가 아니면 예외가 발생하고 변경하지 않는다")
        void changeContractAccountFailsWhenUserIsNotOwner() {
            given(loanContractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract(ContractStatus.PENDING)));

            assertThatThrownBy(() ->
                    contractAccountService.changeContractAccount(CONTRACT_ID, OTHER_USER_ID, OTHER_LINKED_ACCOUNT_ID)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_ACCESS_DENIED)
                    );

            verify(contractAccountMapper, never()).updateContractAccountStatus(any(), any());
            verify(contractAccountMapper, never()).insertContractAccount(any());
        }

        @Test
        @DisplayName("계약이 이미 완료 상태면 예외가 발생하고 변경하지 않는다")
        void changeContractAccountFailsWhenContractCompleted() {
            given(loanContractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract(ContractStatus.COMPLETED)));

            assertThatThrownBy(() ->
                    contractAccountService.changeContractAccount(CONTRACT_ID, CREDITOR_ID, OTHER_LINKED_ACCOUNT_ID)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_ACCESS_DENIED)
                    );

            verify(contractAccountMapper, never()).updateContractAccountStatus(any(), any());
            verify(contractAccountMapper, never()).insertContractAccount(any());
        }

        @Test
        @DisplayName("새로 선택한 계좌가 선택 불가능하면 예외가 발생하고 변경하지 않는다")
        void changeContractAccountFailsWhenNewAccountNotSelectable() {
            given(loanContractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract(ContractStatus.PENDING)));
            given(linkedBankAccountService.getLinkedAccounts(CREDITOR_ID)).willReturn(List.of(
                    createLinkedAccount(OTHER_LINKED_ACCOUNT_ID, ConnectionStatus.UNAVAILABLE)
            ));

            assertThatThrownBy(() ->
                    contractAccountService.changeContractAccount(CONTRACT_ID, CREDITOR_ID, OTHER_LINKED_ACCOUNT_ID)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.INVALID_LINKED_ACCOUNT)
                    );

            verify(contractAccountMapper, never()).updateContractAccountStatus(any(), any());
            verify(contractAccountMapper, never()).insertContractAccount(any());
        }

        @Test
        @DisplayName("교체 대상 활성 계좌가 없으면 예외가 발생하고 새 계좌를 등록하지 않는다")
        void changeContractAccountFailsWhenNoActiveAccountToReplace() {
            given(loanContractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract(ContractStatus.PENDING)));
            given(linkedBankAccountService.getLinkedAccounts(CREDITOR_ID)).willReturn(List.of(
                    createLinkedAccount(OTHER_LINKED_ACCOUNT_ID, ConnectionStatus.AVAILABLE)
            ));
            given(contractAccountMapper.updateContractAccountStatus(CONTRACT_ID, ContractAccountStatus.REPLACED))
                    .willReturn(0);

            assertThatThrownBy(() ->
                    contractAccountService.changeContractAccount(CONTRACT_ID, CREDITOR_ID, OTHER_LINKED_ACCOUNT_ID)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_ACCOUNT_NOT_FOUND)
                    );

            verify(contractAccountMapper, never()).insertContractAccount(any());
        }
    }

    @Nested
    @DisplayName("계약 계좌 비활성화")
    class deactivateContractAccount {

        @Test
        @DisplayName("계약 소유자가 요청하면 계좌 상태를 DISABLED로 변경한다")
        void deactivateContractAccountSuccess() {
            given(loanContractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract(ContractStatus.PENDING)));
            given(contractAccountMapper.updateContractAccountStatus(CONTRACT_ID, ContractAccountStatus.DISABLED))
                    .willReturn(1);

            contractAccountService.deactivateContractAccount(CONTRACT_ID, CREDITOR_ID);

            verify(contractAccountMapper).updateContractAccountStatus(CONTRACT_ID, ContractAccountStatus.DISABLED);
        }

        @Test
        @DisplayName("계약서를 찾을 수 없으면 예외가 발생하고 비활성화하지 않는다")
        void deactivateContractAccountFailsWhenContractNotFound() {
            given(loanContractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    contractAccountService.deactivateContractAccount(CONTRACT_ID, CREDITOR_ID)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_NOT_FOUND)
                    );

            verify(contractAccountMapper, never()).updateContractAccountStatus(any(), any());
        }

        @Test
        @DisplayName("계약 당사자가 아니면 예외가 발생하고 비활성화하지 않는다")
        void deactivateContractAccountFailsWhenUserIsNotOwner() {
            given(loanContractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract(ContractStatus.PENDING)));

            assertThatThrownBy(() ->
                    contractAccountService.deactivateContractAccount(CONTRACT_ID, OTHER_USER_ID)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_ACCESS_DENIED)
                    );

            verify(contractAccountMapper, never()).updateContractAccountStatus(any(), any());
        }

        @Test
        @DisplayName("계약이 이미 완료 상태면 예외가 발생하고 비활성화하지 않는다")
        void deactivateContractAccountFailsWhenContractCompleted() {
            given(loanContractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract(ContractStatus.COMPLETED)));

            assertThatThrownBy(() ->
                    contractAccountService.deactivateContractAccount(CONTRACT_ID, CREDITOR_ID)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_ACCESS_DENIED)
                    );

            verify(contractAccountMapper, never()).updateContractAccountStatus(any(), any());
        }

        @Test
        @DisplayName("활성화된 계좌가 없으면 예외가 발생한다")
        void deactivateContractAccountFailsWhenNoActiveAccount() {
            given(loanContractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract(ContractStatus.PENDING)));
            given(contractAccountMapper.updateContractAccountStatus(CONTRACT_ID, ContractAccountStatus.DISABLED))
                    .willReturn(0);

            assertThatThrownBy(() ->
                    contractAccountService.deactivateContractAccount(CONTRACT_ID, CREDITOR_ID)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_ACCOUNT_NOT_FOUND)
                    );
        }
    }

    private LinkedBankAccountResponse createLinkedAccount(Long linkedAccountId, ConnectionStatus status) {
        return LinkedBankAccountResponse.builder()
                .linkedAccountId(linkedAccountId)
                .bankCode("088")
                .bankName("신한은행")
                .maskedAccountNumber("110-***-******")
                .accountAlias("생활비 통장")
                .accountHolderName("김채권")
                .connectionStatus(status.name())
                .build();
    }

    private LoanContractResponse createContract(ContractStatus status) {
        return LoanContractResponse.builder()
                .contractId(CONTRACT_ID)
                .creditorId(CREDITOR_ID)
                .status(status)
                .build();
    }
}
