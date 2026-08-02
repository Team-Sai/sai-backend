package org.teamsai.saibackend.domain.account.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;

import java.util.List;

@Mapper
public interface LinkedBankAccountMapper {

    void insertBatch(@Param("list") List<LinkedBankAccountDTO> list);

    List<LinkedBankAccountDTO> findByUserId(@Param("userId") Long userId);

    List<LinkedBankAccountDTO> selectLinkedAccountsByUserId(@Param("userId") Long userId);
}
