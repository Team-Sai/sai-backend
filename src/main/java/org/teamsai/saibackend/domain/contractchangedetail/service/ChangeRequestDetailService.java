package org.teamsai.saibackend.domain.contractchangedetail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
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

    private final ContractChangeService contractChangeService;

    public ChangeRequestDetailDTO getDetail(Long contractId, Long changeRequestId, Long userId) {

        LoanContractChangeDTO changeDTO = contractChangeService.getChangeRequest(changeRequestId);

        if(!changeDTO.getContractId().equals(contractId)) {
            throw ChangeRequestDetailErrorCode.CHANGE_REQUEST_NOT_FOUND.toException();
        }

        if(changeDTO.getNewInterestRate() == null) {
            throw ChangeRequestDetailErrorCode.INVALID_CHANGE_REQUEST_DATA.toException();
        }

        LoanContractResponse contract = contractChangeService.getContract(contractId, userId);

        if(!changeDTO.getUserId().equals(contract.getCreditorId())) {
            throw ChangeRequestDetailErrorCode.CHANGE_REQUEST_NOT_FOUND.toException();
        }



        BigDecimal currentMonthlyPayment = RepaymentCalculator.calculate(
                contract.getPrincipalAmount(),
                contract.getInterestRate(),
                contract.getRepaymentType().getDescription(),
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

        int extendedMonths = Period.between(contract.getMaturityDate(), changeDTO.getNewMaturityDate()).getMonths()
                + Period.between(contract.getMaturityDate(), changeDTO.getNewMaturityDate()).getYears() * 12;

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
                .newRepaymentType(changeDTO.getNewRepaymentType())
                .changeReason(changeDTO.getChangeReason())
                .extendedMonths(extendedMonths)
                .currentMonthlyPayment(currentMonthlyPayment)
                .newMonthlyPayment(newMonthlyPayment)
                .build();
    }

    private String translateStatus(String status) {
        return switch (status) {
            case "PENDING" -> "승인 대기 중";
            case "APPROVED" -> "승인됨";
            case "REJECTED" -> "거절됨";
            default -> status;
        };


    }
}
