package org.teamsai.saibackend.domain.contractchangedetail.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum ChangeRequestDetailErrorCode implements BaseErrorCode<DomainException> {

    INVALID_CHANGE_REQUEST_DATA(HttpStatus.INTERNAL_SERVER_ERROR, "변경 요청 데이터가 올바르지 않습니다."),
    CHANGE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "변경 요청을 찾을 수 없습니다."),
    UNKNOWN_REPAYMENT_TYPE(HttpStatus.BAD_REQUEST, "알 수 없는 상환 방식입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}