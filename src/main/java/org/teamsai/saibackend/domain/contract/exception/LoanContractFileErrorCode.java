package org.teamsai.saibackend.domain.contract.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum LoanContractFileErrorCode implements BaseErrorCode<DomainException> {

    CONTRACT_FILE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당 계약서의 첨부파일을 찾을 수 없습니다."
    ),

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
