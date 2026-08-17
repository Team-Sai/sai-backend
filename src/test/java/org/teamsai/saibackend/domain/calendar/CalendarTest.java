package org.teamsai.saibackend.domain.calendar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.calendar.response.DashboardCalendarItemResponse;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contractdashboard.dto.response.DashboardResponse;
import org.teamsai.saibackend.domain.contractdashboard.dto.response.DashboardSummaryResponse;
import org.teamsai.saibackend.domain.contractdashboard.service.DashboardService;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contractrepaymentschedule.type.RepaymentScheduleStatus;
import org.teamsai.saibackend.domain.integration.service.IntegrationDashboardService;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementListResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentObligationResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentStatusResponse;
import org.teamsai.saibackend.domain.settlement.service.SettlementPaymentStatusService;
import org.teamsai.saibackend.domain.settlement.service.SettlementQueryService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CalendarTest {

    @Mock
    private DashboardService contractDashboardService;

    @Mock
    private SettlementQueryService settlementQueryService;

    @Mock
    private SettlementPaymentStatusService settlementPaymentStatusService;

    @InjectMocks
    private IntegrationDashboardService integrationDashboardService;

    private static final Long USER_ID = 1L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 14);

    @Test
    void 채권자인_대여_스케줄은_수취예정으로_표시된다() {
        LoanContractResponse contract = buildContract(10L, USER_ID, 2L, "생활비 대출");
        RepaymentScheduleDTO schedule = buildSchedule(TARGET_DATE, RepaymentScheduleStatus.PENDING, 600_000);

        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, schedule));
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of());

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).hasSize(1);
        DashboardCalendarItemResponse item = result.get(0);
        assertThat(item.getType()).isEqualTo(PaymentTargetType.LOAN);
        assertThat(item.getTargetId()).isEqualTo(10L);
        assertThat(item.getSubLabel()).isEqualTo("수취예정");
        assertThat(item.getAmount()).isEqualByComparingTo("600000");
        assertThat(item.getDetailUrl()).isEqualTo("/contracts/10/schedule");
    }

    @Test
    void 채무자인_대여_스케줄은_납부예정으로_표시된다() {
        LoanContractResponse contract = buildContract(11L, 2L, USER_ID, "차량구입 대출");
        RepaymentScheduleDTO schedule = buildSchedule(TARGET_DATE, RepaymentScheduleStatus.PENDING, 350_000);

        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, schedule));
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of());

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSubLabel()).isEqualTo("납부예정");
    }

    @Test
    void 다른_날짜의_스케줄은_결과에서_제외된다() {
        LoanContractResponse contract = buildContract(12L, USER_ID, 2L, "생활비 대출");
        RepaymentScheduleDTO schedule = buildSchedule(
                TARGET_DATE.plusDays(1), RepaymentScheduleStatus.PENDING, 600_000
        );

        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, schedule));
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of());

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).isEmpty();
    }

    @Test
    void 이미_납부완료된_스케줄은_결과에서_제외된다() {
        LoanContractResponse contract = buildContract(13L, USER_ID, 2L, "생활비 대출");
        RepaymentScheduleDTO schedule = buildSchedule(TARGET_DATE, RepaymentScheduleStatus.PAID, 600_000);

        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, schedule));
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of());

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).isEmpty();
    }

    @Test
    void 정산_참여자는_낼_돈으로_표시된다() {
        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(null, null));

        SettlementListResponse settlement = settlement(
                100L, "회식비 정산", "MEMBER", "OPEN", TARGET_DATE, LocalDateTime.now()
        );
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of(settlement));
        when(settlementPaymentStatusService.getPaymentStatus(100L, USER_ID))
                .thenReturn(paymentStatus(
                        100L,
                        BigDecimal.valueOf(35_000),
                        BigDecimal.valueOf(35_000),
                        obligation(1L, USER_ID, BigDecimal.valueOf(35_000))
                ));

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).hasSize(1);
        DashboardCalendarItemResponse item = result.get(0);
        assertThat(item.getType()).isEqualTo(PaymentTargetType.SETTLEMENT);
        assertThat(item.getSubLabel()).isEqualTo("낼 돈");
        assertThat(item.getAmount()).isEqualByComparingTo("35000");
        assertThat(item.getDetailUrl()).isEqualTo("/settlements/100");
    }

    @Test
    void 마감된_정산은_결과에서_제외된다() {
        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(null, null));

        SettlementListResponse settlement = settlement(
                101L, "종료된 정산", "OWNER", "CLOSED", TARGET_DATE, LocalDateTime.now()
        );
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of(settlement));
        when(settlementPaymentStatusService.getPaymentStatus(101L, USER_ID))
                .thenReturn(paymentStatus(101L, BigDecimal.ZERO, BigDecimal.ZERO));

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).isEmpty();
    }

    @Test
    void 대여와_정산_항목이_함께_반환된다() {
        LoanContractResponse contract = buildContract(14L, USER_ID, 2L, "생활비 대출");
        RepaymentScheduleDTO schedule = buildSchedule(TARGET_DATE, RepaymentScheduleStatus.PENDING, 600_000);
        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, schedule));

        SettlementListResponse settlement = settlement(
                102L, "회식비 정산", "OWNER", "OPEN", TARGET_DATE, LocalDateTime.now()
        );
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of(settlement));
        when(settlementPaymentStatusService.getPaymentStatus(102L, USER_ID))
                .thenReturn(paymentStatus(
                        102L,
                        BigDecimal.valueOf(20_000),
                        BigDecimal.valueOf(20_000)
                ));

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DashboardCalendarItemResponse::getType)
                .containsExactlyInAnyOrder(PaymentTargetType.LOAN, PaymentTargetType.SETTLEMENT);
        assertThat(result)
                .filteredOn(item -> item.getType() == PaymentTargetType.SETTLEMENT)
                .extracting(DashboardCalendarItemResponse::getSubLabel)
                .containsExactly("받을 돈");
    }

    @Test
    void 결과는_제목_가나다순으로_정렬된다() {
        LoanContractResponse contract = buildContract(15L, USER_ID, 2L, "차용증 대출");
        RepaymentScheduleDTO schedule = buildSchedule(TARGET_DATE, RepaymentScheduleStatus.PENDING, 100_000);
        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, schedule));

        SettlementListResponse settlement = settlement(
                103L, "가나다 정산", "OWNER", "OPEN", TARGET_DATE, LocalDateTime.now()
        );
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of(settlement));
        when(settlementPaymentStatusService.getPaymentStatus(103L, USER_ID))
                .thenReturn(paymentStatus(103L, BigDecimal.valueOf(10_000), BigDecimal.valueOf(10_000)));

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).extracting(DashboardCalendarItemResponse::getTitle)
                .containsExactly("가나다 정산", "차용증 대출");
    }

    @Test
    void 상환예정일이_없는_스케줄은_예외없이_결과에서_제외된다() {
        LoanContractResponse contract = buildContract(16L, USER_ID, 2L, "생활비 대출");
        RepaymentScheduleDTO schedule = buildSchedule(null, RepaymentScheduleStatus.PENDING, 600_000);

        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, schedule));
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of());

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).isEmpty();
    }

    private LoanContractResponse buildContract(
            Long contractId, Long creditorId, Long debtorId, String alias
    ) {
        return LoanContractResponse.builder()
                .contractId(contractId)
                .status(ContractStatus.COMPLETED)
                .contractAlias(alias)
                .creditorId(creditorId)
                .debtorId(debtorId)
                .principalAmount(BigDecimal.valueOf(1_000_000))
                .interestRate(BigDecimal.valueOf(5))
                .repaymentType(RepaymentMethod.EQUAL_PRINCIPAL_AND_INTEREST)
                .startDate(LocalDate.of(2026, 7, 14))
                .maturityDate(LocalDate.of(2027, 6, 14))
                .build();
    }

    private RepaymentScheduleDTO buildSchedule(
            LocalDate dueDate, RepaymentScheduleStatus status, long amount
    ) {
        return RepaymentScheduleDTO.builder()
                .dueDate(dueDate)
                .status(status)
                .totalPaymentDue(BigDecimal.valueOf(amount))
                .remainingPrincipal(BigDecimal.valueOf(amount))
                .build();
    }

    private SettlementListResponse settlement(
            Long id, String title, String role, String status, LocalDate dueDate, LocalDateTime createdAt
    ) {
        return new SettlementListResponse(
                id, title, role, "ETC", "ONE_TIME", "EQUAL", status, dueDate, null, null, createdAt
        );
    }

    private SettlementPaymentStatusResponse paymentStatus(
            Long settlementId,
            BigDecimal totalExpected,
            BigDecimal totalRemaining,
            SettlementPaymentObligationResponse... obligations
    ) {
        return SettlementPaymentStatusResponse.builder()
                .settlementId(settlementId)
                .obligations(List.of(obligations))
                .totalExpectedAmount(totalExpected)
                .totalRemainingAmount(totalRemaining)
                .build();
    }

    private SettlementPaymentObligationResponse obligation(Long id, Long userId, BigDecimal remaining) {
        return SettlementPaymentObligationResponse.builder()
                .paymentObligationId(id)
                .userId(userId)
                .expectedAmount(remaining)
                .paidAmount(BigDecimal.ZERO)
                .remainingAmount(remaining)
                .build();
    }

    private DashboardService.IntegrationDashboardData loanData(
            LoanContractResponse contract, RepaymentScheduleDTO schedule
    ) {
        DashboardResponse dashboard = DashboardResponse.builder()
                .summary(DashboardSummaryResponse.builder()
                        .totalLentAmount(BigDecimal.ZERO)
                        .totalBorrowedAmount(BigDecimal.ZERO)
                        .build())
                .contracts(List.of())
                .build();

        List<DashboardService.LoanScheduleContext> contexts = contract == null
                ? List.of()
                : List.of(new DashboardService.LoanScheduleContext(contract, schedule));

        return new DashboardService.IntegrationDashboardData(dashboard, contexts);
    }
}
