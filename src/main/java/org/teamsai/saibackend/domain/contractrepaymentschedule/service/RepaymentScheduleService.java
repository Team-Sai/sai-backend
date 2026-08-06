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
import org.teamsai.saibackend.domain.contractrepaymentschedule.util.ScheduleGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Optional;

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
        loanContractService.findContract(contractId, userId);   // 존재확인 + 당사자검증, 한번에

        List<RepaymentScheduleDTO> schedules = repaymentScheduleMapper.findByContractId(contractId);

        BigDecimal totalScheduledAmount = schedules.stream().map(RepaymentScheduleDTO::getTotalPaymentDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paidAmount = schedules.stream().filter(s -> "PAID".equals(s.getStatus()))
                .map(RepaymentScheduleDTO::getTotalPaymentDue).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingAmount = totalScheduledAmount.subtract(paidAmount);

        int paidCount = (int) schedules.stream().filter(s -> "PAID".equals(s.getStatus())).count();
        int totalCount = schedules.size();

        List<RepaymentScheduleResponse> scheduleResponses = schedules.stream()
                .map(RepaymentScheduleResponse::from)
                .toList();

        return RepaymentScheduleSummaryResponse.builder()
                .totalScheduledAmount(totalScheduledAmount)
                .paidAmount(paidAmount)
                .remainingAmount(remainingAmount)
                .paidCount(paidCount)
                .totalCount(totalCount)
                .schedules(scheduleResponses)
                .build();
    }
}