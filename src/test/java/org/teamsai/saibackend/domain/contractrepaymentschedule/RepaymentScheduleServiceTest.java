package org.teamsai.saibackend.domain.contractrepaymentschedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractMapper;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.response.RepaymentScheduleSummaryResponse;
import org.teamsai.saibackend.domain.contractrepaymentschedule.mapper.RepaymentScheduleMapper;
import org.teamsai.saibackend.domain.contractrepaymentschedule.service.RepaymentScheduleService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class RepaymentScheduleServiceTest {

    @Mock
    private RepaymentScheduleMapper repaymentScheduleMapper;

    @Mock
    private LoanContractMapper loanContractMapper;

    @InjectMocks
    private RepaymentScheduleService repaymentScheduleService;

    @Test
    @DisplayName("계약 조건대로 스케줄을 계산해서 12건 저장한다")
    void generateSchedule_savesAllRows() {
        Long contractId = 1L;
        LoanContractResponse contract = LoanContractResponse.builder()
                .contractId(contractId)
                .principalAmount(BigDecimal.valueOf(10_000_000))
                .interestRate(BigDecimal.valueOf(12))
                .repaymentType(RepaymentMethod.EQUAL_PRINCIPAL_AND_INTEREST)
                .startDate(LocalDate.of(2026, 1, 1))
                .maturityDate(LocalDate.of(2027, 1, 1))
                .build();

        when(loanContractMapper.findContractById(contractId)).thenReturn(Optional.of(contract));

        repaymentScheduleService.generateSchedule(contractId);

        verify(repaymentScheduleMapper).insertAll(argThat(list -> list.size() == 12));
    }

    @Test
    @DisplayName("존재하지 않는 계약이면 예외를 던지고 저장하지 않는다")
    void generateSchedule_throwsWhenContractNotFound() {
        Long contractId = 999L;
        when(loanContractMapper.findContractById(contractId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repaymentScheduleService.generateSchedule(contractId))
                .isInstanceOf(DomainException.class);

        verify(repaymentScheduleMapper, never()).insertAll(anyList());
    }

    @Test
    @DisplayName("요약 조회 시 PAID 건만 누적 납부액에 합산된다")
    void getScheduleSummary_calculatesCorrectly() {
        Long contractId = 1L;
        List<RepaymentScheduleDTO> schedules = List.of(
                buildRow(1, "PAID", "800000"),
                buildRow(2, "PAID", "800000"),
                buildRow(3, "PENDING", "800000"),
                buildRow(4, "PENDING", "800000")
        );
        when(repaymentScheduleMapper.findByContractId(contractId)).thenReturn(schedules);

        RepaymentScheduleSummaryResponse summary = repaymentScheduleService.getScheduleSummary(contractId);

        assertThat(summary.getTotalScheduledAmount()).isEqualByComparingTo("3200000");
        assertThat(summary.getPaidAmount()).isEqualByComparingTo("1600000");
        assertThat(summary.getRemainingAmount()).isEqualByComparingTo("1600000");
        assertThat(summary.getPaidCount()).isEqualTo(2);
        assertThat(summary.getTotalCount()).isEqualTo(4);
        assertThat(summary.getSchedules()).hasSize(4);
    }

    @Test
    @DisplayName("납부 확정 시 Mapper의 상태변경 메서드를 호출한다")
    void markAsPaid_callsUpdateStatusToPaid() {
        Long scheduleId = 5L;
        LocalDateTime paidAt = LocalDateTime.now();

        repaymentScheduleService.markAsPaid(scheduleId, paidAt);

        verify(repaymentScheduleMapper).updateStatusToPaid(scheduleId, paidAt);
    }

    private RepaymentScheduleDTO buildRow(int sequence, String status, String totalPaymentDue) {
        return RepaymentScheduleDTO.builder()
                .scheduleId((long) sequence)
                .contractId(1L)
                .sequence(sequence)
                .totalPaymentDue(new BigDecimal(totalPaymentDue))
                .status(status)
                .build();
    }
}
