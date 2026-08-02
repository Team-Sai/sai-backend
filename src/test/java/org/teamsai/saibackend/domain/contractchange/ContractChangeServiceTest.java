package org.teamsai.saibackend.domain.contractchange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contractchange.dto.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractReadDTO;
import org.teamsai.saibackend.domain.contractchange.exception.ContractChangeErrorCode;
import org.teamsai.saibackend.domain.contractchange.mapper.ContractChangeMapper;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContractChangeService 단위 테스트")
class ContractChangeServiceTest {

    private static final Long CONTRACT_ID = 1L;

    @Mock
    private ContractChangeMapper contractChangeMapper;

    @InjectMocks
    private ContractChangeService contractChangeService;

    @Nested
    @DisplayName("현 계약의 조건 조회")
    class GetContract {

        @Test
        @DisplayName("contractId로 계약서를 조회해 반환한다")
        void getContractSuccess() {

            given(contractChangeMapper.findById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract()));


            LoanContractReadDTO result = contractChangeService.getContract(CONTRACT_ID);


            assertThat(result.getContractId()).isEqualTo(CONTRACT_ID);
        }

        @Test
        @DisplayName("존재하지 않는 계약서면 예외가 발생한다")
        void getContractFailsWhenNotFound() {
            given(contractChangeMapper.findById(CONTRACT_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> contractChangeService.getContract(CONTRACT_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CONTRACT_NOT_FOUND)
                    );
        }
    }

    private LoanContractReadDTO createContract() {
        return LoanContractReadDTO.builder()
                .contractId(CONTRACT_ID)
                .status("ACTIVE")
                .build();
    }

    private ContractChangeRequest changeRequest(){
        return ContractChangeRequest.builder()
                .changeReason("이자율 조정 요청")
                .newMaturityDate(LocalDate.of(2027,1,1))
                .newInterestRate(BigDecimal.valueOf(5.0))
                .newRepaymentType("원리금균등상환")
                .newRepaymentDate(LocalDate.of(2027, 1, 15))
                .userId(1L)
                .build();
    }

    @Nested
    @DisplayName("변경 요청 저장")
    class RequestChange{

        @Test
        @DisplayName("계약서가 존재하면 변경 요청을 저장한다.")
        void requestChangeSuccess(){
            given(contractChangeMapper.findById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract()));

            contractChangeService.requestChange(CONTRACT_ID, changeRequest());

            verify(contractChangeMapper).insert(any());
        }

        @Test
        @DisplayName("존재하지 않는 계약서면 예외가 발생한다")
        void getContractFailsWhenNotFound() {
            given(contractChangeMapper.findById(CONTRACT_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> contractChangeService.requestChange(CONTRACT_ID, changeRequest()))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CONTRACT_NOT_FOUND)
                    );
        }


    }
}
