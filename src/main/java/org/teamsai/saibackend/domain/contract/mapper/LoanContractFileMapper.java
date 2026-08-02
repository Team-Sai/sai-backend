package org.teamsai.saibackend.domain.contract.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.teamsai.saibackend.domain.contract.dto.LoanContractFileDTO;

import java.util.Optional;

@Mapper
public interface LoanContractFileMapper {

    //파일 최초 저장
    void insertContractFile(LoanContractFileDTO fileDTO);

    //저장된 파일 불러오기
    Optional<LoanContractFileDTO> findFileByContractId(Long contractId);
}
