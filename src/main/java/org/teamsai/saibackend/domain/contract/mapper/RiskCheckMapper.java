package org.teamsai.saibackend.domain.contract.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RiskCheckMapper {

    Long sumCompletedPrincipalByCreditor(@Param("userId") Long userId);

}
