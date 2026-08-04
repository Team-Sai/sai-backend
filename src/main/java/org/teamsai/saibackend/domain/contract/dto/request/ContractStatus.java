package org.teamsai.saibackend.domain.contract.dto.request;

public enum ContractStatus {
    DRAFT, // 최초 생성 시 기본 상태
    PENDING, // 전송 후 서명 기다리는 상태
    COMPLETED // 채권자와 채무자 둘 다 서명 완료 후 저장
}