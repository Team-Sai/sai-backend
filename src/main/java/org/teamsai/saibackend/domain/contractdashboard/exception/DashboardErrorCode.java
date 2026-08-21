package org.teamsai.saibackend.domain.contractdashboard.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum DashboardErrorCode implements BaseErrorCode<DomainException> {
    INVALID_ROLE_FILTER(HttpStatus.BAD_REQUEST, "유효하지 않은 역할 필터입니다."),
    INVALID_STATUS_FILTER(HttpStatus.BAD_REQUEST, "유효하지 않은 차용증 상태 필터입니다."),
    INVALID_SORT_TYPE(HttpStatus.BAD_REQUEST, "유효하지 않은 정렬 기준입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
