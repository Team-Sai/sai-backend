package org.teamsai.saibackend.domain.contractdashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;
import org.teamsai.saibackend.domain.contractdashboard.dto.response.DashboardContractRowResponse;
import org.teamsai.saibackend.domain.contractdashboard.dto.response.DashboardResponse;
import org.teamsai.saibackend.domain.contractdashboard.service.DashboardService;
import org.teamsai.saibackend.domain.contractdashboard.type.ContractRole;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contractrepaymentschedule.service.RepaymentScheduleService;
import org.teamsai.saibackend.domain.contractrepaymentschedule.type.RepaymentScheduleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private LoanContractService loanContractService;

    @Mock
    private RepaymentScheduleService repaymentScheduleService;

    @InjectMocks
    private DashboardService dashboardService;

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("구버전(V1)과 미완료 계약은 목록에서 제외된다")
    void getDashboard_excludesSupersededAndIncompleteContracts() {
        LoanContractResponse v1Superseded = buildContract(10L, null, ContractStatus.COMPLETED, "생활비-구버전", 1L, 2L);
        LoanContractResponse v2Current = buildContract(11L, 10L, ContractStatus.COMPLETED, "생활비-신버전", 1L, 2L);
        LoanContractResponse incomplete = buildContract(12L, null, ContractStatus.PENDING, "미완료계약", 1L, 5L);

        when(loanContractService.findContractsByUser(USER_ID))
                .thenReturn(List.of(v1Superseded, v2Current, incomplete));
        when(repaymentScheduleService.getSchedule(11L)).thenReturn(List.of());

        DashboardResponse response = dashboardService.getDashboard(USER_ID, null, "ALL", null, 1);

        assertThat(response.getContracts()).hasSize(1);
        assertThat(response.getContracts().get(0).getContractId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("총 잔액, 이번달 낼 돈, 납부상태를 정확히 계산한다")
    void getDashboard_calculatesAmountsCorrectly() {
        LoanContractResponse borrowedContract = buildContract(20L, null, ContractStatus.COMPLETED, "차량구입", 3L, 1L);

        List<RepaymentScheduleDTO> schedules = List.of(
                buildSchedule(RepaymentScheduleStatus.PAID, 500_000, LocalDate.now().minusMonths(1)),
                buildSchedule(RepaymentScheduleStatus.PENDING, 600_000, LocalDate.now()),
                buildSchedule(RepaymentScheduleStatus.PENDING, 650_000, LocalDate.now().plusMonths(1))
        );

        when(loanContractService.findContractsByUser(USER_ID)).thenReturn(List.of(borrowedContract));
        when(repaymentScheduleService.getSchedule(20L)).thenReturn(schedules);

        DashboardResponse response = dashboardService.getDashboard(USER_ID, null, "ALL", null, 1);
        DashboardContractRowResponse row = response.getContracts().get(0);

        assertThat(row.getTotalRemainingAmount()).isEqualByComparingTo("1250000");
        assertThat(row.getThisMonthDueAmount()).isEqualByComparingTo("600000");
        assertThat(row.getPaymentStatus().name()).isEqualTo("WAITING");
        assertThat(row.getContractStatus().name()).isEqualTo("ONGOING");
    }

    @Test
    @DisplayName("빌려준 돈, 빌린 돈, 이번달 상환예정금이 정확히 합산되고 defaultFilter가 결정된다")
    void getDashboard_buildsSummaryCorrectly() {
        LoanContractResponse lentContract = buildContract(30L, null, ContractStatus.COMPLETED, "빌려준계약", 1L, 2L);
        LoanContractResponse borrowedContract = buildContract(31L, null, ContractStatus.COMPLETED, "빌린계약", 3L, 1L);

        when(loanContractService.findContractsByUser(USER_ID))
                .thenReturn(List.of(lentContract, borrowedContract));
        when(repaymentScheduleService.getSchedule(30L))
                .thenReturn(List.of(buildSchedule(RepaymentScheduleStatus.PENDING, 200_000, LocalDate.now().plusMonths(1))));
        when(repaymentScheduleService.getSchedule(31L))
                .thenReturn(List.of(buildSchedule(RepaymentScheduleStatus.PENDING, 1_250_000, LocalDate.now())));

        DashboardResponse response = dashboardService.getDashboard(USER_ID, null, "ALL", null, 1);

        assertThat(response.getSummary().getTotalContractCount()).isEqualTo(2);
        assertThat(response.getSummary().getTotalLentAmount()).isEqualByComparingTo("200000");
        assertThat(response.getSummary().getTotalBorrowedAmount()).isEqualByComparingTo("1250000");
        assertThat(response.getSummary().getThisMonthDueAmount()).isEqualByComparingTo("1250000");
        assertThat(response.getSummary().getDefaultFilter()).isEqualTo("BORROWED");
    }

    @Test
    @DisplayName("계약 별칭으로 검색하면 일치하는 계약만 반환된다")
    void getDashboard_filtersByKeyword() {
        LoanContractResponse target = buildContract(40L, null, ContractStatus.COMPLETED, "생활비 대출", 1L, 2L);
        LoanContractResponse other = buildContract(41L, null, ContractStatus.COMPLETED, "차량 구입", 1L, 3L);

        when(loanContractService.findContractsByUser(USER_ID)).thenReturn(List.of(target, other));
        when(repaymentScheduleService.getSchedule(40L)).thenReturn(List.of());
        when(repaymentScheduleService.getSchedule(41L)).thenReturn(List.of());

        DashboardResponse response = dashboardService.getDashboard(USER_ID, "생활비", "ALL", null, 1);

        assertThat(response.getContracts()).hasSize(1);
        assertThat(response.getContracts().get(0).getContractAlias()).isEqualTo("생활비 대출");
    }

    @Test
    @DisplayName("역할 필터(LENT)를 적용하면 채권자인 계약만 반환된다")
    void getDashboard_filtersByRole() {
        LoanContractResponse lent = buildContract(50L, null, ContractStatus.COMPLETED, "빌려준계약", 1L, 2L);
        LoanContractResponse borrowed = buildContract(51L, null, ContractStatus.COMPLETED, "빌린계약", 3L, 1L);

        when(loanContractService.findContractsByUser(USER_ID)).thenReturn(List.of(lent, borrowed));
        when(repaymentScheduleService.getSchedule(50L)).thenReturn(List.of());
        when(repaymentScheduleService.getSchedule(51L)).thenReturn(List.of());

        DashboardResponse response = dashboardService.getDashboard(USER_ID, null, "LENT", null, 1);

        assertThat(response.getContracts()).hasSize(1);
        assertThat(response.getContracts().get(0).getContractId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("금액 큰 순 정렬을 적용하면 잔액이 큰 계약이 먼저 온다")
    void getDashboard_sortsByAmountDesc() {
        LoanContractResponse small = buildContract(60L, null, ContractStatus.COMPLETED, "소액계약", 1L, 2L);
        LoanContractResponse large = buildContract(61L, null, ContractStatus.COMPLETED, "고액계약", 1L, 3L);

        when(loanContractService.findContractsByUser(USER_ID)).thenReturn(List.of(small, large));
        when(repaymentScheduleService.getSchedule(60L))
                .thenReturn(List.of(buildSchedule(RepaymentScheduleStatus.PENDING, 100_000, LocalDate.now())));
        when(repaymentScheduleService.getSchedule(61L))
                .thenReturn(List.of(buildSchedule(RepaymentScheduleStatus.PENDING, 9_000_000, LocalDate.now())));

        DashboardResponse response = dashboardService.getDashboard(USER_ID, null, "ALL", "AMOUNT_DESC", 1);

        assertThat(response.getContracts().get(0).getContractId()).isEqualTo(61L);
        assertThat(response.getContracts().get(1).getContractId()).isEqualTo(60L);
    }

    @Test
    @DisplayName("페이지당 5개씩, totalPages가 올림 계산된다")
    void getDashboard_paginatesCorrectly() {
        List<LoanContractResponse> sixContracts = List.of(
                buildContract(70L, null, ContractStatus.COMPLETED, "계약1", 1L, 2L),
                buildContract(71L, null, ContractStatus.COMPLETED, "계약2", 1L, 2L),
                buildContract(72L, null, ContractStatus.COMPLETED, "계약3", 1L, 2L),
                buildContract(73L, null, ContractStatus.COMPLETED, "계약4", 1L, 2L),
                buildContract(74L, null, ContractStatus.COMPLETED, "계약5", 1L, 2L),
                buildContract(75L, null, ContractStatus.COMPLETED, "계약6", 1L, 2L)
        );

        when(loanContractService.findContractsByUser(USER_ID)).thenReturn(sixContracts);
        for (long id = 70L; id <= 75L; id++) {
            when(repaymentScheduleService.getSchedule(id)).thenReturn(List.of());
        }

        DashboardResponse page1 = dashboardService.getDashboard(USER_ID, null, "ALL", null, 1);
        DashboardResponse page2 = dashboardService.getDashboard(USER_ID, null, "ALL", null, 2);

        assertThat(page1.getContracts()).hasSize(5);
        assertThat(page2.getContracts()).hasSize(1);
        assertThat(page1.getTotalPages()).isEqualTo(2);
        assertThat(page1.getTotalCount()).isEqualTo(6);
    }

    private LoanContractResponse buildContract(
            Long contractId, Long previousContractId, ContractStatus status,
            String alias, Long creditorId, Long debtorId
    ) {
        return LoanContractResponse.builder()
                .contractId(contractId)
                .previousContractId(previousContractId)
                .status(status)
                .contractAlias(alias)
                .creditorId(creditorId)
                .debtorId(debtorId)
                .principalAmount(BigDecimal.valueOf(1_000_000))
                .interestRate(BigDecimal.valueOf(5))
                .repaymentType(RepaymentMethod.EQUAL_PRINCIPAL_AND_INTEREST)
                .startDate(LocalDate.now().minusMonths(3))
                .maturityDate(LocalDate.now().plusMonths(9))
                .build();
    }

    private RepaymentScheduleDTO buildSchedule(RepaymentScheduleStatus status, long amount, LocalDate dueDate) {
        return RepaymentScheduleDTO.builder()
                .status(status)
                .totalPaymentDue(BigDecimal.valueOf(amount))
                .remainingPrincipal(BigDecimal.valueOf(amount))
                .dueDate(dueDate)
                .build();
    }

    @Test
    @DisplayName("sortType이 null이면 예외 없이 최신 계약순(contractId 내림차순)으로 처리된다")
    void getDashboard_handlesNullSortTypeSafely() {
        LoanContractResponse older = buildContract(80L, null, ContractStatus.COMPLETED, "먼저생성", 1L, 2L);
        LoanContractResponse newer = buildContract(81L, null, ContractStatus.COMPLETED, "나중생성", 1L, 2L);

        when(loanContractService.findContractsByUser(USER_ID)).thenReturn(List.of(older, newer));
        when(repaymentScheduleService.getSchedule(80L)).thenReturn(List.of());
        when(repaymentScheduleService.getSchedule(81L)).thenReturn(List.of());

        DashboardResponse response = dashboardService.getDashboard(USER_ID, null, "ALL", null, 1);

        assertThat(response.getContracts()).hasSize(2);
        assertThat(response.getContracts().get(0).getContractId()).isEqualTo(81L);
        assertThat(response.getContracts().get(1).getContractId()).isEqualTo(80L);
    }

    @Test
    @DisplayName("page가 0 이하로 들어와도 예외 없이 1페이지로 처리된다")
    void getDashboard_handlesInvalidPageSafely() {
        LoanContractResponse contract = buildContract(90L, null, ContractStatus.COMPLETED, "테스트계약", 1L, 2L);

        when(loanContractService.findContractsByUser(USER_ID)).thenReturn(List.of(contract));
        when(repaymentScheduleService.getSchedule(90L)).thenReturn(List.of());

        DashboardResponse responseZero = dashboardService.getDashboard(USER_ID, null, "ALL", null, 0);
        DashboardResponse responseNegative = dashboardService.getDashboard(USER_ID, null, "ALL", null, -5);

        assertThat(responseZero.getContracts()).hasSize(1);
        assertThat(responseNegative.getContracts()).hasSize(1);
    }

    @Test
    @DisplayName("빌려준 돈이 더 많으면 defaultFilter는 LENT다")
    void getDashboard_defaultFilterIsLentWhenLentIsGreater() {
        LoanContractResponse lentContract = buildContract(100L, null, ContractStatus.COMPLETED, "빌려준계약", 1L, 2L);
        LoanContractResponse borrowedContract = buildContract(101L, null, ContractStatus.COMPLETED, "빌린계약", 3L, 1L);

        when(loanContractService.findContractsByUser(USER_ID))
                .thenReturn(List.of(lentContract, borrowedContract));
        when(repaymentScheduleService.getSchedule(100L))
                .thenReturn(List.of(buildSchedule(RepaymentScheduleStatus.PENDING, 5_000_000, LocalDate.now())));
        when(repaymentScheduleService.getSchedule(101L))
                .thenReturn(List.of(buildSchedule(RepaymentScheduleStatus.PENDING, 100_000, LocalDate.now())));

        DashboardResponse response = dashboardService.getDashboard(USER_ID, null, "ALL", null, 1);

        assertThat(response.getSummary().getDefaultFilter()).isEqualTo("LENT");
    }

    @Test
    @DisplayName("정렬 기준(역할순/구분순/상태순/마감일순/가나다순)이 각각 정확히 적용된다")
    void getDashboard_sortsByEachCriteriaCorrectly() {
        LoanContractResponse creditorContract = buildContract(110L, null, ContractStatus.COMPLETED, "가나다1", 1L, 2L);
        LoanContractResponse debtorContract = buildContract(111L, null, ContractStatus.COMPLETED, "나다라2", 3L, 1L);

        when(loanContractService.findContractsByUser(USER_ID))
                .thenReturn(List.of(debtorContract, creditorContract));
        when(repaymentScheduleService.getSchedule(110L)).thenReturn(List.of());
        when(repaymentScheduleService.getSchedule(111L)).thenReturn(List.of());

        DashboardResponse alphabetSorted = dashboardService.getDashboard(USER_ID, null, "ALL", "ALPHABET", 1);
        assertThat(alphabetSorted.getContracts().get(0).getContractAlias()).isEqualTo("가나다1");

        DashboardResponse roleSorted = dashboardService.getDashboard(USER_ID, null, "ALL", "ROLE", 1);
        assertThat(roleSorted.getContracts().get(0).getRole()).isEqualTo(ContractRole.CREDITOR);

        DashboardResponse categorySorted = dashboardService.getDashboard(USER_ID, null, "ALL", "CATEGORY", 1);
        assertThat(categorySorted.getContracts().get(0).getCategory().name()).isEqualTo("RECEIVE");

        DashboardResponse deadlineSorted = dashboardService.getDashboard(USER_ID, null, "ALL", "DEADLINE", 1);
        assertThat(deadlineSorted.getContracts()).hasSize(2);
    }
}