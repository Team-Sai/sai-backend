package org.teamsai.saibackend.domain.contractdetail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.contract.LoanContractService;
import org.teamsai.saibackend.domain.contractdetail.dto.response.ContractDetailResponse;
import org.teamsai.saibackend.domain.contractdetail.service.ContractDetailService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContractDetailService 단위 테스트")
public class ContractDetailTest {

    private static final Long CONTRACT_ID = 1L;
    private static final Long CREDITOR_ID = 10L;
    private static final Long DEBTOR_ID = 20L;

    @Mock
    private LoanContractService loanContractService;

    @InjectMocks
    private ContractDetailService contractDetailService;

    @Nested
    @DisplayName("채권자 확인")
    class GetContract{

        @Test
        @DisplayName("채권자면 계약 버튼 활성화")
        void getContractCreditor(){
            given(loanContractService.findContract(CONTRACT_ID, CREDITOR_ID))
                    .willReturn(createContract());

            ContractDetailResponse result = contractDetailService.getCheck(CONTRACT_ID, CREDITOR_ID);

            assertThat(result.isCanRequestChange()).isTrue();

        }

        @Test
        @DisplayName("채무자이면 계약 버튼 비활성화")
        void getContractDebtor(){
            given(loanContractService.findContract(CONTRACT_ID, DEBTOR_ID))
                    .willReturn(createContract());

            ContractDetailResponse result = contractDetailService.getCheck(CONTRACT_ID, DEBTOR_ID);

            assertThat(result.isCanRequestChange()).isFalse();

        }
    }

    private LoanContractResponse createContract() {
        return LoanContractResponse.builder()
                .contractId(CONTRACT_ID)
                .creditorId(CREDITOR_ID)
                .debtorId(DEBTOR_ID)
                .build();

    }


}
