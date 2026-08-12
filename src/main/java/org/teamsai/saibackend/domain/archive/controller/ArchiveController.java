package org.teamsai.saibackend.domain.archive.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.teamsai.saibackend.domain.archive.dto.ArchiveStatus;
import org.teamsai.saibackend.domain.archive.dto.FileDTO;
import org.teamsai.saibackend.domain.archive.service.ArchiveService;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;
    private final LoanContractService loanContractService;

    @GetMapping("/archive")
    public String archivePage() {
        return "archive/archive";
    }

    @Operation(
            summary = "대출 계약서 PDF 다운로드",
            description = "계약이 완료되어 저장된 차용증 PDF가 있으면 그 파일을 내려주고, 없으면(아직 진행 중인 계약) 즉시 생성해서 내려줍니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PDF 다운로드 성공"),
            @ApiResponse(responseCode = "404", description = "계약 정보를 찾을 수 없거나 권한 없음"),
            @ApiResponse(responseCode = "500", description = "PDF 생성 실패 등 서버 내부 오류")
    })
    @GetMapping("/api/contracts/{contractId}/pdf")
    public void generateContractPdf(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            HttpServletResponse response
    ) throws Exception {

        LoanContractResponse contract = loanContractService.findContract(contractId, userId);

        List<FileDTO> savedFiles = archiveService.findFilesByReference(ArchiveStatus.CONTRACT.name(), contractId);

        byte[] pdfBytes;
        if (!savedFiles.isEmpty()) {
            FileDTO latestFile = savedFiles.get(0);
            Resource resource = archiveService.loadFileAsResource(latestFile.getSavedFilename());
            try (InputStream is = resource.getInputStream()) {
                pdfBytes = StreamUtils.copyToByteArray(is);
            }
        } else {
            pdfBytes = archiveService.renderContractPdf(contract);
        }

        String fileName = URLEncoder.encode("차용증_" + contractId + ".pdf", StandardCharsets.UTF_8)
                .replace("+", "%20");

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        try (OutputStream os = response.getOutputStream()) {
            os.write(pdfBytes);
        }
    }
}
