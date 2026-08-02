package org.teamsai.saibackend.domain.contractchange.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum ContractChangeErrorCode implements BaseErrorCode<DomainException> {

    CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "계약서가 존재 하지 않습니다."),
    DUPLICATE_PENDING_REQUEST(HttpStatus.CONFLICT,"이미 처리 대기 중인 변경 요청이 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException(){
        return new DomainException(httpStatus, this);
    }
}
