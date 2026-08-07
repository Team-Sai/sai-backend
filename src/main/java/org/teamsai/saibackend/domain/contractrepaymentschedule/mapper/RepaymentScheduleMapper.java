package org.teamsai.saibackend.domain.contractrepaymentschedule.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface RepaymentScheduleMapper {

    int insertAll(
            @Param("list") List<RepaymentScheduleDTO> schedules);

    List<RepaymentScheduleDTO> findByContractId(
            @Param("contractId") Long contractID
    );

    Optional<RepaymentScheduleDTO> findEarliestPendingByContractId(
            @Param("contractId") Long contractId
    );

    int updateStatusToPaid(
            @Param("scheduleId") Long scheduleId,
            @Param("paidAt") LocalDateTime paidAt
    );


}
