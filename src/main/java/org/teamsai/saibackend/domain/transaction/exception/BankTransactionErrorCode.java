package org.teamsai.saibackend.domain.transaction.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum BankTransactionErrorCode implements BaseErrorCode<DomainException> {

    BANK_TRANSACTION_CREATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "은행 거래 생성에 실패했습니다."
    ),

    BANK_TRANSACTION_STATUS_UPDATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "은행 거래 처리 상태 변경에 실패했습니다."
    ),

    BANK_TRANSACTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "은행 거래를 찾을 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
