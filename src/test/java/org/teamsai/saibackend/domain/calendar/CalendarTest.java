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
                .thenReturn(loanData(null, List.of()));

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
                .thenReturn(loanData(null, List.of()));

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
        return buildContract(contractId, creditorId, debtorId, alias, "채권자", "채무자");
    }

    private LoanContractResponse buildContract(
            Long contractId, Long creditorId, Long debtorId, String alias,
            String creditorName, String debtorName
    ) {
        return LoanContractResponse.builder()
                .contractId(contractId)
                .status(ContractStatus.COMPLETED)
                .contractAlias(alias)
                .creditorId(creditorId)
                .debtorId(debtorId)
                .creditorName(creditorName)
                .debtorName(debtorName)
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
        return buildSchedule(dueDate, status, amount, 1);
    }

    private RepaymentScheduleDTO buildSchedule(
            LocalDate dueDate, RepaymentScheduleStatus status, long amount, int sequence
    ) {
        return RepaymentScheduleDTO.builder()
                .sequence(sequence)
                .dueDate(dueDate)
                .status(status)
                .totalPaymentDue(BigDecimal.valueOf(amount))
                .remainingPrincipal(BigDecimal.valueOf(amount))
                .build();
    }

    private DashboardService.IntegrationDashboardData loanData(
            LoanContractResponse contract, List<RepaymentScheduleDTO> schedules
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
                : schedules.stream()
                .map(schedule -> new DashboardService.LoanScheduleContext(contract, schedule))
                .toList();

        return new DashboardService.IntegrationDashboardData(dashboard, contexts);

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

    private SettlementListResponse settlement(
            Long id, String title, String role, String status, LocalDate dueDate, LocalDateTime createdAt
    ) {
        return new SettlementListResponse(id, title, role, "ETC", "ONE_TIME", "EQUAL", status, BigDecimal.ZERO, dueDate, null, null, createdAt);
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

    @Test
    void 대여_항목에는_상대방_이름과_만기일_원금_이자율이_채워진다() {
        LoanContractResponse contract = buildContract(20L, USER_ID, 2L, "생활비 대출", "김채권", "이채무");
        RepaymentScheduleDTO schedule = buildSchedule(TARGET_DATE, RepaymentScheduleStatus.PENDING, 600_000);

        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, schedule));
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of());

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).hasSize(1);
        DashboardCalendarItemResponse item = result.get(0);
        assertThat(item.getCounterpartyName()).isEqualTo("이채무");
        assertThat(item.getMaturityDate()).isEqualTo(LocalDate.of(2027, 6, 14));
        assertThat(item.getPrincipalAmount()).isEqualByComparingTo("1000000");
        assertThat(item.getInterestRate()).isEqualByComparingTo("5");
    }

    @Test
    void 채무자_입장에서는_상대방이_채권자이다() {
        LoanContractResponse contract = buildContract(21L, 2L, USER_ID, "차량구입 대출", "김채권", "이채무");
        RepaymentScheduleDTO schedule = buildSchedule(TARGET_DATE, RepaymentScheduleStatus.PENDING, 350_000);

        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, schedule));
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of());

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCounterpartyName()).isEqualTo("김채권");
    }

    @Test
    void 대여_항목의_회차_정보는_계약의_전체_스케줄_개수_기준으로_계산된다() {
        LoanContractResponse contract = buildContract(22L, USER_ID, 2L, "생활비 대출");
        RepaymentScheduleDTO schedule1 = buildSchedule(TARGET_DATE.minusMonths(1), RepaymentScheduleStatus.PAID, 600_000, 1);
        RepaymentScheduleDTO schedule2 = buildSchedule(TARGET_DATE, RepaymentScheduleStatus.PENDING, 600_000, 2);
        RepaymentScheduleDTO schedule3 = buildSchedule(TARGET_DATE.plusMonths(1), RepaymentScheduleStatus.PENDING, 600_000, 3);

        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, List.of(schedule1, schedule2, schedule3)));
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of());

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInstallmentInfo()).isEqualTo("2/3회차");
    }

    @Test
    void 조회_날짜가_오늘보다_과거면_연체로_표시된다() {
        LocalDate pastDate = LocalDate.now().minusDays(5);
        LoanContractResponse contract = buildContract(23L, USER_ID, 2L, "생활비 대출");
        RepaymentScheduleDTO schedule = buildSchedule(pastDate, RepaymentScheduleStatus.PENDING, 600_000);

        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, schedule));
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of());

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, pastDate);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOverdue()).isTrue();
    }

    @Test
    void 조회_날짜가_오늘이거나_미래면_연체가_아니다() {
        LocalDate futureDate = LocalDate.now().plusDays(5);
        LoanContractResponse contract = buildContract(24L, USER_ID, 2L, "생활비 대출");
        RepaymentScheduleDTO schedule = buildSchedule(futureDate, RepaymentScheduleStatus.PENDING, 600_000);

        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(contract, schedule));
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of());

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, futureDate);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOverdue()).isFalse();
    }

    @Test
    void 정산_항목의_구분_라벨은_settlementCategory_값을_그대로_사용하고_회차와_상대방은_없다() {
        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(null, List.of()));

        SettlementListResponse settlement = settlement(
                105L, "여행 정산", "OWNER", "OPEN", TARGET_DATE, LocalDateTime.now()
        );
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of(settlement));
        when(settlementPaymentStatusService.getPaymentStatus(105L, USER_ID))
                .thenReturn(paymentStatus(105L, BigDecimal.valueOf(10_000), BigDecimal.valueOf(10_000)));

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).hasSize(1);
        DashboardCalendarItemResponse item = result.get(0);
        assertThat(item.getCategoryLabel()).isEqualTo("ETC");
        assertThat(item.getInstallmentInfo()).isNull();
        assertThat(item.getCounterpartyName()).isNull();
    }

    @Test
    void 정산_항목에는_유형과_정산방식과_기간이_채워진다() {
        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(null, List.of()));

        SettlementListResponse settlement = new SettlementListResponse(
                106L, "여행 정산", "OWNER", "TRAVEL", "RECURRING", "CUSTOM",
                "OPEN", BigDecimal.valueOf(100_000), TARGET_DATE, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                LocalDateTime.now()
        );
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of(settlement));
        when(settlementPaymentStatusService.getPaymentStatus(106L, USER_ID))
                .thenReturn(paymentStatus(106L, BigDecimal.valueOf(10_000), BigDecimal.valueOf(10_000)));

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).hasSize(1);
        DashboardCalendarItemResponse item = result.get(0);
        assertThat(item.getSettlementTypeLabel()).isEqualTo("정기정산");
        assertThat(item.getSplitTypeLabel()).isEqualTo("직접입력");
        assertThat(item.getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(item.getPeriodEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void 정산_항목의_유형과_정산방식_기본값은_공동정산과_균등이다() {
        when(contractDashboardService.getIntegrationDashboardData(USER_ID))
                .thenReturn(loanData(null, List.of()));

        SettlementListResponse settlement = settlement(
                107L, "회식비 정산", "OWNER", "OPEN", TARGET_DATE, LocalDateTime.now()
        );
        when(settlementQueryService.getSettlementList(USER_ID)).thenReturn(List.of(settlement));
        when(settlementPaymentStatusService.getPaymentStatus(107L, USER_ID))
                .thenReturn(paymentStatus(107L, BigDecimal.valueOf(10_000), BigDecimal.valueOf(10_000)));

        List<DashboardCalendarItemResponse> result =
                integrationDashboardService.getCalendarDayDetail(USER_ID, TARGET_DATE);

        assertThat(result).hasSize(1);
        DashboardCalendarItemResponse item = result.get(0);
        assertThat(item.getSettlementTypeLabel()).isEqualTo("공동정산");
        assertThat(item.getSplitTypeLabel()).isEqualTo("균등");
    }
}
