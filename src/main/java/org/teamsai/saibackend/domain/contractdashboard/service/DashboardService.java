package org.teamsai.saibackend.domain.contractdashboard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;
import org.teamsai.saibackend.domain.contractdashboard.dto.response.DashboardContractRowResponse;
import org.teamsai.saibackend.domain.contractdashboard.dto.response.DashboardResponse;
import org.teamsai.saibackend.domain.contractdashboard.dto.response.DashboardSummaryResponse;
import org.teamsai.saibackend.domain.contractdashboard.exception.DashboardErrorCode;
import org.teamsai.saibackend.domain.contractdashboard.type.ContractRole;
import org.teamsai.saibackend.domain.contractdashboard.type.DashboardContractStatus;
import org.teamsai.saibackend.domain.contractdashboard.type.DashboardPaymentStatus;
import org.teamsai.saibackend.domain.contractdashboard.type.TransactionCategory;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contractrepaymentschedule.service.RepaymentScheduleService;
import org.teamsai.saibackend.domain.contractrepaymentschedule.type.RepaymentScheduleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final LoanContractService loanContractService;
    private final RepaymentScheduleService repaymentScheduleService;

    private record RoleDueSummary(BigDecimal amount, Integer dueMonth) {}

    private List<LoanContractResponse> getVisibleContracts(Long userId) {
        List<LoanContractResponse> contract = loanContractService.findContractsByUser(userId);

        Set<Long> supersededIds = contract.stream()
                .filter(c -> c.getPreviousContractId() != null && c.getStatus() == ContractStatus.COMPLETED)
                .map(c -> c.getPreviousContractId())
                .collect(Collectors.toSet());

        return contract.stream()
                .filter(c -> !supersededIds.contains(c.getContractId()) && c.getStatus() == ContractStatus.COMPLETED)
                .toList();
    }

    private BigDecimal calculateTotalRemaining(List<RepaymentScheduleDTO> schedules) {
        return schedules.stream()
                .filter(s -> s.getStatus().isUnresolved())
                .map(this::getRemainingPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateThisMonthDue(List<RepaymentScheduleDTO> schedules) {
        YearMonth thisMonth = YearMonth.now();
        return schedules.stream()
                .filter(s -> s.getStatus().isUnresolved()
                        && YearMonth.from(s.getDueDate()).equals(thisMonth))
                .map(this::getRemainingPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateYearMonthDue(List<RepaymentScheduleDTO> schedules, YearMonth targetMonth) {
        return schedules.stream()
                .filter(s -> s.getStatus().isUnresolved()
                        && YearMonth.from(s.getDueDate()).equals(targetMonth))
                .map(this::getRemainingPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getRemainingPaymentAmount(RepaymentScheduleDTO schedule) {
        return Optional.ofNullable(schedule.getRemainingPaymentAmount())
                .orElse(schedule.getTotalPaymentDue());
    }

    private DashboardPaymentStatus determinePaymentStatus(BigDecimal totalRemaining) {
        return totalRemaining.compareTo(BigDecimal.ZERO) == 0
                ? DashboardPaymentStatus.PAID
                : DashboardPaymentStatus.ONGOING;
    }

    private DashboardContractStatus determineContractStatus(BigDecimal totalRemaining) {
        return totalRemaining.compareTo(BigDecimal.ZERO) == 0
                ? DashboardContractStatus.COMPLETED
                : DashboardContractStatus.ONGOING;
    }

    private ContractRole determineRole(LoanContractResponse contract, Long userId) {
        return contract.getCreditorId().equals(userId)
                ? ContractRole.CREDITOR
                : ContractRole.DEBTOR;
    }

    private TransactionCategory determineCategory(ContractRole role) {
        return role == ContractRole.CREDITOR
                ? TransactionCategory.RECEIVE
                : TransactionCategory.PAY;
    }


    private DashboardContractRowResponse toRow(
            ContractScheduleContext context,
            Long userId
    ) {
        LoanContractResponse contract = context.contract();
        List<RepaymentScheduleDTO> schedules = context.schedules();


        BigDecimal totalRemaining = calculateTotalRemaining(schedules);
        BigDecimal thisMonthDue = calculateThisMonthDue(schedules);

        ContractRole role = determineRole(contract, userId);
        TransactionCategory category = determineCategory(role);
        DashboardContractStatus contractStatus = determineContractStatus(totalRemaining);
        DashboardPaymentStatus paymentStatus = determinePaymentStatus(totalRemaining);
        Optional<RepaymentScheduleDTO> nearestSchedule = findNearestSchedule(schedules);
        LocalDate nearestDueDate = nearestSchedule.map(RepaymentScheduleDTO::getDueDate).orElse(null);
        BigDecimal nextDueAmount = nearestSchedule
                .map(this::getRemainingPaymentAmount)
                .orElse(null);
        return DashboardContractRowResponse.builder()
                .contractId(contract.getContractId())
                .contractAlias(contract.getContractAlias())
                .role(role)
                .category(category)
                .principalAmount(contract.getPrincipalAmount())
                .totalRemainingAmount(totalRemaining)
                .thisMonthDueAmount(thisMonthDue)
                .contractStatus(contractStatus)
                .paymentStatus(paymentStatus)
                .maturityDate(contract.getMaturityDate())
                .nearestScheduleDueDate(nearestDueDate)
                .nextDueAmount(nextDueAmount)
                .createdAt(contract.getCreatedAt())
                .build();
    }

    private RoleDueSummary calculateRoleDueSummary(List<DashboardContractRowResponse> roleRows, Map<Long, List<RepaymentScheduleDTO>> scheduleMap) {
        BigDecimal thisMonthDue = roleRows.stream()
                .map(DashboardContractRowResponse::getThisMonthDueAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate nearestDueDate = roleRows.stream()
                .filter(row -> row.getNearestScheduleDueDate() != null)
                .map(DashboardContractRowResponse::getNearestScheduleDueDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        if (nearestDueDate == null) {
            return new RoleDueSummary(thisMonthDue, null);
        }

        YearMonth targetMonth = YearMonth.from(nearestDueDate);
        boolean isCurrentMonth = targetMonth.equals(YearMonth.now());

        if (thisMonthDue.compareTo(BigDecimal.ZERO) == 0 && !isCurrentMonth) {
            BigDecimal yearMonthDue = roleRows.stream()
                    .map(row -> calculateYearMonthDue(
                            scheduleMap.getOrDefault(row.getContractId(), List.of()), targetMonth))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return new RoleDueSummary(yearMonthDue, targetMonth.getMonthValue());
        }

        return new RoleDueSummary(thisMonthDue, null);
    }

    private DashboardSummaryResponse buildSummary(List<DashboardContractRowResponse> rows, Map<Long, List<RepaymentScheduleDTO>> scheduleMap) {
        int totalContractCount = rows.size();

        BigDecimal totalLentAmount = rows.stream()
                .filter(c -> c.getRole() == ContractRole.CREDITOR)
                .map(DashboardContractRowResponse::getTotalRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBorrowedAmount = rows.stream()
                .filter(c -> c.getRole() == ContractRole.DEBTOR)
                .map(DashboardContractRowResponse::getTotalRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String defaultFilter = totalLentAmount.compareTo(totalBorrowedAmount) >= 0 ? "LENT" : "BORROWED";

        LocalDate nearestDueDate = rows.stream()
                .filter(s -> s.getNearestScheduleDueDate() != null)
                .map(DashboardContractRowResponse::getNearestScheduleDueDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        RoleDueSummary allSummary = calculateRoleDueSummary(rows, scheduleMap);

        List<DashboardContractRowResponse> creditorRows = rows.stream()
                .filter(row -> row.getRole() == ContractRole.CREDITOR)
                .toList();
        List<DashboardContractRowResponse> debtorRows = rows.stream()
                .filter(row -> row.getRole() == ContractRole.DEBTOR)
                .toList();

        RoleDueSummary receivableSummary = calculateRoleDueSummary(creditorRows, scheduleMap);
        RoleDueSummary payableSummary = calculateRoleDueSummary(debtorRows, scheduleMap);

        return DashboardSummaryResponse.builder()
                .totalContractCount(totalContractCount)
                .totalLentAmount(totalLentAmount)
                .totalBorrowedAmount(totalBorrowedAmount)
                .nearestDueDate(nearestDueDate)
                .defaultFilter(defaultFilter)
                .thisMonthDueAmount(allSummary.amount())
                .dueMonth(allSummary.dueMonth())
                .receivableThisMonthAmount(receivableSummary.amount())
                .receivableDueMonth(receivableSummary.dueMonth())
                .payableThisMonthAmount(payableSummary.amount())
                .payableDueMonth(payableSummary.dueMonth())
                .build();
    }

    private List<DashboardContractRowResponse> filterByKeyword(List<DashboardContractRowResponse> rows, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return rows;
        }
        String lowerKeyword = keyword.toLowerCase();

        return rows.stream()
                .filter(c -> c.getContractAlias() != null &&
                        c.getContractAlias().toLowerCase().contains(lowerKeyword))
                .toList();
    }

    private List<DashboardContractRowResponse> filterByRole(List<DashboardContractRowResponse> rows, String filterType) {
        if (filterType == null || filterType.equals("ALL")) {
            return rows;
        } else if (filterType.equals("LENT")) {
            return rows.stream()
                    .filter(c -> c.getRole() == ContractRole.CREDITOR)
                    .toList();
        } else if (filterType.equals("BORROWED")) {
            return rows.stream()
                    .filter(c -> c.getRole() == ContractRole.DEBTOR)
                    .toList();
        }
        throw DashboardErrorCode.INVALID_ROLE_FILTER.toException();
    }

    private List<DashboardContractRowResponse> sortRows(List<DashboardContractRowResponse> rows, String sortType) {
        List<DashboardContractRowResponse> sorted = new ArrayList<>(rows);

        if (sortType == null || sortType.isEmpty()) {
            sorted.sort(Comparator.comparing(DashboardContractRowResponse::getContractId).reversed());
            return sorted;
        }

        switch (sortType) {
            case "ALPHABET" -> sorted.sort(Comparator.comparing(DashboardContractRowResponse::getContractAlias));
            case "ROLE" -> sorted.sort(Comparator.comparing(DashboardContractRowResponse::getRole));
            case "CATEGORY" -> sorted.sort(Comparator.comparing(DashboardContractRowResponse::getCategory));
            case "AMOUNT_DESC" -> sorted.sort(Comparator.comparing(DashboardContractRowResponse::getTotalRemainingAmount).reversed());
            case "AMOUNT_ASC" -> sorted.sort(Comparator.comparing(DashboardContractRowResponse::getTotalRemainingAmount));
            case "STATUS" -> sorted.sort(Comparator.comparing(DashboardContractRowResponse::getContractStatus));
            case "DEADLINE" -> sorted.sort(Comparator.comparing(DashboardContractRowResponse::getMaturityDate));
            case "CREATED_DESC" -> sorted.sort(Comparator.comparing(
                    DashboardContractRowResponse::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())
            ));
            default -> throw DashboardErrorCode.INVALID_SORT_TYPE.toException();
        }
        return sorted;
    }

    private List<DashboardContractRowResponse> paginate(List<DashboardContractRowResponse> rows, int page, int pageSize) {
        if (page < 1) {
            page = 1;
        }

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, rows.size());

        if (startIndex >= rows.size()) {
            return List.of();
        }
        return rows.subList(startIndex, endIndex);
    }

    private List<ContractScheduleContext> getContractScheduleContexts(Long userId) {
        List<LoanContractResponse> contracts = getVisibleContracts(userId);
        List<Long> contractIds = contracts.stream()
                .map(LoanContractResponse::getContractId)
                .toList();
        Map<Long, List<RepaymentScheduleDTO>> scheduleMap =
                repaymentScheduleService.getSchedulesByContractIds(contractIds);

        return contracts.stream()
                .map(contract -> new ContractScheduleContext(
                        contract,
                        scheduleMap.getOrDefault(contract.getContractId(), List.of())
                ))
                .toList();
    }

    private DashboardResponse buildDashboard(
            List<ContractScheduleContext> contexts,
            Long userId,
            String keyword,
            String roleFilter,
            String sortType,
            int page
    ) {
        Map<Long, List<RepaymentScheduleDTO>> scheduleMap = contexts.stream()
                .collect(Collectors.toMap(
                        context -> context.contract().getContractId(),
                        ContractScheduleContext::schedules
                ));

        List<DashboardContractRowResponse> allRows = contexts.stream()
                .map(context -> toRow(context, userId)).toList();

        DashboardSummaryResponse summary = buildSummary(allRows, scheduleMap);
        List<DashboardContractRowResponse> filtered = filterByKeyword(allRows, keyword);
        List<DashboardContractRowResponse> roleFiltered = filterByRole(filtered, roleFilter);
        List<DashboardContractRowResponse> sorted = sortRows(roleFiltered, sortType);
        long totalCount = sorted.size();
        List<DashboardContractRowResponse> pagedRows = paginate(sorted, page, 5);
        int totalPages = (int) Math.ceil((double) totalCount / 5);

        return DashboardResponse.builder()
                .summary(summary)
                .contracts(pagedRows)
                .currentPage(page)
                .totalPages(totalPages)
                .totalCount(totalCount)
                .pageSize(5)
                .build();
    }

    private Optional<RepaymentScheduleDTO> findNearestSchedule(List<RepaymentScheduleDTO> schedules) {
        return schedules.stream()
                .filter(s -> s.getStatus() == RepaymentScheduleStatus.PENDING)
                .min(Comparator.comparing(RepaymentScheduleDTO::getDueDate));
    }

    public DashboardResponse getDashboard(Long userId, String keyword, String roleFilter, String sortType, int page) {
        return buildDashboard(
                getContractScheduleContexts(userId),
                userId,
                keyword,
                roleFilter,
                sortType,
                page
        );
    }

    public IntegrationDashboardData getIntegrationDashboardData(Long userId) {
        List<ContractScheduleContext> contexts = getContractScheduleContexts(userId);
        DashboardResponse dashboard = buildDashboard(
                contexts,
                userId,
                null,
                "ALL",
                "CREATED_DESC",
                1
        );
        List<LoanScheduleContext> loanSchedules = contexts.stream()
                .flatMap(context -> context.schedules().stream()
                        .map(schedule -> new LoanScheduleContext(context.contract(), schedule)))
                .toList();
        return new IntegrationDashboardData(dashboard, loanSchedules);
    }

    private record ContractScheduleContext(
            LoanContractResponse contract,
            List<RepaymentScheduleDTO> schedules
    ) {
    }

    public record LoanScheduleContext(
            LoanContractResponse contract,
            RepaymentScheduleDTO schedule
    ) {
    }

    public record IntegrationDashboardData(
            DashboardResponse dashboard,
            List<LoanScheduleContext> loanSchedules
    ) {
    }

}
