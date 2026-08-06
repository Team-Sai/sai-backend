package org.teamsai.saibackend.domain.account.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode implements BaseErrorCode<DomainException> {

    ACCOUNT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "본인이 연동한 계좌만 조회할 수 있습니다."
    ),
    BANK_SERVER_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "사이은행 서버에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해주세요."
    ),
    INVALID_BANK_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "은행으로부터 올바르지 않은 응답을 받았습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
