package org.teamsai.saibackend.domain.payment.type;

// MVP에서는 자동매칭 기반 납부 반영만 사용한다.
// MANUAL은 이후 수동매칭 기능에서 사용할 값이다.
public enum SourceType {
    AUTO_MATCH,
    MANUAL
}
