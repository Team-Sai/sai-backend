package org.teamsai.saibackend.domain.payment.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements BaseErrorCode<DomainException> {

    PAYMENT_OBLIGATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "납부의무를 찾을 수 없습니다."
    ),

    PAYMENT_OBLIGATION_NOT_ACTIVE(
            HttpStatus.CONFLICT,
            "활성 상태의 납부의무가 아닙니다."
    ),

    INVALID_SETTLEMENT_BANK_TRANSACTION_ID(
            HttpStatus.BAD_REQUEST,
            "은행 거래 ID가 올바르지 않습니다."
    ),

    DUPLICATE_PAYMENT_RECORD(
            HttpStatus.CONFLICT,
            "이미 반영된 은행 거래입니다."
    ),

    INVALID_PAYMENT_AMOUNT(
            HttpStatus.BAD_REQUEST,
            "납부 금액이 올바르지 않습니다."
    ),

    INVALID_PAYMENT_SOURCE_TYPE(
            HttpStatus.BAD_REQUEST,
            "납부 출처가 올바르지 않습니다."
    ),

    PAYMENT_AMOUNT_EXCEEDS_REMAINING_AMOUNT(
            HttpStatus.BAD_REQUEST,
            "납부 금액이 남은 금액을 초과합니다."
    ),

    PAYMENT_RECORD_CREATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "납부 기록 생성에 실패했습니다."
    ),

    PAYMENT_STATUS_UPDATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "납부 상태 변경에 실패했습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
