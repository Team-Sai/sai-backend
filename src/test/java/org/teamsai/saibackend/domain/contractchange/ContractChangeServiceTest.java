package org.teamsai.saibackend.domain.contractchange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.ChangeLoanContractResponse;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
import org.teamsai.saibackend.domain.contract.service.LoanContractFileService;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.request.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.exception.ContractChangeErrorCode;
import org.teamsai.saibackend.domain.contractchange.mapper.ContractChangeMapper;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;
import org.teamsai.saibackend.domain.contractchange.type.ChangeRequestStatus;
import org.teamsai.saibackend.domain.notification.service.NotificationService;
import org.teamsai.saibackend.domain.notification.type.NotificationType;
import org.teamsai.saibackend.domain.user.dto.response.UserResponse;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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

    @Mock
    private LoanContractFileService fileService;

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
        @DisplayName("채무자가 요청해도 정상적으로 저장된다")
        void requestChangeSucceedsWhenRequesterIsDebtor() {
            LoanContractResponse contract = LoanContractResponse.builder()
                    .contractId(CONTRACT_ID)
                    .status(ContractStatus.COMPLETED)
                    .creditorId(888L)
                    .debtorId(USER_ID)
                    .build();

            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willReturn(contract);
            given(contractChangeMapper.findByContractId(CONTRACT_ID))
                    .willReturn(List.of());

            contractChangeService.requestChange(CONTRACT_ID, changeRequest(), USER_ID);

            verify(contractChangeMapper).insert(any());
            verify(loanContractService).insertChangedContract(any());
        }
    }

    @Nested
    @DisplayName("변경 요청 반려")
    class RejectChange {

        private static final Long CHANGE_REQUEST_ID = 5L;
        private static final Long V2_CONTRACT_ID = 2L;
        private static final String RETURN_REASON = "이율이 너무 높습니다";

        private LoanContractChangeDTO pendingChangeRequest(Long requesterId) {
            return LoanContractChangeDTO.builder()
                    .changeRequestId(CHANGE_REQUEST_ID)
                    .contractId(CONTRACT_ID)
                    .userId(requesterId)
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
        @DisplayName("채무자가 요청한 건을 채권자가 반려할 수 있다")
        void rejectChangeSuccessWhenRequesterIsDebtor() {
            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(pendingChangeRequest(DEBTOR_ID)));
            given(contractChangeMapper.updateStatusWithReturnReason(CHANGE_REQUEST_ID, ChangeRequestStatus.REJECTED, RETURN_REASON))
                    .willReturn(1);
            given(loanContractService.findPendingContractByPreviousId(CONTRACT_ID))
                    .willReturn(Optional.of(pendingV2()));
            given(userService.getMyInfo(USER_ID))
                    .willReturn(UserResponse.builder().name("채권자").build());

            contractChangeService.rejectChange(CONTRACT_ID, CHANGE_REQUEST_ID, RETURN_REASON, USER_ID);

            verify(contractChangeMapper)
                    .updateStatusWithReturnReason(CHANGE_REQUEST_ID, ChangeRequestStatus.REJECTED, RETURN_REASON);
        }

        @Test
        @DisplayName("채권자가 요청한 건을 채무자가 반려하면 사유를 저장하고 v2를 REJECTED로 바꾼다")
        void rejectChangeSuccess() {
            given(loanContractService.findContract(CONTRACT_ID, DEBTOR_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(pendingChangeRequest(USER_ID)));
            given(contractChangeMapper.updateStatusWithReturnReason(CHANGE_REQUEST_ID, ChangeRequestStatus.REJECTED, RETURN_REASON))
                    .willReturn(1);
            given(loanContractService.findPendingContractByPreviousId(CONTRACT_ID))
                    .willReturn(Optional.of(pendingV2()));
            given(userService.getMyInfo(DEBTOR_ID))
                    .willReturn(UserResponse.builder().name("채무자").build());

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
                    .willReturn(Optional.of(pendingChangeRequest(USER_ID)));

            assertThatThrownBy(() ->
                    contractChangeService.rejectChange(CONTRACT_ID, CHANGE_REQUEST_ID, RETURN_REASON, USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.NOT_CONTRACT_PARTY)
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
                    .userId(USER_ID)
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
                    .userId(USER_ID)
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
                    .willReturn(Optional.of(pendingChangeRequest(USER_ID)));
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

    @Nested
    @DisplayName("변경 요청 전자서명 제출")
    class SubmitRequesterSignature {

        private static final Long CHANGE_REQUEST_ID = 200L;
        private static final String SAVED_PATH = "uploads/signatures/change_200_signature.png";

        private LoanContractChangeDTO changeRequestDTO(ChangeRequestStatus status, Long ownerUserId, Long contractId) {
            return LoanContractChangeDTO.builder()
                    .changeRequestId(CHANGE_REQUEST_ID)
                    .userId(ownerUserId)
                    .contractId(contractId)
                    .changeReason("이자율 조정 요청")
                    .status(status)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("채무자가 요청자면 서명 후 채권자에게 알림이 간다")
        void submitRequesterSignatureSuccessWhenRequesterIsDebtor() {
            MultipartFile signature = mock(MultipartFile.class);

            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(changeRequestDTO(ChangeRequestStatus.PENDING, DEBTOR_ID, CONTRACT_ID)));
            given(fileService.saveSignatureFile(CHANGE_REQUEST_ID, signature))
                    .willReturn(SAVED_PATH);
            given(contractChangeMapper.updateRequesterSignature(CHANGE_REQUEST_ID, SAVED_PATH))
                    .willReturn(1);
            given(loanContractService.findContract(CONTRACT_ID, DEBTOR_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(userService.getMyInfo(DEBTOR_ID))
                    .willReturn(UserResponse.builder().name("채무자").build());

            contractChangeService.submitRequesterSignature(CONTRACT_ID, CHANGE_REQUEST_ID, DEBTOR_ID, signature);

            verify(notificationService).create(
                    eq(USER_ID),
                    eq(NotificationType.CONTRACT_CHANGE),
                    any(), any(), eq(CONTRACT_ID), eq(CHANGE_REQUEST_ID)
            );
        }

        @Test
        @DisplayName("채권자가 요청자면 서명 후 채무자에게 알림이 간다")
        void submitRequesterSignatureSuccessWhenRequesterIsCreditor() {
            MultipartFile signature = mock(MultipartFile.class);

            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(changeRequestDTO(ChangeRequestStatus.PENDING, USER_ID, CONTRACT_ID)));
            given(fileService.saveSignatureFile(CHANGE_REQUEST_ID, signature))
                    .willReturn(SAVED_PATH);
            given(contractChangeMapper.updateRequesterSignature(CHANGE_REQUEST_ID, SAVED_PATH))
                    .willReturn(1);
            given(loanContractService.findContract(CONTRACT_ID, USER_ID))
                    .willReturn(createContract(ContractStatus.COMPLETED));
            given(userService.getMyInfo(USER_ID))
                    .willReturn(UserResponse.builder().name("채권자").build());

            contractChangeService.submitRequesterSignature(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID, signature);

            verify(notificationService).create(
                    eq(DEBTOR_ID),
                    eq(NotificationType.CONTRACT_CHANGE),
                    any(), any(), eq(CONTRACT_ID), eq(CHANGE_REQUEST_ID)
            );
        }

        @Test
        @DisplayName("요청을 등록한 당사자가 아니면 예외가 발생한다")
        void submitRequesterSignatureFailsWhenNotRequester() {
            MultipartFile signature = mock(MultipartFile.class);

            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(changeRequestDTO(ChangeRequestStatus.PENDING, USER_ID, CONTRACT_ID)));

            assertThatThrownBy(() ->
                    contractChangeService.submitRequesterSignature(CONTRACT_ID, CHANGE_REQUEST_ID, DEBTOR_ID, signature))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.NOT_CONTRACT_PARTY)
                    );

            verify(fileService, never()).saveSignatureFile(any(), any());
            verify(contractChangeMapper, never()).updateRequesterSignature(any(), any());
        }

        @Test
        @DisplayName("이미 처리된 요청이면 예외가 발생한다")
        void submitRequesterSignatureFailsWhenAlreadyProcessed() {
            MultipartFile signature = mock(MultipartFile.class);

            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(changeRequestDTO(ChangeRequestStatus.APPROVED, USER_ID, CONTRACT_ID)));

            assertThatThrownBy(() ->
                    contractChangeService.submitRequesterSignature(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID, signature))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.ALREADY_BEING_REQUEST)
                    );

            verify(fileService, never()).saveSignatureFile(any(), any());
            verify(contractChangeMapper, never()).updateRequesterSignature(any(), any());
        }

        @Test
        @DisplayName("변경 요청이 해당 계약의 것이 아니면 예외가 발생한다")
        void submitRequesterSignatureFailsWhenContractIdMismatch() {
            MultipartFile signature = mock(MultipartFile.class);
            Long otherContractId = 999L;

            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(changeRequestDTO(ChangeRequestStatus.PENDING, USER_ID, otherContractId)));

            assertThatThrownBy(() ->
                    contractChangeService.submitRequesterSignature(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID, signature))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND)
                    );

            verify(fileService, never()).saveSignatureFile(any(), any());
            verify(contractChangeMapper, never()).updateRequesterSignature(any(), any());
        }

        @Test
        @DisplayName("존재하지 않는 변경 요청이면 예외가 발생한다")
        void submitRequesterSignatureFailsWhenChangeRequestNotFound() {
            MultipartFile signature = mock(MultipartFile.class);

            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    contractChangeService.submitRequesterSignature(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID, signature))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND)
                    );

            verify(fileService, never()).saveSignatureFile(any(), any());
        }
    }

    @Nested
    @DisplayName("변경 요청 취소")
    class CancelChangeRequest {

        private static final Long CHANGE_REQUEST_ID = 300L;
        private static final Long V2_CONTRACT_ID = 301L;

        private LoanContractChangeDTO changeRequestDTO(
                ChangeRequestStatus status, Long ownerUserId, Long contractId, String requesterSignature
        ) {
            return LoanContractChangeDTO.builder()
                    .changeRequestId(CHANGE_REQUEST_ID)
                    .userId(ownerUserId)
                    .contractId(contractId)
                    .changeReason("이자율 조정 요청")
                    .status(status)
                    .requesterSignature(requesterSignature)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
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
        @DisplayName("서명 전 요청은 요청자 본인이 취소할 수 있고, 임시 계약도 함께 무효화한다")
        void cancelChangeRequestSuccess() {
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(changeRequestDTO(ChangeRequestStatus.PENDING, USER_ID, CONTRACT_ID, null)));
            given(contractChangeMapper.cancelPendingUnsignedRequest(CHANGE_REQUEST_ID))
                    .willReturn(1);
            given(loanContractService.findPendingContractByPreviousId(CONTRACT_ID))
                    .willReturn(Optional.of(pendingV2()));

            contractChangeService.cancelChangeRequest(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID);

            verify(contractChangeMapper).cancelPendingUnsignedRequest(CHANGE_REQUEST_ID);
            verify(loanContractService).rejectChangedContract(V2_CONTRACT_ID);
        }

        @Test
        @DisplayName("이미 서명을 제출한 요청은 취소할 수 없다")
        void cancelChangeRequestFailsWhenAlreadySigned() {
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(changeRequestDTO(
                            ChangeRequestStatus.PENDING, USER_ID, CONTRACT_ID, "uploads/signatures/change_300_signature.png"
                    )));
            given(contractChangeMapper.cancelPendingUnsignedRequest(CHANGE_REQUEST_ID))
                    .willReturn(0);   // ← DB 조건(requester_signature IS NULL)에 안 걸려서 0건

            assertThatThrownBy(() ->
                    contractChangeService.cancelChangeRequest(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.ALREADY_SIGNED)
                    );

            verify(contractChangeMapper).cancelPendingUnsignedRequest(CHANGE_REQUEST_ID);   // never() → 호출은 되지만 실패로 처리됨
            verify(loanContractService, never()).rejectChangedContract(any());
        }

        @Test
        @DisplayName("요청을 등록한 당사자가 아니면 취소할 수 없다")
        void cancelChangeRequestFailsWhenNotRequester() {
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(changeRequestDTO(ChangeRequestStatus.PENDING, USER_ID, CONTRACT_ID, null)));

            assertThatThrownBy(() ->
                    contractChangeService.cancelChangeRequest(CONTRACT_ID, CHANGE_REQUEST_ID, DEBTOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.NOT_CONTRACT_PARTY)
                    );

            verify(contractChangeMapper, never()).cancelPendingUnsignedRequest(any());
            verify(loanContractService, never()).rejectChangedContract(any());
        }

        @Test
        @DisplayName("이미 처리된(승인/반려/취소) 요청이면 취소할 수 없다")
        void cancelChangeRequestFailsWhenAlreadyProcessed() {
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(changeRequestDTO(ChangeRequestStatus.APPROVED, USER_ID, CONTRACT_ID, null)));

            assertThatThrownBy(() ->
                    contractChangeService.cancelChangeRequest(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.ALREADY_BEING_REQUEST)
                    );

            verify(contractChangeMapper, never()).cancelPendingUnsignedRequest(any());
            verify(loanContractService, never()).rejectChangedContract(any());
        }

        @Test
        @DisplayName("변경 요청이 해당 계약의 것이 아니면 취소할 수 없다")
        void cancelChangeRequestFailsWhenContractIdMismatch() {
            Long otherContractId = 999L;

            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(changeRequestDTO(ChangeRequestStatus.PENDING, USER_ID, otherContractId, null)));

            assertThatThrownBy(() ->
                    contractChangeService.cancelChangeRequest(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND)
                    );

            verify(contractChangeMapper, never()).cancelPendingUnsignedRequest(any());
            verify(loanContractService, never()).rejectChangedContract(any());
        }

        @Test
        @DisplayName("존재하지 않는 변경 요청이면 취소할 수 없다")
        void cancelChangeRequestFailsWhenChangeRequestNotFound() {
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    contractChangeService.cancelChangeRequest(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND)
                    );

            verify(contractChangeMapper, never()).cancelPendingUnsignedRequest(any());
        }

        @Test
        @DisplayName("동시 요청으로 이미 서명이 제출되어 갱신 행이 0건이면 예외가 발생한다")
        void cancelChangeRequestFailsWhenRaceConditionLeavesZeroRowsUpdated() {
            given(contractChangeMapper.findByChangeRequestId(CHANGE_REQUEST_ID))
                    .willReturn(Optional.of(changeRequestDTO(ChangeRequestStatus.PENDING, USER_ID, CONTRACT_ID, null)));
            given(contractChangeMapper.cancelPendingUnsignedRequest(CHANGE_REQUEST_ID))
                    .willReturn(0);

            assertThatThrownBy(() ->
                    contractChangeService.cancelChangeRequest(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.ALREADY_SIGNED)
                    );

            verify(loanContractService, never()).rejectChangedContract(any());
        }
    }
}