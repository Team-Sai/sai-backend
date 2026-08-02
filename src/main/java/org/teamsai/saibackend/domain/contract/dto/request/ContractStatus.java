package org.teamsai.saibackend.domain.contract.dto.request;

public enum ContractStatus {
    DRAFT, // 임시저장
    PENDING, // 상대방에게 전송해서 대기중
    COMPLETED // 채권자와 채무자 둘 다 서명 완료 후 저장
}