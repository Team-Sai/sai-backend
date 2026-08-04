package org.teamsai.saibackend.domain.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractDebtorLinkRequest;
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractRequest;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractMapper;
import org.teamsai.saibackend.domain.contract.service.contract.LoanContractFileService;
import org.teamsai.saibackend.domain.contract.service.contract.LoanContractService;
import org.teamsai.saibackend.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saibackend.domain.identity.service.IdentityService;
import org.teamsai.saibackend.domain.identity.type.IdentityPurpose;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanContractService 단위 테스트")
class LoanContractServiceTest {

    private static final Long CREDITOR_ID = 1L;
    private static final Long DEBTOR_ID = 2L;
    private static final Long OTHER_USER_ID = 999L;
    private static final String DEBTOR_EMAIL = "debtor@example.com";
    private static final Long CONTRACT_ID = 1L;
    private static final String IDENTITY_VERIFICATION_ID = "identity-verification-abc123";

    @Mock
    private LoanContractMapper contractMapper;

    @Mock
    private LoanContractFileService fileService;

    @Mock
    private UserService userService;

    @Mock
    private IdentityService identityService;

    @InjectMocks
    private LoanContractService loanContractService;

    @Nested
    @DisplayName("차용증 최초 생성")
    class CreateContract {

        @Test
        @DisplayName("채권자 본인 확인 후 계약서를 생성한다")
        void createContractSuccess() {
            LoanContractRequest request = createRequest();

            loanContractService.createContract(request, CREDITOR_ID);

            verify(identityService).consume(
                    CREDITOR_ID, IDENTITY_VERIFICATION_ID, IdentityPurpose.LOAN_CONTRACT
            );
            verify(userService).getMyInfo(CREDITOR_ID);
            verify(contractMapper).insertByContract(request, CREDITOR_ID);
        }

        @Test
        @DisplayName("생성된 계약서의 contractId를 그대로 반환한다")
        void createContractReturnsGeneratedId() {
            LoanContractRequest request = createRequest();
            request.setContractId(100L);

            Long contractId = loanContractService.createContract(request, CREDITOR_ID);

            assertThat(contractId).isEqualTo(100L);
        }

        @Test
        @DisplayName("채권자를 찾을 수 없으면 예외가 발생하고 계약서를 생성하지 않는다")
        void createContractFailsWhenCreditorNotFound() {
            LoanContractRequest request = createRequest();

            willThrow(UserErrorCode.USER_NOT_FOUND.toException())
                    .given(userService)
                    .getMyInfo(CREDITOR_ID);

            assertThatThrownBy(() -> loanContractService.createContract(request, CREDITOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(UserErrorCode.USER_NOT_FOUND)
                    );

            verify(contractMapper, never()).insertByContract(any(), any());
        }

        @Test
        @DisplayName("본인인증을 완료하지 않았으면 예외가 발생하고 계약서를 생성하지 않는다")
        void createContractFailsWhenIdentityNotVerified() {
            LoanContractRequest request = createRequest();

            willThrow(IdentityErrorCode.IDENTITY_VERIFICATION_CONSUME_FAILED.toException())
                    .given(identityService)
                    .consume(CREDITOR_ID, IDENTITY_VERIFICATION_ID, IdentityPurpose.LOAN_CONTRACT);

            assertThatThrownBy(() -> loanContractService.createContract(request, CREDITOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(IdentityErrorCode.IDENTITY_VERIFICATION_CONSUME_FAILED)
                    );

            verify(userService, never()).getMyInfo(any());
            verify(contractMapper, never()).insertByContract(any(), any());
        }
    }

    @Nested
    @DisplayName("채무자 계약 합류")
    class LinkDebtor {

        private final LoanContractDebtorLinkRequest linkRequest =
                new LoanContractDebtorLinkRequest(IDENTITY_VERIFICATION_ID);

        @Test
        @DisplayName("본인인증을 마친 채무자를 계약에 연결한다")
        void linkDebtorSuccess() {
            given(contractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createResponseWithoutDebtor()));

            loanContractService.linkDebtor(CONTRACT_ID, DEBTOR_ID, linkRequest);

            verify(identityService).consume(
                    DEBTOR_ID, IDENTITY_VERIFICATION_ID, IdentityPurpose.LOAN_CONTRACT
            );
            verify(contractMapper).updateDebtorId(CONTRACT_ID, DEBTOR_ID);
        }

        @Test
        @DisplayName("계약서를 찾을 수 없으면 예외가 발생하고 연결하지 않는다")
        void linkDebtorFailsWhenContractNotFound() {
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> loanContractService.linkDebtor(CONTRACT_ID, DEBTOR_ID, linkRequest))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_NOT_FOUND)
                    );

