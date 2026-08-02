package org.teamsai.saibackend.domain.contract.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.contract.dto.LoanContractDTO;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractRequest;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.user.dto.UserDTO;

import java.util.Optional;


@Mapper
public interface LoanContractMapper {

    void insertByContract(
            @Param("request") LoanContractRequest request,
            @Param("user") UserDTO user,
            @Param("debtor") UserDTO debtor
    );

    void updateContractStatus(
            @Param("contractId") Long contractId,
            @Param("status") ContractStatus status
    );

    void updateCreditorSignature(
            @Param("contractId") Long contractId,
            @Param("signaturePath") String signaturePath,
            @Param("status") ContractStatus status
    );

    void updateDebtorSignature(
            @Param("contractId") Long contractId,
            @Param("signaturePath") String signaturePath,
            @Param("status") ContractStatus status
    );

    Optional<LoanContractResponse> findContractById(@Param("contractId") Long contractId);


}
