package org.teamsai.saibackend.domain.contract.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum LoanContractFileErrorCode implements BaseErrorCode<DomainException> {

    SIGNATURE_UPLOAD_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "서명 파일 저장에 실패했습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
