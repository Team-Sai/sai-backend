package org.teamsai.saibackend.domain.contractchange.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum ContractChangeErrorCode implements BaseErrorCode<DomainException> {

    NOT_CONTRACT_PARTY(HttpStatus.FORBIDDEN, "계약 변경 요청은 로그인된 사용자만 요청할 수 있습니다."),
    CONTRACT_NOT_COMPLETED(HttpStatus.BAD_REQUEST,"완료된 계약만 변경 요청 할 수 있습니다."),
    DUPLICATE_PENDING_REQUEST(HttpStatus.CONFLICT, "이미 처리 대기 중인 변경 요청이 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException(){
        return new DomainException(httpStatus, this);
    }
}
