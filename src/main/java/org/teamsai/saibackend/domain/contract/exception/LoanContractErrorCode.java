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
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
