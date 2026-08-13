package org.teamsai.saibackend.domain.contractchange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.ChangeLoanContractResponse;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.request.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.exception.ContractChangeErrorCode;
import org.teamsai.saibackend.domain.contractchange.mapper.ContractChangeMapper;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;
import org.teamsai.saibackend.domain.contractchange.type.ChangeRequestStatus;
import org.teamsai.saibackend.domain.notification.service.NotificationService;
import org.teamsai.saibackend.domain.user.dto.response.UserResponse;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContractChangeService 단위 테스트")
class ContractChangeServiceTest {

    private static final Long CONTRACT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long DEBTOR_ID = 20L;

    @Mock
    private ContractChangeMapper contractChangeMapper;

    @Mock
    private LoanContractService loanContractService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ContractChangeService contractChangeService;

    @Nested
    @DisplayName("계약 조회")
    class GetContract {

        @Test
        @DisplayName("완료된 계약이면 정상적으로 반환한다")
        void getContractSuccess() {
            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));

            LoanContractResponse result = contractChangeService.getContract(CONTRACT_ID, USER_ID);

            assertThat(result.getContractId()).isEqualTo(CONTRACT_ID);
        }

        @Test
        @DisplayName("완료되지 않은 계약이면 예외가 발생한다")
        void getContractFailsWhenNotCompleted() {
            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willReturn(createContract(ContractStatus.PENDING));

            assertThatThrownBy(() -> contractChangeService.getContract(CONTRACT_ID, USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CONTRACT_NOT_COMPLETED)
                    );
        }

        @Test
        @DisplayName("계약서 도메인에서 던진 예외를 그대로 전달한다")
        void getContractPropagatesExceptionFromLoanContractService() {
            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willThrow(LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException());

            assertThatThrownBy(() -> contractChangeService.getContract(CONTRACT_ID, USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_ACCESS_DENIED)
                    );
        }
    }

    private LoanContractResponse createContract(ContractStatus status) {
        return LoanContractResponse.builder()
                .contractId(CONTRACT_ID)
                .status(status)
                .creditorId(USER_ID)
                .debtorId(DEBTOR_ID)
                .principalAmount(BigDecimal.valueOf(1_000_000))
                .repaymentDay(15)
                .creditorAddress("서울시 강남구")
                .debtorAddress("서울시 서초구")
                .contractAlias("차용증")
                .terms("계약 조건")
                .build();
    }

    private ContractChangeRequest changeRequest() {
        return ContractChangeRequest.builder()
                .changeReason("이자율 조정 요청")
                .newMaturityDate(LocalDate.of(2027, 1, 1))
                .newInterestRate(BigDecimal.valueOf(5.0))
                .newRepaymentType(RepaymentMethod.EQUAL_PRINCIPAL_AND_INTEREST.name())
                .newRepaymentDate(15)
                .build();
    }

    @Nested
    @DisplayName("변경 요청 저장")
    class RequestChange {

        @Test
        @DisplayName("완료된 계약이고 중복 요청이 없으면 변경 요청과 계약서 테이블에 함께 저장한다")
        void requestChangeSuccess() {
            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(contractChangeMapper.findByContractId(CONTRACT_ID))
                    .willReturn(List.of());
            given(userService.getMyInfo(USER_ID))
                    .willReturn(UserResponse.builder().name("채권자").build());

            contractChangeService.requestChange(CONTRACT_ID, changeRequest(), USER_ID);

            verify(contractChangeMapper).insert(any());

            ArgumentCaptor<ChangeLoanContractResponse> captor =
                    ArgumentCaptor.forClass(ChangeLoanContractResponse.class);
            verify(loanContractService).insertChangedContract(captor.capture());

            ChangeLoanContractResponse changedContract = captor.getValue();
            assertThat(changedContract.getPreviousContractId()).isEqualTo(CONTRACT_ID);
            assertThat(changedContract.getStatus()).isEqualTo(ContractStatus.PENDING);
        }

        @Test
        @DisplayName("완료되지 않은 계약이면 예외가 발생한다")
        void requestChangeFailsWhenNotCompleted() {
            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willReturn(createContract(ContractStatus.DRAFT));

            assertThatThrownBy(() -> contractChangeService.requestChange(CONTRACT_ID, changeRequest(), USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CONTRACT_NOT_COMPLETED)
                    );
        }

        @Test
        @DisplayName("이미 PENDING 요청이 있으면 예외가 발생한다")
        void requestChangeFailsWhenDuplicatePending() {
            LoanContractChangeDTO pendingRequest = LoanContractChangeDTO.builder()
                    .status(ChangeRequestStatus.PENDING)
                    .build();

            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(contractChangeMapper.findByContractId(CONTRACT_ID))
                    .willReturn(List.of(pendingRequest));

            assertThatThrownBy(() -> contractChangeService.requestChange(CONTRACT_ID, changeRequest(), USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.DUPLICATE_PENDING_REQUEST)
                    );
        }

        @Test
        @DisplayName("계약 당사자가 아니면 예외가 발생한다")
        void requestChangeFailsWhenNotContractParty() {
            LoanContractResponse contract = LoanContractResponse.builder()
                    .contractId(CONTRACT_ID)
                    .status(ContractStatus.COMPLETED)
                    .creditorId(888L)   // ← userId(10L)와 다른 채권자
                    .debtorId(999L)     // ← userId(10L)와 다른 채무자
                    .build();

            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willReturn(contract);

            assertThatThrownBy(() -> contractChangeService.requestChange(CONTRACT_ID, changeRequest(), USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.NOT_CONTRACT_PARTY)
                    );
        }

        @Test
        @DisplayName("채무자가 요청하면 예외가 발생하고 저장하지 않는다")
        void requestChangeFailsWhenRequesterIsDebtor() {
            LoanContractResponse contract = LoanContractResponse.builder()
                    .contractId(CONTRACT_ID)
                    .status(ContractStatus.COMPLETED)
                    .creditorId(888L)
                    .debtorId(USER_ID)   // ← 요청자가 채무자인 경우
                    .build();

            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willReturn(contract);

            assertThatThrownBy(() -> contractChangeService.requestChange(CONTRACT_ID, changeRequest(), USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.NOT_CREDITOR)
                    );

            verify(contractChangeMapper, never()).insert(any());
            verify(loanContractService, never()).insertChangedContract(any());
        }
    }

    @Nested
    @DisplayName("변경 요청 반려")
    class RejectChange {

        private static final Long CHANGE_REQUEST_ID = 5L;
        private static final Long V2_CONTRACT_ID = 2L;
        private static final String RETURN_REASON = "이율이 너무 높습니다";

        private LoanContractChangeDTO pendingChangeRequest() {
            return LoanContractChangeDTO.builder()
                    .changeRequestId(CHANGE_REQUEST_ID)
                    .contractId(CONTRACT_ID)
                    .status(ChangeRequestStatus.PENDING)
                    .build();
        }

        private LoanContractResponse pendingV2() {
            return LoanContractResponse.builder()
                    .contractId(V2_CONTRACT_ID)
                    .previousContractId(CONTRACT_ID)
                    .status(ContractStatus.PENDING)
                    .build();
        }

        @Test
        @DisplayName("채무자가 PENDING 요청을 반려하면 사유를 저장하고 v2를 CHANGE_REJECTED로 바꾼다")
        void rejectChangeSuccess() {
            given(loanContractService.findContract(CONTRACT_ID, DEBTOR_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(pendingChangeRequest()));
            given(contractChangeMapper.updateStatusWithReturnReason(CHANGE_REQUEST_ID, ChangeRequestStatus.REJECTED, RETURN_REASON))
                    .willReturn(1);
            given(loanContractService.findPendingContractByPreviousId(CONTRACT_ID))
                    .willReturn(Optional.of(pendingV2()));

            contractChangeService.rejectChange(CONTRACT_ID, CHANGE_REQUEST_ID, RETURN_REASON, DEBTOR_ID);

            verify(contractChangeMapper)
                    .updateStatusWithReturnReason(CHANGE_REQUEST_ID, ChangeRequestStatus.REJECTED, RETURN_REASON);
            verify(loanContractService).rejectChangedContract(V2_CONTRACT_ID);
        }

        @Test
        @DisplayName("채권자가 반려를 시도하면 예외가 발생하고 아무것도 저장하지 않는다")
        void rejectChangeFailsWhenRequesterIsCreditor() {
            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(pendingChangeRequest()));

            assertThatThrownBy(() ->
                    contractChangeService.rejectChange(CONTRACT_ID, CHANGE_REQUEST_ID, RETURN_REASON, USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.NOT_DEBTOR)
                    );

            verify(contractChangeMapper, never()).updateStatusWithReturnReason(any(), any(), any());
            verify(loanContractService, never()).rejectChangedContract(any());
        }

        @Test
        @DisplayName("changeRequestId가 다른 계약 소속이면 예외가 발생한다")
        void rejectChangeFailsWhenContractMismatch() {
            LoanContractChangeDTO otherContractRequest = LoanContractChangeDTO.builder()
                    .changeRequestId(CHANGE_REQUEST_ID)
                    .contractId(999L)
                    .status(ChangeRequestStatus.PENDING)
                    .build();

            given(loanContractService.findContract(CONTRACT_ID, DEBTOR_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(otherContractRequest));

            assertThatThrownBy(() ->
                    contractChangeService.rejectChange(CONTRACT_ID, CHANGE_REQUEST_ID, RETURN_REASON, DEBTOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND)
                    );
        }

        @Test
        @DisplayName("이미 처리된(APPROVED) 요청을 반려하려 하면 예외가 발생한다")
        void rejectChangeFailsWhenAlreadyProcessed() {
            LoanContractChangeDTO approvedRequest = LoanContractChangeDTO.builder()
                    .changeRequestId(CHANGE_REQUEST_ID)
                    .contractId(CONTRACT_ID)
                    .status(ChangeRequestStatus.APPROVED)
                    .build();

            given(loanContractService.findContract(CONTRACT_ID, DEBTOR_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(approvedRequest));

            assertThatThrownBy(() ->
                    contractChangeService.rejectChange(CONTRACT_ID, CHANGE_REQUEST_ID, RETURN_REASON, DEBTOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.ALREADY_BEING_REQUEST)
                    );

            verify(contractChangeMapper, never()).updateStatusWithReturnReason(any(), any(), any());
        }

        @Test
        @DisplayName("존재하지 않는 변경 요청이면 예외가 발생한다")
        void rejectChangeFailsWhenChangeRequestNotFound() {
            given(loanContractService.findContract(CONTRACT_ID, DEBTOR_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    contractChangeService.rejectChange(CONTRACT_ID, CHANGE_REQUEST_ID, RETURN_REASON, DEBTOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND)
                    );
        }

        @Test
        @DisplayName("승인 처리와 경쟁 상태로 이미 상태가 바뀌었으면(영향받은 행 0개) 예외가 발생한다")
        void rejectChangeFailsWhenRaceConditionLeavesZeroRowsUpdated() {
            given(loanContractService.findContract(CONTRACT_ID, DEBTOR_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(pendingChangeRequest()));
            given(contractChangeMapper.updateStatusWithReturnReason(CHANGE_REQUEST_ID, ChangeRequestStatus.REJECTED, RETURN_REASON))
                    .willReturn(0);

            assertThatThrownBy(() ->
                    contractChangeService.rejectChange(CONTRACT_ID, CHANGE_REQUEST_ID, RETURN_REASON, DEBTOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.ALREADY_BEING_REQUEST)
                    );

            verify(loanContractService, never()).rejectChangedContract(any());
        }
    }
}