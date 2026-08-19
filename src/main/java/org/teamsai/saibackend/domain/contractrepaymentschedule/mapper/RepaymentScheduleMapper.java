package org.teamsai.saibackend.domain.contractrepaymentschedule.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface RepaymentScheduleMapper {

    List<Long> findWriteOffCandidateIds(@Param("cutoffDate") LocalDate cutoffDate);

    int writeOffBulk(@Param("scheduleIds") List<Long> scheduleIds);

    List<RepaymentScheduleDTO> findDueOnDates(@Param("dueDates") List<LocalDate> dueDates);

    Long findDebtorUserIdByContractId(@Param("contractId") Long contractId);

    int deletePendingByContractId(@Param("contractId") Long contractId);

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


    List<RepaymentScheduleDTO> findByContractIds(
            @Param("contractIds") List<Long> contractIds
    );

    Optional<RepaymentScheduleDTO> findById(@Param ("scheduleId")Long scheduleId);
}
