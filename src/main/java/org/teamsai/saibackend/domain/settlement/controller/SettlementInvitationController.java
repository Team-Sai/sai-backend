package org.teamsai.saibackend.domain.settlement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.settlement.dto.response.ReceivedSettlementInvitationResponse;
import org.teamsai.saibackend.domain.settlement.service.SettlementInvitationResponseService;
import org.teamsai.saibackend.domain.settlement.service.SettlementInvitationService;

import java.util.List;

@Tag(
        name = "정산 초대 API",
        description = "받은 정산 요청 조회 및 수락·거절 API"
)
@Controller
@RequiredArgsConstructor
public class SettlementInvitationController {
    private final SettlementInvitationService service;
    private final SettlementInvitationResponseService invitationResponseService;


    @Operation(
            summary = "받은 정산 초대 목록 조회",
            description = "로그인한 회원에게 도착한 대기 중인 정산 초대 목록을 최신순으로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "받은 정산 초대 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 토큰이 유효하지 않음"
            )
    })
    @ResponseBody
    @GetMapping("/api/settlement-invitations/received")
    public ResponseEntity<List<ReceivedSettlementInvitationResponse>> findReceivedInvitation(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId")
            Long userId
    ){
        List<ReceivedSettlementInvitationResponse> response = service.findReceivedInvitations(userId);

        return ResponseEntity.ok(response);
    }
    @Operation(
            summary = "정산 초대 수락",
            description = "로그인한 회원이 자신에게 도착한 정산 요청을 확인하고 수락 상태로 변경합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "정산 초대 수락 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "종료된 정산의 초대"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 토큰이 유효하지 않음"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 정산 초대를 처리할 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "정산 또는 정산 초대를 찾을 수 없음"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 처리된 정산 초대"
            )
    })
    @ResponseBody
    @PostMapping("/api/settlement-invitations/{invitationId}/accept")
    public ResponseEntity<Void> accept(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId")
            Long userId,

            @Parameter(
                    description = "수락할 정산 초대 ID",
                    example = "1"
            )
            @PathVariable
            Long invitationId
    ){
        invitationResponseService.accept(userId, invitationId);
        return ResponseEntity.noContent().build();
    }
    @Operation(
            summary = "정산 초대 거절",
            description = "로그인한 회원이 자신에게 도착한 정산 초대를 거절합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "정산 초대 거절 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 토큰이 유효하지 않음"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 정산 초대를 처리할 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "정산 초대를 찾을 수 없음"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 처리된 정산 초대"
            )
    })
    @ResponseBody
    @PostMapping("/api/settlement-invitations/{invitationId}/reject")
    public ResponseEntity<Void> reject(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId")
            Long userId,

            @Parameter(
                    description = "거절할 정산 초대 ID",
                    example = "1"
            )
            @PathVariable
            Long invitationId
    ){
        invitationResponseService.reject(userId, invitationId);
        return ResponseEntity.noContent().build();
    }

}
