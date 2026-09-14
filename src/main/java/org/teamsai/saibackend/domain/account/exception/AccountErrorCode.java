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
    LINKED_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "연동된 계좌를 찾을 수 없습니다."
    ),
    INVALID_BANK_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "은행으로부터 올바르지 않은 응답을 받았습니다."
    ),
    LOCAL_KEY_SAVE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "userKey 저장에 실패했습니다. 잠시 후 다시 시도해주세요."
    ),
    USER_KEY_ALREADY_LINKED(
            HttpStatus.CONFLICT,
            "이미 다른 요청에서 userKey가 연동되었습니다. 다시 시도해주세요."
    );
    private final HttpStatus httpStatus;
    private final String message;
    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
