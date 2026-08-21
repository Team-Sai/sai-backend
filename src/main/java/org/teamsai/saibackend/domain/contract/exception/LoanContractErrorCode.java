package org.teamsai.saibackend.domain.contract.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum LoanContractErrorCode implements BaseErrorCode<DomainException> {

    CONTRACT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당 차용증 계약서를 찾을 수 없습니다."
    ),

    DEBTOR_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당 이메일로 가입된 채무자를 찾을 수 없습니다."
    ),

    CONTRACT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "해당 차용증에 접근할 권한이 없습니다."
    ),

    CANNOT_CREATE_CONTRACT_TO_SELF(
            HttpStatus.FORBIDDEN,
            "채무자와 채권자는 동일인이 될 수 없습니다."
    ),

    DEBTOR_ALREADY_LINKED(
            HttpStatus.CONFLICT,
            "이미 채무자가 연결된 계약서입니다."
    ),

    DEBTOR_ADDRESS_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "채무자 주소를 입력해야 합니다."
    ),

    INVALID_LINKED_ACCOUNT(
            HttpStatus.BAD_REQUEST,
            "본인 명의로 연동된 활성 계좌만 선택할 수 있습니다."
    ),

    CONTRACT_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당 차용증에 연결된 계좌가 없습니다."
    ),

    CONTRACT_ALREADY_COMPLETED(
            HttpStatus.CONFLICT,
            "이미 완료된 계약입니다."
    ),

    NOT_A_CHANGE_CONTRACT(
            HttpStatus.BAD_REQUEST,
            "계약 변경 승인 대상이 아닌 계약입니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
