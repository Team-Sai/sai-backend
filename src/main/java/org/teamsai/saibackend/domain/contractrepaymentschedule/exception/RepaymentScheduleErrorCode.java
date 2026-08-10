package org.teamsai.saibackend.domain.contractrepaymentschedule.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum RepaymentScheduleErrorCode implements BaseErrorCode<DomainException> {

    INVALID_CONTRACT_PERIOD(HttpStatus.BAD_REQUEST, "대출 기간은 최소 1개월 이상이어야 합니다."),

    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND,"상환 스케줄을 찾을 수 없습니다"),

    SCHEDULE_NOT_PENDING(HttpStatus.CONFLICT, "이미 상환 완료되었거나 처리 불가능한 스케줄입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}