package org.teamsai.saibackend.domain.contractrepaymentschedule.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum RepaymentScheduleErrorCode implements BaseErrorCode<DomainException> {

    INVALID_CONTRACT_PERIOD(HttpStatus.BAD_REQUEST, "대출 기간은 최소 1개월 이상이어야 합니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}