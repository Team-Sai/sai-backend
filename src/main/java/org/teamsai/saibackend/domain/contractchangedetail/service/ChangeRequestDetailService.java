package org.teamsai.saibackend.domain.contractchangedetail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;
import org.teamsai.saibackend.domain.contractchange.type.ChangeRequestStatus;
import org.teamsai.saibackend.domain.contractchangedetail.dto.ChangeRequestDetailDTO;
import org.teamsai.saibackend.domain.contractchangedetail.exception.ChangeRequestDetailErrorCode;
import org.teamsai.saibackend.domain.contractchangedetail.util.RepaymentCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

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

        if (!changeDTO.getUserId().equals(contract.getCreditorId())) {
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

        return ChangeRequestDetailDTO.builder()
                .changeRequestId(changeDTO.getChangeRequestId())
                .requesterName(contract.getCreditorName())
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
                .currentMonthlyPayment(currentMonthlyPayment)
                .newMonthlyPayment(newMonthlyPayment)
                .build();
    }



    private String translateStatus(ChangeRequestStatus status) {
        return switch (status) {
            case PENDING -> "승인 대기 중";
            case APPROVED -> "승인됨";
            case REJECTED -> "거절됨";

        };


    }
}
