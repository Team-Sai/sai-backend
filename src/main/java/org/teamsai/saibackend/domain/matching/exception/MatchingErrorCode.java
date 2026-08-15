package org.teamsai.saibackend.domain.matching.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum MatchingErrorCode
        implements BaseErrorCode<DomainException> {

    INVALID_MATCHING_REQUEST(
            HttpStatus.BAD_REQUEST,
            "매칭 요청값이 올바르지 않습니다."
    ),

    MATCHING_TARGET_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "매칭 대상을 찾을 수 없습니다."
    ),

    MATCHING_CANDIDATE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "매칭 후보를 찾을 수 없습니다."
    ),

    MATCHING_CANDIDATE_SAVE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "매칭 후보 저장에 실패했습니다."
    ),

    MATCHING_REVIEW_NOT_REQUIRED(
            HttpStatus.CONFLICT,
            "확인이 필요한 은행 거래가 아닙니다."
    ),

    ALREADY_MATCHED(
            HttpStatus.CONFLICT,
            "이미 매칭된 항목입니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
