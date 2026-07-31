package org.teamsai.saibackend.domain.contract.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.teamsai.saibackend.domain.contract.dto.LoanContractDTO;
import org.teamsai.saibackend.domain.contract.entity.LoanContractEntity;
import org.teamsai.saibackend.domain.contract.entity.LoanContractStatus;


@Mapper
public interface LoanContractMapper {
    //차용증 내용입력
    int insertContract(LoanContractEntity contract);

    //계약서 입력 시 채권자 user_id로 사용자 정보(이름, 생년월일)만 조회
    LoanContractDTO selectCreditorInfoById(Long creditorId);

    //계약서 입력 시 채무자 user_id로 사용자 정보(이름, 생년월일)만 조회
    LoanContractDTO selectDebtorInfoById(Long debtorId);

    //차용증 상태 변경 (임시저장 -> 대기중 -> 완료)
    int updateContractStatus(Long contractId, LoanContractStatus status);

    //임시저장된 차용증 내용 수정
    int updateContract(LoanContractEntity contract);


    //차용증 단건 조회
    LoanContractEntity selectContractWithUserById(Long contractId);

    //본인인증된 사용자 정보를 불러오기
    //UserEntity selectUserById(int userId);


}
