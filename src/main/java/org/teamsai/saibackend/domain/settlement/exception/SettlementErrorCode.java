package org.teamsai.saibackend.domain.settlement.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;
@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements BaseErrorCode<DomainException> {

    SETTLEMENT_CREATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "정산 생성에 실패했습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
