package org.teamsai.saibackend.domain.contract.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractRequest;
import org.teamsai.saibackend.domain.contract.dto.response.ChangeLoanContractResponse;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;

import java.util.List;
import java.util.Optional;


@Mapper
public interface LoanContractMapper {

    void insertByContract(
            @Param("request") LoanContractRequest request,
            @Param("creditorId") Long creditorId
    );

    void updateDebtorId(
            @Param("contractId") Long contractId,
            @Param("debtorId") Long debtorId
    );

    void updateCreditorSignature(
            @Param("contractId") Long contractId,
            @Param("signatureData") String signatureData,
            @Param("debtorId") Long debtorId,
            @Param("status") ContractStatus status
    );

    void updateDebtorSignature(
            @Param("contractId") Long contractId,
            @Param("debtorAddress") String debtorAddress,
            @Param("signatureData") String signatureData,
            @Param("status") ContractStatus status
    );

    void updateChangeStatus(
            @Param("contractId") Long contractId,
            @Param("status") ContractStatus status
    );

    Optional<LoanContractResponse> findContractById(@Param("contractId") Long contractId);

    List<LoanContractResponse> findContractsByUser(@Param("userId") Long userID);

    Optional<LoanContractResponse> findPendingContractByPreviousId(@Param("previousContractId") Long previousContractId);

    void insertChangedContract(ChangeLoanContractResponse contract);

}
