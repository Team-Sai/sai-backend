package org.teamsai.saibackend.domain.contractchange.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractReadDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ContractChangeMapper {

    // LoanService 완성되면 이 메서드는 지우고 LoanService.getContract(id) 호출로 교체
    Optional<LoanContractReadDTO> findById(
            @Param("contractId") Long contractId
    );

    int insert(LoanContractChangeDTO contract);


    List<LoanContractChangeDTO> findByContractId(
            @Param("contractId") Long contractId
    );


}
