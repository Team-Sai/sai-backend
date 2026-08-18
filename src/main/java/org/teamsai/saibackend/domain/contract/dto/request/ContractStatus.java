package org.teamsai.saibackend.domain.contract.dto.request;

public enum ContractStatus {
    DRAFT, // 최초 생성 시 기본 상태
    PENDING, // 전송 후 서명 기다리는 상태
    COMPLETED, // 채권자와 채무자 둘 다 서명 완료 후 저장
    SUPERSEDED,      // 조건 변경으로 인해 신규 계약(V2)으로 대체된 구버전 계약
    TERMINATED,      // 계약 기간 만료 또는 중도 해지된 계약
    CHANGE_REJECTED // 계약 변경 요청 반려 상태
}