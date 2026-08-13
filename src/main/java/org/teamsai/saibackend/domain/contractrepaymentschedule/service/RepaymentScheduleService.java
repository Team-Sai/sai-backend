package org.teamsai.saibackend.domain.contractrepaymentschedule.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.event.ContractCreatedEvent;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.response.RepaymentScheduleResponse;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.response.RepaymentScheduleSummaryResponse;
import org.teamsai.saibackend.domain.contractrepaymentschedule.exception.RepaymentScheduleErrorCode;
import org.teamsai.saibackend.domain.contractrepaymentschedule.mapper.RepaymentScheduleMapper;
import org.teamsai.saibackend.domain.contractrepaymentschedule.type.RepaymentScheduleStatus;
import org.teamsai.saibackend.domain.contractrepaymentschedule.util.ScheduleGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RepaymentScheduleService {

    private final RepaymentScheduleMapper repaymentScheduleMapper;
    private final LoanContractService loanContractService;

    @EventListener
    @Transactional
    public void onContractCreated(ContractCreatedEvent event) {
        generateSchedule(event.contractId());
    }

    @Transactional
    public void generateSchedule(Long contractId) {
        LoanContractResponse contract = loanContractService.getContractForInternalUse(contractId);

        Period period = Period.between(contract.getStartDate(), contract.getMaturityDate());
        int months = period.getYears() * 12 + period.getMonths();

        if (months <= 0) {
            throw RepaymentScheduleErrorCode.INVALID_CONTRACT_PERIOD.toException();
        }

        List<RepaymentScheduleDTO> schedules = switch (contract.getRepaymentType()) {
            case EQUAL_PRINCIPAL_AND_INTEREST -> ScheduleGenerator.generateEqualPrincipalAndInterest(
                    contractId, contract.getPrincipalAmount(), contract.getInterestRate(), months, contract.getStartDate());
            case EQUAL_PRINCIPAL -> ScheduleGenerator.generateEqualPrincipal(
                    contractId, contract.getPrincipalAmount(), contract.getInterestRate(), months, contract.getStartDate());
            case BULLET_REPAYMENT -> ScheduleGenerator.generateBulletRepayment(
                    contractId, contract.getPrincipalAmount(), contract.getInterestRate(), months, contract.getStartDate());
        };

        repaymentScheduleMapper.insertAll(schedules);
    }

    public List<RepaymentScheduleDTO> getSchedule(Long contractId) {
        return repaymentScheduleMapper.findByContractId(contractId);
    }

    public Optional<RepaymentScheduleDTO> findNextPendingSchedule(Long contractId) {
        return repaymentScheduleMapper.findEarliestPendingByContractId(contractId);
    }

    @Transactional
    public void markAsPaid(Long scheduleId, LocalDateTime paidAt) {
        repaymentScheduleMapper.updateStatusToPaid(scheduleId, paidAt);
    }

    public RepaymentScheduleSummaryResponse getScheduleSummary(Long contractId, Long userId) {

        LoanContractResponse contract = loanContractService.findContract(contractId, userId);

        List<RepaymentScheduleDTO> schedules = repaymentScheduleMapper.findByContractId(contractId);

        BigDecimal totalScheduledAmount = schedules.stream()
                .map(RepaymentScheduleDTO::getTotalPaymentDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paidAmount = schedules.stream()
                .filter(s -> s.getStatus() == RepaymentScheduleStatus.PAID)
                .map(RepaymentScheduleDTO::getTotalPaymentDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingAmount = totalScheduledAmount.subtract(paidAmount);

        int paidCount = (int) schedules.stream()
                .filter(s -> s.getStatus() == RepaymentScheduleStatus.PAID)
                .count();
        int totalCount = schedules.size();

        Optional<RepaymentScheduleDTO> nextduedate = findNextPendingSchedule(contractId);
        LocalDate nextDueDate = nextduedate
                .map(RepaymentScheduleDTO::getDueDate)
                .orElse(null);

        List<RepaymentScheduleResponse> scheduleResponses = schedules.stream()
                .map(RepaymentScheduleResponse::from)
                .toList();

        return RepaymentScheduleSummaryResponse.builder()
                .creditorName(contract.getCreditorName())
                .debtorName(contract.getDebtorName())
                .totalScheduledAmount(totalScheduledAmount)
                .paidAmount(paidAmount)
                .remainingAmount(remainingAmount)
                .paidCount(paidCount)
                .totalCount(totalCount)
                .schedules(scheduleResponses)
                .nextDueDate(nextDueDate)
                .build();
    }

    @Transactional
    public void generateChangedSchedule(Long v1ContractId, Long v2ContractId) {
        List<RepaymentScheduleDTO> v1Schedules = repaymentScheduleMapper.findByContractId(v1ContractId);

        Optional<RepaymentScheduleDTO> lastPaid = v1Schedules.stream()
                .filter(s -> s.getStatus() == RepaymentScheduleStatus.PAID)
                .max(Comparator.comparing(RepaymentScheduleDTO::getSequence));

        LoanContractResponse v1 = loanContractService.getContractForInternalUse(v1ContractId);
        LoanContractResponse v2 = loanContractService.getContractForInternalUse(v2ContractId);

        BigDecimal openingPrincipal = lastPaid.map(RepaymentScheduleDTO::getRemainingPrincipal)
                .orElse(v1.getPrincipalAmount());
        LocalDate baseDate = lastPaid.map(RepaymentScheduleDTO::getDueDate)
                .orElse(v1.getStartDate());

        repaymentScheduleMapper.deletePendingByContractId(v1ContractId);

        Period period = Period.between(baseDate, v2.getMaturityDate());
        int months = period.getYears() * 12 + period.getMonths();

        if (months <= 0) {
            throw RepaymentScheduleErrorCode.INVALID_CONTRACT_PERIOD.toException();
        }

        List<RepaymentScheduleDTO> newSchedules = switch (v2.getRepaymentType()) {
            case EQUAL_PRINCIPAL_AND_INTEREST -> ScheduleGenerator.generateEqualPrincipalAndInterest(
                    v2ContractId, openingPrincipal, v2.getInterestRate(), months, baseDate);
            case EQUAL_PRINCIPAL -> ScheduleGenerator.generateEqualPrincipal(
                    v2ContractId, openingPrincipal, v2.getInterestRate(), months, baseDate);
            case BULLET_REPAYMENT -> ScheduleGenerator.generateBulletRepayment(
                    v2ContractId, openingPrincipal, v2.getInterestRate(), months, baseDate);
        };

        repaymentScheduleMapper.insertAll(newSchedules);
    }

    public RepaymentScheduleDTO getScheduleByScheduleId(Long scheduleId) {
        return repaymentScheduleMapper.findById(scheduleId)
                .orElseThrow(() -> RepaymentScheduleErrorCode.SCHEDULE_NOT_FOUND.toException());
    }

    public Map<Long, List<RepaymentScheduleDTO>> getSchedulesByContractIds(List<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return Map.of();
        }

        List<RepaymentScheduleDTO> all = repaymentScheduleMapper.findByContractIds(contractIds);
        return all.stream()
                .collect(Collectors.groupingBy(RepaymentScheduleDTO::getContractId));
    }
}