            verify(contractMapper, never()).updateDebtorId(any(), any());
        }

        @Test
        @DisplayName("이미 채무자가 연결되어 있으면 예외가 발생하고 본인인증을 소비하지 않는다")
        void linkDebtorFailsWhenAlreadyLinked() {
            given(contractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createResponse()));

            assertThatThrownBy(() -> loanContractService.linkDebtor(CONTRACT_ID, OTHER_USER_ID, linkRequest))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.DEBTOR_ALREADY_LINKED)
                    );

            verify(identityService, never()).consume(any(), any(), any());
            verify(contractMapper, never()).updateDebtorId(any(), any());
        }

        @Test
        @DisplayName("채권자 본인이 채무자로 합류할 수 없다")
        void linkDebtorFailsWhenCreditorTriesToJoinAsDebtor() {
            given(contractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createResponseWithoutDebtor()));

            assertThatThrownBy(() -> loanContractService.linkDebtor(CONTRACT_ID, CREDITOR_ID, linkRequest))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CANNOT_CREATE_CONTRACT_TO_SELF)
                    );

            verify(identityService, never()).consume(any(), any(), any());
            verify(contractMapper, never()).updateDebtorId(any(), any());
        }

        @Test
        @DisplayName("본인인증을 완료하지 않았으면 예외가 발생하고 연결하지 않는다")
        void linkDebtorFailsWhenIdentityNotVerified() {
            given(contractMapper.findContractById(CONTRACT_ID))
                    .willReturn(Optional.of(createResponseWithoutDebtor()));

            willThrow(IdentityErrorCode.IDENTITY_VERIFICATION_CONSUME_FAILED.toException())
                    .given(identityService)
                    .consume(DEBTOR_ID, IDENTITY_VERIFICATION_ID, IdentityPurpose.LOAN_CONTRACT);

            assertThatThrownBy(() -> loanContractService.linkDebtor(CONTRACT_ID, DEBTOR_ID, linkRequest))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(IdentityErrorCode.IDENTITY_VERIFICATION_CONSUME_FAILED)
                    );

            verify(contractMapper, never()).updateDebtorId(any(), any());
        }
    }

    @Nested
    @DisplayName("채권자 전자서명 제출")
    class SubmitCreditorSignature {

        @Test
        @DisplayName("서명 파일을 저장하고 상태를 PENDING으로 변경한다")
        void submitCreditorSignatureSuccess() {
            MultipartFile signature = mock(MultipartFile.class);
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.of(createResponse()));
            given(fileService.saveSignatureFile(CONTRACT_ID, signature))
                    .willReturn("uploads/signatures/1_signature.png");

            ContractStatus status = loanContractService.submitCreditorSignature(CONTRACT_ID, CREDITOR_ID, signature);

            verify(contractMapper).updateCreditorSignature(
                    CONTRACT_ID, "uploads/signatures/1_signature.png", ContractStatus.PENDING
            );
            assertThat(status).isEqualTo(ContractStatus.PENDING);
        }

        @Test
        @DisplayName("계약 당사자가 아닌 사용자가 제출하면 예외가 발생하고 서명이 저장되지 않는다")
        void submitCreditorSignatureFailsWhenUserIsNotCreditor() {
            MultipartFile signature = mock(MultipartFile.class);
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.of(createResponse()));

            assertThatThrownBy(() ->
                    loanContractService.submitCreditorSignature(CONTRACT_ID, OTHER_USER_ID, signature)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_ACCESS_DENIED)
                    );

            verify(fileService, never()).saveSignatureFile(any(), any());
            verify(contractMapper, never()).updateCreditorSignature(any(), any(), any());
        }

        @Test
        @DisplayName("계약서를 찾을 수 없으면 예외가 발생하고 서명이 저장되지 않는다")
        void submitCreditorSignatureFailsWhenContractNotFound() {
            MultipartFile signature = mock(MultipartFile.class);
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    loanContractService.submitCreditorSignature(CONTRACT_ID, CREDITOR_ID, signature)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_NOT_FOUND)
                    );

            verify(fileService, never()).saveSignatureFile(any(), any());
            verify(contractMapper, never()).updateCreditorSignature(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("채무자 전자서명 제출")
    class SubmitDebtorSignature {

        @Test
        @DisplayName("채무자 본인이 제출하면 서명 파일과 주소를 저장하고 상태를 COMPLETED로 변경한다")
        void submitDebtorSignatureSuccess() {
            MultipartFile signature = mock(MultipartFile.class);
            String debtorAddress = "서울특별시 마포구 월드컵로 1";
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.of(createResponse()));
            given(fileService.saveSignatureFile(CONTRACT_ID, signature))
                    .willReturn("uploads/signatures/1_signature.png");

            ContractStatus status =
                    loanContractService.submitDebtorSignature(CONTRACT_ID, DEBTOR_ID, debtorAddress, signature);

            verify(contractMapper).updateDebtorSignature(
                    CONTRACT_ID, debtorAddress, "uploads/signatures/1_signature.png", ContractStatus.COMPLETED
            );
            assertThat(status).isEqualTo(ContractStatus.COMPLETED);
        }

        @Test
        @DisplayName("채무자 주소가 빈 문자열이어도 그대로 전달되어 저장된다")
        void submitDebtorSignatureWithBlankAddress() {
            MultipartFile signature = mock(MultipartFile.class);
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.of(createResponse()));
            given(fileService.saveSignatureFile(CONTRACT_ID, signature))
                    .willReturn("uploads/signatures/1_signature.png");

            loanContractService.submitDebtorSignature(CONTRACT_ID, DEBTOR_ID, "", signature);

            verify(contractMapper).updateDebtorSignature(
                    CONTRACT_ID, "", "uploads/signatures/1_signature.png", ContractStatus.COMPLETED
            );
        }

        @Test
        @DisplayName("채무자 주소가 null이어도 그대로 전달되어 저장된다")
        void submitDebtorSignatureWithNullAddress() {
            MultipartFile signature = mock(MultipartFile.class);
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.of(createResponse()));
            given(fileService.saveSignatureFile(CONTRACT_ID, signature))
                    .willReturn("uploads/signatures/1_signature.png");

            loanContractService.submitDebtorSignature(CONTRACT_ID, DEBTOR_ID, null, signature);

            verify(contractMapper).updateDebtorSignature(
                    CONTRACT_ID, null, "uploads/signatures/1_signature.png", ContractStatus.COMPLETED
            );
        }

        @Test
        @DisplayName("계약 당사자가 아닌 사용자가 제출하면 예외가 발생하고 서명이 저장되지 않는다")
        void submitDebtorSignatureFailsWhenUserIsNotDebtor() {
            MultipartFile signature = mock(MultipartFile.class);
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.of(createResponse()));

            assertThatThrownBy(() ->
                    loanContractService.submitDebtorSignature(CONTRACT_ID, OTHER_USER_ID, "아무 주소", signature)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_ACCESS_DENIED)
                    );

            verify(fileService, never()).saveSignatureFile(any(), any());
            verify(contractMapper, never()).updateDebtorSignature(any(), any(), any(), any());
        }

        @Test
        @DisplayName("계약서를 찾을 수 없으면 예외가 발생하고 서명이 저장되지 않는다")
        void submitDebtorSignatureFailsWhenContractNotFound() {
            MultipartFile signature = mock(MultipartFile.class);
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    loanContractService.submitDebtorSignature(CONTRACT_ID, DEBTOR_ID, "아무 주소", signature)
            )
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_NOT_FOUND)
                    );

            verify(fileService, never()).saveSignatureFile(any(), any());
            verify(contractMapper, never()).updateDebtorSignature(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("차용증 상세 조회")
    class FindContract {

        @Test
        @DisplayName("채권자가 조회하면 응답 DTO를 반환한다")
        void findContractSuccessAsCreditor() {
            LoanContractResponse response = createResponse();
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.of(response));

            LoanContractResponse result = loanContractService.findContract(CONTRACT_ID, CREDITOR_ID);

            assertThat(result).isEqualTo(response);
        }

        @Test
        @DisplayName("채무자가 조회하면 응답 DTO를 반환한다")
        void findContractSuccessAsDebtor() {
            LoanContractResponse response = createResponse();
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.of(response));

            LoanContractResponse result = loanContractService.findContract(CONTRACT_ID, DEBTOR_ID);

            assertThat(result).isEqualTo(response);
        }

        @Test
        @DisplayName("계약서를 찾을 수 없으면 예외가 발생한다")
        void findContractFailsWhenNotFound() {
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> loanContractService.findContract(CONTRACT_ID, CREDITOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_NOT_FOUND)
                    );
        }

        @Test
        @DisplayName("계약 당사자가 아닌 사용자가 조회하면 예외가 발생한다")
        void findContractFailsWhenUserIsNotParty() {
            LoanContractResponse response = createResponse();
            given(contractMapper.findContractById(CONTRACT_ID)).willReturn(Optional.of(response));

            assertThatThrownBy(() -> loanContractService.findContract(CONTRACT_ID, OTHER_USER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.CONTRACT_ACCESS_DENIED)
                    );
        }
    }

    private LoanContractRequest createRequest() {
        return LoanContractRequest.builder()
                .identityVerificationId(IDENTITY_VERIFICATION_ID)
                .principalAmount(BigDecimal.valueOf(1_000_000))
                .interestRate(BigDecimal.valueOf(5.0))
                .repaymentType(RepaymentMethod.EQUAL_PRINCIPAL_AND_INTEREST)
                .startDate(LocalDate.of(2026, 1, 1))
                .maturityDate(LocalDate.of(2027, 1, 1))
                .repaymentDay(15)
                .creditorAddress("서울특별시 강남구 테헤란로 123")
                .debtorEmail(DEBTOR_EMAIL)
                .contractAlias("생활비 차용")
                .terms(null)
                .build();
    }

    private LoanContractResponse createResponseWithoutDebtor() {
        return LoanContractResponse.builder()
                .contractId(CONTRACT_ID)
                .creditorId(CREDITOR_ID)
                .debtorId(null)
                .creditorName("김채권")
                .creditorBirthDate("1995-05-05")
                .creditorAddress("서울특별시 강남구 테헤란로 123")
                .principalAmount(BigDecimal.valueOf(1_000_000))
                .interestRate(BigDecimal.valueOf(5.0))
                .repaymentType(RepaymentMethod.EQUAL_PRINCIPAL_AND_INTEREST)
                .startDate(LocalDate.of(2026, 1, 1))
                .maturityDate(LocalDate.of(2027, 1, 1))
                .repaymentDay(15)
                .contractAlias("생활비 차용")
                .status(ContractStatus.DRAFT)
                .build();
    }

    private LoanContractResponse createResponse() {
        return LoanContractResponse.builder()
                .contractId(CONTRACT_ID)
                .creditorId(CREDITOR_ID)
                .debtorId(DEBTOR_ID)
                .creditorName("김채권")
                .creditorBirthDate("1995-05-05")
                .creditorAddress("서울특별시 강남구 테헤란로 123")
                .debtorName("이채무")
                .debtorBirthDate("1996-06-06")
                .principalAmount(BigDecimal.valueOf(1_000_000))
                .interestRate(BigDecimal.valueOf(5.0))
                .repaymentType(RepaymentMethod.EQUAL_PRINCIPAL_AND_INTEREST)
                .startDate(LocalDate.of(2026, 1, 1))
                .maturityDate(LocalDate.of(2027, 1, 1))
                .repaymentDay(15)
                .contractAlias("생활비 차용")
                .status(ContractStatus.DRAFT)
                .build();
    }
}
