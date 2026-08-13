package org.teamsai.saibackend.domain.contractdetail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;
import org.teamsai.saibackend.domain.contractdetail.dto.response.ContractDetailResponse;
import org.teamsai.saibackend.domain.contractdetail.service.ContractDetailService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContractDetailService 단위 테스트")
class ContractDetailServiceTest {

    private static final Long CONTRACT_ID = 1L;
    private static final Long CREDITOR_ID = 10L;
    private static final Long DEBTOR_ID = 20L;

    @Mock
    private LoanContractService loanContractService;

    @Mock
    private ContractChangeService contractChangeService;

    @InjectMocks
    private ContractDetailService contractDetailService;

    private LoanContractResponse contract() {
        return LoanContractResponse.builder()
                .contractId(CONTRACT_ID)
                .creditorId(CREDITOR_ID)
                .debtorId(DEBTOR_ID)
                .creditorAddress("서울시 채권자로 1")
                .debtorAddress("서울시 채무자로 1")
                .build();
    }

    @Nested
    @DisplayName("변경 요청 가능 여부")
    class CanRequestChange {

        @Test
        @DisplayName("채권자이고 PENDING 요청이 없으면 true를 반환한다")
        void trueWhenCreditorAndNoPendingRequest() {
            given(loanContractService.findContract(CONTRACT_ID, CREDITOR_ID)).willReturn(contract());
            given(contractChangeService.hasPendingChangeRequest(CONTRACT_ID)).willReturn(false);

            boolean result = contractDetailService.canRequestChange(CONTRACT_ID, CREDITOR_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("채권자여도 PENDING 요청이 있으면 false를 반환한다")
        void falseWhenCreditorButHasPendingRequest() {
            given(loanContractService.findContract(CONTRACT_ID, CREDITOR_ID)).willReturn(contract());
            given(contractChangeService.hasPendingChangeRequest(CONTRACT_ID)).willReturn(true);

            boolean result = contractDetailService.canRequestChange(CONTRACT_ID, CREDITOR_ID);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("채무자면 PENDING 요청 여부와 상관없이 false를 반환한다")
        void falseWhenDebtor() {
            given(loanContractService.findContract(CONTRACT_ID, DEBTOR_ID)).willReturn(contract());

            boolean result = contractDetailService.canRequestChange(CONTRACT_ID, DEBTOR_ID);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("계약 상세 조회")
    class GetCheck {

        @Test
        @DisplayName("채권자로 조회하면 채권자 주소를 반환하고 isCreditor는 true다")
        void returnsCreditorAddressForCreditor() {
            given(loanContractService.findContract(CONTRACT_ID, CREDITOR_ID)).willReturn(contract());
            given(contractChangeService.hasPendingChangeRequest(CONTRACT_ID)).willReturn(false);

            ContractDetailResponse result = contractDetailService.getCheck(CONTRACT_ID, CREDITOR_ID);

            assertThat(result.getAddress()).isEqualTo("서울시 채권자로 1");
            assertThat(result.isCreditor()).isTrue();
            assertThat(result.isCanRequestChange()).isTrue();
        }

        @Test
        @DisplayName("채무자로 조회하면 채무자 주소를 반환하고 isCreditor는 false다")
        void returnsDebtorAddressForDebtor() {
            given(loanContractService.findContract(CONTRACT_ID, DEBTOR_ID)).willReturn(contract());

            ContractDetailResponse result = contractDetailService.getCheck(CONTRACT_ID, DEBTOR_ID);

            assertThat(result.getAddress()).isEqualTo("서울시 채무자로 1");
            assertThat(result.isCreditor()).isFalse();
            assertThat(result.isCanRequestChange()).isFalse();
        }
    }
}