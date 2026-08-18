package org.teamsai.saibackend.domain.contractchangedetail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
import org.teamsai.saibackend.domain.contractchange.type.ChangeRequestStatus;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;
import org.teamsai.saibackend.domain.contractchangedetail.dto.ChangeRequestDetailDTO;
import org.teamsai.saibackend.domain.contractchangedetail.exception.ChangeRequestDetailErrorCode;
import org.teamsai.saibackend.domain.contractchangedetail.service.ChangeRequestDetailService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeRequestDetailService 단위 테스트")
class ChangeRequestDetailServiceTest {

    private static final Long CONTRACT_ID = 1L;
    private static final Long CHANGE_REQUEST_ID = 5L;
    private static final Long USER_ID = 10L;

    @Mock
    private ContractChangeService contractChangeService;

    @InjectMocks
    private ChangeRequestDetailService changeRequestDetailService;

    private LoanContractChangeDTO createChangeRequestWithDifferentContractId(){
        return LoanContractChangeDTO.builder()
                .changeRequestId(CHANGE_REQUEST_ID)
                .status(ChangeRequestStatus.PENDING)
                .contractId(999L)
                .build();
    }

    @Test
    @DisplayName("변경요청이 다른 계약서에 속하면 예외가 발생한다.")
    void getDetailFailsWhenChangeRequestBelongsToOtherContract(){
        given(contractChangeService.getChangeRequest(CHANGE_REQUEST_ID))
                .willReturn(createChangeRequestWithDifferentContractId());

        assertThatThrownBy(() -> changeRequestDetailService.getDetail(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ChangeRequestDetailErrorCode.CHANGE_REQUEST_NOT_FOUND)

                );
    }

    @Test
    @DisplayName("계약서와 변경요청을 조합해 상세 정보(월 상환액 포함)를 반환한다")
    void getDetailSuccess() {
        given(contractChangeService.getContract(CONTRACT_ID, USER_ID))
                .willReturn(createContract());
        given(contractChangeService.getChangeRequest(CHANGE_REQUEST_ID))
                .willReturn(createChangeRequest());

        ChangeRequestDetailDTO result = changeRequestDetailService.getDetail(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID);

        assertThat(result.getRequesterName()).isEqualTo("김민수");
        assertThat(result.getStatus()).isEqualTo("승인 대기 중");
        assertThat(result.getExtendedMonths()).isEqualTo(12);
        assertThat(result.getCurrentMonthlyPayment()).isEqualByComparingTo(BigDecimal.valueOf(375000));
        assertThat(result.getNewMonthlyPayment()).isEqualByComparingTo(BigDecimal.valueOf(4532773.92));
    }

    private LoanContractResponse createContract() {
        return LoanContractResponse.builder()
                .contractId(CONTRACT_ID)
                .creditorName("김민수")
                .principalAmount(BigDecimal.valueOf(100_000_000))
                .interestRate(BigDecimal.valueOf(4.5))
                .repaymentType(RepaymentMethod.BULLET_REPAYMENT)
                .startDate(LocalDate.of(2025, 1, 1))
                .maturityDate(LocalDate.of(2025, 12, 31))
                .creditorId(USER_ID)
                .build();
    }

    private LoanContractChangeDTO createChangeRequest() {
        return LoanContractChangeDTO.builder()
                .changeRequestId(CHANGE_REQUEST_ID)
                .status(ChangeRequestStatus.PENDING)
                .newMaturityDate(LocalDate.of(2026, 12, 31))
                .newInterestRate(BigDecimal.valueOf(4.2))
                .newRepaymentType("EQUAL_PRINCIPAL_AND_INTEREST")
                .newRepaymentDate(15)
                .changeReason("자금 사정으로 인한 연장 요청")
                .createdAt(LocalDateTime.now())
                .contractId(CONTRACT_ID)
                .userId(USER_ID)
                .build();
    }

    @Test
    @DisplayName("newMaturityDate가 없으면 현재 계약의 만기일로 채워진다")
    void getDetail_fallsBackToCurrentMaturityDate_whenNewMaturityDateIsNull() {
        LoanContractResponse contract = createContract();

        LoanContractChangeDTO changeDTO = LoanContractChangeDTO.builder()
                .changeRequestId(CHANGE_REQUEST_ID)
                .status(ChangeRequestStatus.PENDING)
                .newMaturityDate(null)
                .newInterestRate(BigDecimal.valueOf(4.2))
                .newRepaymentType("EQUAL_PRINCIPAL_AND_INTEREST")
                .newRepaymentDate(15)
                .changeReason("자금 사정으로 인한 연장 요청")
                .createdAt(LocalDateTime.now())
                .contractId(CONTRACT_ID)
                .userId(USER_ID)
                .build();

        given(contractChangeService.getContract(CONTRACT_ID, USER_ID)).willReturn(contract);
        given(contractChangeService.getChangeRequest(CHANGE_REQUEST_ID)).willReturn(changeDTO);

        ChangeRequestDetailDTO result = changeRequestDetailService.getDetail(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID);

        assertThat(result.getNewMaturityDate()).isEqualTo(contract.getMaturityDate());
    }

    @Test
    @DisplayName("계약 당사자가 아니면 예외가 발생한다")
    void getDetailFailsWhenNotContractParty() {
        given(contractChangeService.getContract(CONTRACT_ID, USER_ID))
                .willThrow(LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException());

        assertThatThrownBy(() -> changeRequestDetailService.getDetail(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID))
                .isInstanceOf(DomainException.class);

    }

    @Test
    @DisplayName("채무자가 요청자면 requesterName이 채무자 이름으로 결정된다")
    void getDetail_requesterIsDebtor() {
        LoanContractResponse contract = createContract();
        LoanContractChangeDTO changeDTO = LoanContractChangeDTO.builder()
                .changeRequestId(CHANGE_REQUEST_ID)
                .contractId(CONTRACT_ID)
                .userId(contract.getDebtorId())
                .status(ChangeRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(contractChangeService.getContract(CONTRACT_ID, USER_ID)).thenReturn(contract);
        when(contractChangeService.getChangeRequest(CHANGE_REQUEST_ID)).thenReturn(changeDTO);
        when(contractChangeService.getPendingChangedContractId(CONTRACT_ID)).thenReturn(null);

        ChangeRequestDetailDTO result = changeRequestDetailService.getDetail(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID);

        assertThat(result.getRequesterName()).isEqualTo(contract.getDebtorName());
    }

    @Test
    @DisplayName("반려 사유(returnReason)가 응답에 그대로 채워진다")
    void getDetail_includesReturnReason() {
        LoanContractResponse contract = createContract();
        LoanContractChangeDTO changeDTO = LoanContractChangeDTO.builder()
                .changeRequestId(CHANGE_REQUEST_ID)
                .contractId(CONTRACT_ID)
                .userId(contract.getCreditorId())
                .status(ChangeRequestStatus.REJECTED)
                .returnReason("이율이 너무 높습니다")
                .createdAt(LocalDateTime.now())
                .build();

        when(contractChangeService.getContract(CONTRACT_ID, USER_ID)).thenReturn(contract);
        when(contractChangeService.getChangeRequest(CHANGE_REQUEST_ID)).thenReturn(changeDTO);
        when(contractChangeService.getPendingChangedContractId(CONTRACT_ID)).thenReturn(null);

        ChangeRequestDetailDTO result = changeRequestDetailService.getDetail(CONTRACT_ID, CHANGE_REQUEST_ID, USER_ID);

        assertThat(result.getReturnReason()).isEqualTo("이율이 너무 높습니다");
    }

}
