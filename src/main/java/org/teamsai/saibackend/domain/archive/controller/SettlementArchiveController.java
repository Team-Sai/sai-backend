package org.teamsai.saibackend.domain.archive.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.archive.service.SettlementArchiveService;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementArchivePreviewResponse;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(
        name = "정산 아카이브 API",
        description = "정산 이행현황 PDF 다운로드 API"
)
@Slf4j
@Controller
@RequiredArgsConstructor
public class SettlementArchiveController {

    private final SettlementArchiveService settlementArchiveService;

    @Hidden
    @GetMapping("/archive/settlements/{settlementId}")
    public String archivePreviewPage(
            @PathVariable Long settlementId,
            Model model
    ) {
        model.addAttribute("settlementId", settlementId);
        return "archive/settlement-detail";
    }

    @Operation(
            summary = "정산 아카이브 미리보기 조회",
            description = "PDF로 내려받기 전, PDF에 담길 정산 기본정보·이행현황·참여자별 납부 현황·상세 납부 내역·수취 계좌 정보를 화면에서 미리 확인합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "해당 정산에 대한 접근 권한이 없음"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 정산")
    })
    @ResponseBody
    @GetMapping("/api/settlements/{settlementId}/archive-preview")
    public SettlementArchivePreviewResponse getArchivePreview(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId")
            Long userId,

            @PathVariable
            Long settlementId
    ) {
        return settlementArchiveService.getArchivePreview(settlementId, userId);
    }

    @Operation(
            summary = "정산 이행현황 PDF 다운로드",
            description = "정산 기본정보, 납부 현황, 참여자별 납부 내역, 수취 계좌 정보를 담은 PDF를 내려줍니다. " +
                    "종료된 정산은 최초 생성된 PDF를 재사용하고, 진행 중인 정산은 요청마다 최신 이행현황으로 새로 생성합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PDF 다운로드 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "해당 정산에 대한 접근 권한이 없음"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 정산"),
            @ApiResponse(responseCode = "500", description = "PDF 생성 실패 등 서버 내부 오류")
    })
    @GetMapping("/api/settlements/{settlementId}/pdf")
    public void generateSettlementPdf(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId")
            Long userId,

            @PathVariable
            Long settlementId,

            HttpServletResponse response
    ) throws Exception {

        byte[] pdfBytes = settlementArchiveService.generateSettlementPdfBytes(settlementId, userId);

        String fileName = URLEncoder.encode("정산_" + settlementId + ".pdf", StandardCharsets.UTF_8)
                .replace("+", "%20");

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        try (OutputStream os = response.getOutputStream()) {
            os.write(pdfBytes);
        }
    }
}
