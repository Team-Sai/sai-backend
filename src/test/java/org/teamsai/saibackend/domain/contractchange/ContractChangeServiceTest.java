package org.teamsai.saibackend.domain.contractchange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.request.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractReadDTO;
import org.teamsai.saibackend.domain.contractchange.exception.ContractChangeErrorCode;
import org.teamsai.saibackend.domain.contractchange.mapper.ContractChangeMapper;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    private static final Long CREDITOR_ID = 10L;
    private static final Long DEBTOR_ID = 20L;
    private static final Long STRANGER_ID = 99L;

    @Mock
    private ContractChangeMapper contractChangeMapper;

    @InjectMocks
    private ContractChangeService contractChangeService;

    @Nested
    @DisplayName("현 계약의 조건 조회")
    class GetContract {

        @Test
        @DisplayName("당사자가 조회하면 계약서를 반환한다")
        void getContractSuccess() {
            given(contractChangeMapper.findById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract()));

            LoanContractReadDTO result = contractChangeService.getContract(CONTRACT_ID, CREDITOR_ID);

            assertThat(result.getContractId()).isEqualTo(CONTRACT_ID);
        }

        @Test
        @DisplayName("존재하지 않는 계약서면 예외가 발생한다")
        void getContractFailsWhenNotFound() {
            given(contractChangeMapper.findById(CONTRACT_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> contractChangeService.getContract(CONTRACT_ID, CREDITOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CONTRACT_NOT_FOUND)
                    );
        }

        @Test
        @DisplayName("당사자가 아니면 예외가 발생한다")
        void getContractFailsWhenNotParty() {
            given(contractChangeMapper.findById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract()));

            assertThatThrownBy(() -> contractChangeService.getContract(CONTRACT_ID, STRANGER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.FORBIDDEN_CONTRACT_ACCESS)
                    );
        }
    }

    private LoanContractReadDTO createContract() {
        return LoanContractReadDTO.builder()
                .contractId(CONTRACT_ID)
                .creditorId(CREDITOR_ID)
                .debtorId(DEBTOR_ID)
                .status("ACTIVE")
                .build();
    }

    private ContractChangeRequest changeRequest() {
        return ContractChangeRequest.builder()
                .changeReason("이자율 조정 요청")
                .newMaturityDate(LocalDate.of(2027, 1, 1))
                .newInterestRate(BigDecimal.valueOf(5.0))
                .newRepaymentType("원리금균등상환")
                .newRepaymentDate(LocalDate.of(2027, 1, 15))
                .build();
    }

    @Nested
    @DisplayName("변경 요청 저장")
    class RequestChange {

        @Test
        @DisplayName("당사자가 요청하면 변경 요청을 저장한다")
        void requestChangeSuccess() {
            given(contractChangeMapper.findById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract()));

            contractChangeService.requestChange(CONTRACT_ID, changeRequest(), CREDITOR_ID);

            verify(contractChangeMapper).insert(any());
        }

        @Test
        @DisplayName("존재하지 않는 계약서면 예외가 발생한다")
        void requestChangeFailsWhenNotFound() {
            given(contractChangeMapper.findById(CONTRACT_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> contractChangeService.requestChange(CONTRACT_ID, changeRequest(), CREDITOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.CONTRACT_NOT_FOUND)
                    );
        }

        @Test
        @DisplayName("당사자가 아니면 예외가 발생한다")
        void requestChangeFailsWhenNotParty() {
            given(contractChangeMapper.findById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract()));

            assertThatThrownBy(() -> contractChangeService.requestChange(CONTRACT_ID, changeRequest(), STRANGER_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.FORBIDDEN_CONTRACT_ACCESS)
                    );
        }

        @Test
        @DisplayName("이미 PENDING 요청이 있으면 예외가 발생한다")
        void requestChangeFailsWhenDuplicatePending() {
            LoanContractChangeDTO pendingRequest = LoanContractChangeDTO.builder()
                    .status("PENDING")
                    .build();

            given(contractChangeMapper.findById(CONTRACT_ID))
                    .willReturn(Optional.of(createContract()));
            given(contractChangeMapper.findByContractId(CONTRACT_ID))
                    .willReturn(List.of(pendingRequest));

            assertThatThrownBy(() -> contractChangeService.requestChange(CONTRACT_ID, changeRequest(), CREDITOR_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ContractChangeErrorCode.DUPLICATE_PENDING_REQUEST)
                    );
        }
    }
}