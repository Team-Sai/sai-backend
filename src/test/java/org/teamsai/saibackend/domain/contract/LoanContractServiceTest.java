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
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractRequest;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractMapper;
import org.teamsai.saibackend.domain.contract.service.contract.LoanContractFileService;
import org.teamsai.saibackend.domain.contract.service.contract.LoanContractService;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanContractService 단위 테스트")
class LoanContractServiceTest {

    private static final Long CREDITOR_ID = 1L;
    private static final Long DEBTOR_ID = 2L;
    private static final Long OTHER_USER_ID = 999L;
    private static final String DEBTOR_EMAIL = "debtor@example.com";
    private static final Long CONTRACT_ID = 1L;

    @Mock
    private LoanContractMapper contractMapper;

    @Mock
    private LoanContractFileService fileService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private LoanContractService loanContractService;

    @Nested
    @DisplayName("차용증 최초 생성")
    class CreateContract {

        @Test
        @DisplayName("채권자와 채무자를 각각 조회해 계약서를 생성한다")
        void createContractSuccess() {
            LoanContractRequest request = createRequest();
            UserDTO creditor = createUser(1L);
            UserDTO debtor = createUser(2L);

            given(userMapper.findById(CREDITOR_ID)).willReturn(Optional.of(creditor));
            given(userMapper.findByEmail(DEBTOR_EMAIL)).willReturn(Optional.of(debtor));

            loanContractService.createContract(request, CREDITOR_ID);

            verify(contractMapper).insertByContract(request, creditor, debtor);
        }

        @Test
        @DisplayName("생성된 계약서의 contractId를 그대로 반환한다")
        void createContractReturnsGeneratedId() {
            LoanContractRequest request = createRequest();
            given(userMapper.findById(CREDITOR_ID)).willReturn(Optional.of(createUser(1L)));
            given(userMapper.findByEmail(DEBTOR_EMAIL)).willReturn(Optional.of(createUser(2L)));

            request.setContractId(100L);

            Long contractId = loanContractService.createContract(request, CREDITOR_ID);

            assertThat(contractId).isEqualTo(100L);
        }

        @Test
        @DisplayName("채권자를 찾을 수 없으면 예외가 발생하고 계약서를 생성하지 않는다")
        void createContractFailsWhenCreditorNotFound() {
            LoanContractRequest request = createRequest();
            given(userMapper.findById(CREDITOR_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> loanContractService.createContract(request, CREDITOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(UserErrorCode.USER_NOT_FOUND)
                    );

            verify(contractMapper, never()).insertByContract(any(), any(), any());
        }

        @Test
        @DisplayName("채무자 이메일로 가입된 회원을 찾을 수 없으면 예외가 발생하고 계약서를 생성하지 않는다")
        void createContractFailsWhenDebtorNotFound() {
            LoanContractRequest request = createRequest();
            given(userMapper.findById(CREDITOR_ID)).willReturn(Optional.of(createUser(1L)));
            given(userMapper.findByEmail(DEBTOR_EMAIL)).willReturn(Optional.empty());

            assertThatThrownBy(() -> loanContractService.createContract(request, CREDITOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractErrorCode.DEBTOR_NOT_FOUND)
                    );

            verify(contractMapper, never()).insertByContract(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("채권자 전자서명 제출")
    class SubmitCreditorSignature {

        @Test
        @DisplayName("서명 파일을 저장하고 상태를 PENDING으로 변경한다")
        void submitCreditorSignatureSuccess() {
            MultipartFile signature = mock(MultipartFile.class);
            given(fileService.saveSignatureFile(CONTRACT_ID, signature))
                    .willReturn("uploads/signatures/1_signature.png");

            ContractStatus status = loanContractService.submitCreditorSignature(CONTRACT_ID, signature);

            verify(contractMapper).updateCreditorSignature(
                    CONTRACT_ID, "uploads/signatures/1_signature.png", ContractStatus.PENDING
            );
            assertThat(status).isEqualTo(ContractStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("채무자 전자서명 제출")
    class SubmitDebtorSignature {

        @Test
        @DisplayName("서명 파일을 저장하고 상태를 COMPLETED로 변경한다")
        void submitDebtorSignatureSuccess() {
            MultipartFile signature = mock(MultipartFile.class);
            given(fileService.saveSignatureFile(CONTRACT_ID, signature))
                    .willReturn("uploads/signatures/1_signature.png");

            ContractStatus status = loanContractService.submitDebtorSignature(CONTRACT_ID, signature);

            verify(contractMapper).updateDebtorSignature(
                    CONTRACT_ID, "uploads/signatures/1_signature.png", ContractStatus.COMPLETED
            );
            assertThat(status).isEqualTo(ContractStatus.COMPLETED);
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

    private UserDTO createUser(Long userId) {
        return UserDTO.builder()
                .userId(userId)
                .userToken("token-" + userId)
                .email("user" + userId + "@example.com")
                .password("encoded-password")
                .name("김사이")
                .birthDate(LocalDate.of(1995, 5, 5))
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
