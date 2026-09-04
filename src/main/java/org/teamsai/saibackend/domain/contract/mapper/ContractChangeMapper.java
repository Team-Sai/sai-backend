package org.teamsai.saibackend.domain.contract.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.contract.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contract.type.ChangeRequestStatus;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ContractChangeMapper {



    int insert(LoanContractChangeDTO contract);


    List<LoanContractChangeDTO> findByContractId(
            @Param("contractId") Long contractId
    );

    Optional<LoanContractChangeDTO> findByChangeRequestId(
            @Param("changeRequestId") Long changeRequestId
    );

    int updateStatus(
            @Param("changeRequestId") Long changeRequestId,
            @Param("status") ChangeRequestStatus status
    );

    int updateStatusWithReturnReason(
            @Param("changeRequestId") Long changeRequestId,
            @Param("status") ChangeRequestStatus status,
            @Param("returnReason") String returnReason
    );

    int updateRequesterSignature(
            @Param("changeRequestId") Long changeRequestId,
            @Param("requesterSignature") String requesterSignature
    );

    int cancelPendingUnsignedRequest(@Param("changeRequestId") Long changeRequestId);


}
