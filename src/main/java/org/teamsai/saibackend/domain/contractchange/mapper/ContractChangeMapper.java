package org.teamsai.saibackend.domain.contractchange.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.springframework.security.core.parameters.P;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;

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


}
