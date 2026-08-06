package org.teamsai.saibackend.domain.settlement.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;
@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements BaseErrorCode<DomainException> {

    SETTLEMENT_CREATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "정산 생성에 실패했습니다."
    ),
    SETTLEMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 정산입니다."
    ),

    SETTLEMENT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "해당 정산에 대한 접근 권한이 없습니다."
    ),

    ALREADY_CLOSED_SETTLEMENT(
            HttpStatus.BAD_REQUEST,
            "이미 종료된 정산입니다."
    ),

    ALREADY_SETTLEMENT_PARTICIPANT(
            HttpStatus.CONFLICT,
            "이미 참여 중인 회원입니다."
    ),

    DUPLICATE_SETTLEMENT_INVITATION(
            HttpStatus.CONFLICT,
            "이미 대기 중인 초대가 존재합니다."
    ),

    SETTLEMENT_INVITATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 정산 초대입니다."
    ),

    INVITATION_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "해당 초대를 처리할 권한이 없습니다."
    ),

    INVITATION_ALREADY_PROCESSED(
            HttpStatus.CONFLICT,
            "이미 처리된 초대입니다."
    ),

    SETTLEMENT_INVITATION_CREATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "정산 초대 생성에 실패했습니다."
    ),
    SETTLEMENT_PARTICIPANT_CREATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "정산 참여자 등록에 실패했습니다."
    ),
    INVALID_SETTLEMENT_AMOUNT(
            HttpStatus.BAD_REQUEST,
            "정산 금액은 1원 이상이어야 합니다."
    ),

    DUPLICATE_SETTLEMENT_INVITEE(
            HttpStatus.CONFLICT,
            "동일한 사용자를 중복으로 선택할 수 없습니다."
    ),
    INVALID_SETTLEMENT_REQUEST(
            HttpStatus.BAD_REQUEST,
            "정산 생성 요청이 올바르지 않습니다."
    ),

    SETTLEMENT_INVITEE_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "정산 납부자를 한 명 이상 선택해야 합니다."
    ),

    INVALID_SETTLEMENT_INVITEE(
            HttpStatus.BAD_REQUEST,
            "초대할 사용자 정보가 올바르지 않습니다."
    ),
    SETTLEMENT_PARTICIPANT_STATUS_UPDATE_FAILED(
            HttpStatus.BAD_REQUEST,
            "참여자 상태 변경에 실패했습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
