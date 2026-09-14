package org.teamsai.saibackend.domain.identity.type;

public enum IdentityStatus {
    // 인증 요청 & 인증 완료는 아닌 상태
    REQUESTED,
    // 인증 성공 및 회원 정보 비교까지 완료된 상태
    VERIFIED,
    // 인증이 사용된 상태
    USED,
    // 인증 실패 또는 회원 정보 불일치 상태
    FAILED,
    // 인증 사용 가능 시간이 지난 상태
    EXPIRED
}
