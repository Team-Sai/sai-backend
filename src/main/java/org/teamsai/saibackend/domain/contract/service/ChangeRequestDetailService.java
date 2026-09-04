package org.teamsai.saibackend.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contract.service.ContractChangeService;
import org.teamsai.saibackend.domain.contract.type.ChangeRequestStatus;
import org.teamsai.saibackend.domain.contract.dto.ChangeRequestDetailDTO;
import org.teamsai.saibackend.domain.contract.exception.ChangeRequestDetailErrorCode;
import org.teamsai.saibackend.domain.contract.util.RepaymentCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChangeRequestDetailService {

    private String translateRepaymentType(String repaymentType) {
                return switch(repaymentType) {
                    case "EQUAL_PRINCIPAL_AND_INTEREST" -> "원리금균등상환";
                    case "EQUAL_PRINCIPAL" -> "원금균등상환";
                    case "BULLET_REPAYMENT" -> "만기일시상환";
                    default -> repaymentType;
                };
    }

    private final ContractChangeService contractChangeService;

    public ChangeRequestDetailDTO getDetail(Long contractId, Long changeRequestId, Long userId) {

        LoanContractResponse contract = contractChangeService.getContract(contractId, userId);
        LoanContractChangeDTO changeDTO = contractChangeService.getChangeRequest(changeRequestId);

        if (!changeDTO.getContractId().equals(contractId)) {
            throw ChangeRequestDetailErrorCode.CHANGE_REQUEST_NOT_FOUND.toException();
        }

        boolean requesterIsParty = Objects.equals(changeDTO.getUserId(), contract.getCreditorId())
                || Objects.equals(changeDTO.getUserId(), contract.getDebtorId());
        if (!requesterIsParty) {
            throw ChangeRequestDetailErrorCode.CHANGE_REQUEST_NOT_FOUND.toException();
        }

        BigDecimal effectiveInterestRate = changeDTO.getNewInterestRate() != null
                ? changeDTO.getNewInterestRate() : contract.getInterestRate();
        String effectiveRepaymentType = changeDTO.getNewRepaymentType() != null
                ? changeDTO.getNewRepaymentType() : contract.getRepaymentType().name();
        LocalDate effectiveMaturityDate = changeDTO.getNewMaturityDate() != null
                ? changeDTO.getNewMaturityDate() : contract.getMaturityDate();
        String effectiveTerms = changeDTO.getNewTerms() != null
                ? changeDTO.getNewTerms() : contract.getTerms();

        BigDecimal currentMonthlyPayment = RepaymentCalculator.calculate(
                contract.getPrincipalAmount(),
                contract.getInterestRate(),
                contract.getRepaymentType().name(),
                contract.getStartDate(),
                contract.getMaturityDate()
        );

        BigDecimal newMonthlyPayment = RepaymentCalculator.calculate(
                contract.getPrincipalAmount(),
                effectiveInterestRate,
                effectiveRepaymentType,
                contract.getStartDate(),
                effectiveMaturityDate
        );

        Period period = Period.between(contract.getMaturityDate(), effectiveMaturityDate);
        int extendedMonths = period.getMonths() + period.getYears() * 12;

        Long newContractId = changeDTO.getStatus() == ChangeRequestStatus.PENDING
                ? contractChangeService.getPendingChangedContractId(contractId)
                : null;

        return ChangeRequestDetailDTO.builder()
                .changeRequestId(changeDTO.getChangeRequestId())
                .newContractId(newContractId)
                .requesterName(Objects.equals(changeDTO.getUserId(), contract.getCreditorId())
                        ? contract.getCreditorName()
                        : contract.getDebtorName())
                .rejectorName(Objects.equals(changeDTO.getUserId(), contract.getCreditorId())
                        ? contract.getDebtorName()
                        : contract.getCreditorName())
                .requestedAt(changeDTO.getCreatedAt())
                .status(translateStatus(changeDTO.getStatus()))
                .currentMaturityDate(contract.getMaturityDate())
                .currentInterestRate(contract.getInterestRate())
                .currentRepaymentType(contract.getRepaymentType().getDescription())
                .currentTerms(contract.getTerms())
                .newMaturityDate(effectiveMaturityDate)
                .newInterestRate(effectiveInterestRate)
                .newRepaymentType(translateRepaymentType(effectiveRepaymentType))
                .newTerms(effectiveTerms)
                .changeReason(changeDTO.getChangeReason())
                .extendedMonths(extendedMonths)
                .returnReason(changeDTO.getReturnReason())
                .currentMonthlyPayment(currentMonthlyPayment)
                .newMonthlyPayment(newMonthlyPayment)
                .build();
    }



    private String translateStatus(ChangeRequestStatus status) {
        return switch (status) {
            case PENDING -> "승인 대기 중";
            case APPROVED -> "승인됨";
            case REJECTED -> "거절됨";
            case CANCELLED -> "취소됨";

        };


    }
}
