package org.teamsai.saibackend.domain.contractchange.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;

import java.util.List;

@Mapper
public interface ContractChangeMapper {



    int insert(LoanContractChangeDTO contract);


    List<LoanContractChangeDTO> findByContractId(
            @Param("contractId") Long contractId
    );


}
