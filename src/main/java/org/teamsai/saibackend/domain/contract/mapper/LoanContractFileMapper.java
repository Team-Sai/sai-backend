package org.teamsai.saibackend.domain.contract.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.teamsai.saibackend.domain.contract.dto.LoanContractFileDTO;

import java.util.Optional;

@Mapper
public interface LoanContractFileMapper {


    void insertContractFile(LoanContractFileDTO fileDTO);

    Optional<LoanContractFileDTO> findFileByContractId(Long contractId);
}
