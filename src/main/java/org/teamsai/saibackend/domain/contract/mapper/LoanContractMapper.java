package org.teamsai.saibackend.domain.contract.mapper;

import jakarta.validation.constraints.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.contract.dto.LoanContractDTO;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractRequest;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.user.dto.UserDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;


@Mapper
public interface LoanContractMapper {

    void insertByContract(
            @Param("request") LoanContractRequest request,
            @Param("user") UserDTO user,
            @Param("debtor") UserDTO debtor
    );

    void updateCreditorSignature(
            @Param("contractId") Long contractId,
            @Param("signatureData") String signatureData,
            @Param("status") ContractStatus status
    );

    void updateDebtorSignature(
            @Param("contractId") Long contractId,
            @Param("debtorAddress") String debtorAddress,
            @Param("signatureData") String signatureData,
            @Param("status") ContractStatus status
    );

    Optional<LoanContractResponse> findContractById(@Param("contractId") Long contractId);

    void insertChangedContract(
            @Param("previousContractId") Long previousContractId,
            @Param("creditorId") Long creditorId,
            @Param("debtorId") Long debtorId,
            @Param("principalAmount") BigDecimal principalAmount,
            @Param("interestRate") BigDecimal interestRate,
            @Param("repaymentType") String repaymentType,
            @Param("startDate") LocalDate startDate,
            @Param("maturityDate") LocalDate maturityDate,
            @Param("repaymentDay") Integer repaymentDay,
            @Param("creditorAddress") String creditorAddress,
            @Param("debtorAddress") String debtorAddress,
            @Param("contractAlias") String contractAlias,
            @Param("terms") String terms,
            @Param("status") ContractStatus status,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

}
