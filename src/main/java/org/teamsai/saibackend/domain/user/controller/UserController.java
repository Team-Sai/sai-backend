package org.teamsai.saibackend.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.teamsai.saibackend.domain.user.dto.response.UserResponse;
import org.teamsai.saibackend.domain.user.service.UserService;

@Tag(
        name = "회원 정보 API",
        description = "마이페이지 조회 및 회원 탈퇴 등 회원 정보 관리 API"
)
@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/mypage")
    public String myPage() {
        return "user/mypage";
    }

    @Operation(
            summary = "내 정보 조회",
            description = "현재 로그인한 회원의 프로필 및 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 회원 정보"
            )
    })
    @ResponseBody
    @GetMapping("/api/users/me")
    public UserResponse getMyInfo(
            @Parameter(hidden = true)
            Authentication authentication
    ) {
        String userToken = authentication.getName();

        return userService.getMyInfo(userToken);
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "현재 로그인한 회원의 계정을 삭제(탈퇴) 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "회원 탈퇴 완료 (응답 바디 없음)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            )
    })
    @ResponseBody
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/users/me")
    public void withdraw(
            @Parameter(hidden = true)
            Authentication authentication
    ) {
        String userToken = authentication.getName();

        userService.withdraw(userToken);
    }
}