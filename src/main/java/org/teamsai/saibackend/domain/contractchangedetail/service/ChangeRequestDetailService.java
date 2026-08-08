package org.teamsai.saibackend.domain.contractchangedetail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contractchange.type.ChangeRequestStatus;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;
import org.teamsai.saibackend.domain.contractchangedetail.dto.ChangeRequestDetailDTO;
import org.teamsai.saibackend.domain.contractchangedetail.exception.ChangeRequestDetailErrorCode;
import org.teamsai.saibackend.domain.contractchangedetail.util.RepaymentCalculator;

import java.math.BigDecimal;
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

        if(!changeDTO.getContractId().equals(contractId)) {
            throw ChangeRequestDetailErrorCode.CHANGE_REQUEST_NOT_FOUND.toException();
        }

        if(changeDTO.getNewInterestRate() == null
        || changeDTO.getNewRepaymentType() == null
        || changeDTO.getNewRepaymentDate() == null
        || changeDTO.getNewMaturityDate() == null) {
            throw ChangeRequestDetailErrorCode.INVALID_CHANGE_REQUEST_DATA.toException();
        }



        if(!changeDTO.getUserId().equals(contract.getCreditorId())) {
            throw ChangeRequestDetailErrorCode.CHANGE_REQUEST_NOT_FOUND.toException();
        }




        BigDecimal currentMonthlyPayment = RepaymentCalculator.calculate(
                contract.getPrincipalAmount(),
                contract.getInterestRate(),
                contract.getRepaymentType().name(),
                contract.getStartDate(),
                contract.getMaturityDate()
        );

        BigDecimal newMonthlyPayment = RepaymentCalculator.calculate(
                contract.getPrincipalAmount(),
                changeDTO.getNewInterestRate(),
                changeDTO.getNewRepaymentType(),
                contract.getStartDate(),
                changeDTO.getNewMaturityDate()
        );

        Period period = Period.between(contract.getMaturityDate(), changeDTO.getNewMaturityDate());
        int extendedMonths = period.getMonths() + period.getYears() * 12;

        return ChangeRequestDetailDTO.builder()
                .changeRequestId(changeDTO.getChangeRequestId())
                .requesterName(contract.getCreditorName())
                .requestedAt(changeDTO.getCreatedAt())
                .status(translateStatus(changeDTO.getStatus()))
                .currentMaturityDate(contract.getMaturityDate())
                .currentInterestRate(contract.getInterestRate())
                .currentRepaymentType(contract.getRepaymentType().getDescription())
                .newMaturityDate(changeDTO.getNewMaturityDate())
                .newInterestRate(changeDTO.getNewInterestRate())
                .newRepaymentType(translateRepaymentType(changeDTO.getNewRepaymentType()))
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
