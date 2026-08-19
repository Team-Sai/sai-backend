package org.teamsai.saibackend.domain.archive.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.teamsai.saibackend.domain.archive.dto.ArchiveStatus;
import org.teamsai.saibackend.domain.archive.dto.FileDTO;
import org.teamsai.saibackend.domain.archive.mapper.ArchiveMapper;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementAccountResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementArchivePreviewResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementDetailResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentHistoryResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentStatusResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.service.SettlementAccountService;
import org.teamsai.saibackend.domain.settlement.service.SettlementPaymentHistoryService;
import org.teamsai.saibackend.domain.settlement.service.SettlementPaymentStatusService;
import org.teamsai.saibackend.domain.settlement.service.SettlementQueryService;
import org.teamsai.saibackend.global.exception.DomainException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementArchiveService {

    private static final String DOCUMENT_VERSION = "v1";
    private static final String SETTLEMENT_DISPLAY_ID_PREFIX = "ST-";

    private final TemplateEngine templateEngine;
    private final ArchiveMapper archiveMapper;
    private final SettlementQueryService settlementQueryService;
    private final SettlementPaymentStatusService settlementPaymentStatusService;
    private final SettlementPaymentHistoryService settlementPaymentHistoryService;
    private final SettlementAccountService settlementAccountService;

    @Getter
    @Value("${file.upload-dir}")
    private String uploadDir;

    public byte[] generateSettlementPdfBytes(Long settlementId, Long userId) {

        SettlementDetailResponse detail = settlementQueryService.getSettlementDetail(settlementId, userId);
        boolean closed = "CLOSED".equals(detail.settlementStatus());

        if (closed) {
            Optional<byte[]> existingPdf = findExistingPdf(settlementId);
            if (existingPdf.isPresent()) {
                return existingPdf.get();
            }
        }

        byte[] pdfBytes = renderSettlementPdf(settlementId, userId, detail);
        
        if (closed) {
            saveGeneratedPdf(settlementId, pdfBytes);
        }

        return pdfBytes;
    }

    public SettlementArchivePreviewResponse getArchivePreview(Long settlementId, Long userId) {

        SettlementDetailResponse detail = settlementQueryService.getSettlementDetail(settlementId, userId);
        ArchiveData data = gatherArchiveData(settlementId, userId, detail);

        return SettlementArchivePreviewResponse.builder()
                .settlementId(detail.settlementId())
                .settlementDisplayId(data.settlementDisplayId())
                .title(detail.title())
                .ownerName(data.archiveDetail().ownerName())
                .settlementType(detail.settlementType())
                .settlementCategory(detail.settlementCategory())
                .settlementStatus(detail.settlementStatus())
                .splitType(detail.splitType())
                .dueDate(detail.dueDate())
                .createdAt(detail.createdAt())
                .paymentStatus(data.paymentStatus())
                .paymentHistory(data.paymentHistory())
                .settlementAccount(data.settlementAccount().orElse(null))
                .documentVersion(DOCUMENT_VERSION)
                .build();
    }

    private byte[] renderSettlementPdf(Long settlementId, Long userId, SettlementDetailResponse detail) {

        ArchiveData data = gatherArchiveData(settlementId, userId, detail);

        String pdfCss = loadPdfCss();
        LocalDateTime generatedAt = LocalDateTime.now();

        Context context = new Context();
        context.setVariable("archiveDetail", data.archiveDetail());
        context.setVariable("detail", detail);
        context.setVariable("paymentStatus", data.paymentStatus());
        context.setVariable("paymentHistory", data.paymentHistory());
        data.settlementAccount().ifPresent(account -> context.setVariable("settlementAccount", account));
        context.setVariable("pdfCss", pdfCss);
        context.setVariable("generatedAt", generatedAt);
        context.setVariable("dataAsOfAt", generatedAt);
        context.setVariable("documentVersion", DOCUMENT_VERSION);
        context.setVariable("settlementDisplayId", data.settlementDisplayId());

        String html = templateEngine.process("archive/settlement-pdf", context);

        ByteArrayOutputStream pdfBuffer = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();


            builder.useFont(
                    () -> getClass().getResourceAsStream("/static/font/pretendard/Pretendard-Regular.ttf"),
                    "Pretendard", 400, BaseRendererBuilder.FontStyle.NORMAL, true
            );
            builder.useFont(
                    () -> getClass().getResourceAsStream("/static/font/pretendard/Pretendard-Bold.ttf"),
                    "Pretendard", 700, BaseRendererBuilder.FontStyle.NORMAL, true
            );

            builder.useDefaultPageSize(210, 297, BaseRendererBuilder.PageSizeUnits.MM); // A4 사이즈
            builder.withHtmlContent(html, "");
            builder.toStream(pdfBuffer);
            builder.run();

        } catch (Exception e) {
            log.error("정산 PDF 생성 실패 - settlementId: {}", settlementId, e);
            throw new RuntimeException("정산 PDF 생성 중 오류가 발생했습니다.", e);
        }

        return pdfBuffer.toByteArray();
    }

    private Optional<byte[]> findExistingPdf(Long settlementId) {
        List<FileDTO> savedFiles = archiveMapper.findFilesByReference(
                ArchiveStatus.SETTLEMENT.name(),
                settlementId
        );

        if (savedFiles.isEmpty()) {
            return Optional.empty();
        }

        FileDTO latestFile = savedFiles.get(0);
        try {
            Path filePath = Paths.get(uploadDir).resolve(latestFile.getSavedFilename());
            return Optional.of(Files.readAllBytes(filePath));
        } catch (IOException e) {
            log.warn("저장된 정산 PDF 로딩 실패 - settlementId: {}, savedFilename: {}", settlementId, latestFile.getSavedFilename(), e);
            return Optional.empty();
        }
    }

    private void saveGeneratedPdf(Long settlementId, byte[] pdfBytes) {
        try {
            Path dirPath = Paths.get(uploadDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String savedFilename = ArchiveStatus.SETTLEMENT.name() + "_" + settlementId + "_" + UUID.randomUUID() + ".pdf";
            Files.write(dirPath.resolve(savedFilename), pdfBytes);

            FileDTO fileDTO = FileDTO.builder()
                    .domainType(ArchiveStatus.SETTLEMENT)
                    .referenceId(settlementId)
                    .originalFilename("정산_" + settlementId + ".pdf")
                    .savedFilename(savedFilename)
                    .fileSize((long) pdfBytes.length)
                    .fileType("application/pdf")
                    .createdAt(LocalDateTime.now())
                    .build();

            archiveMapper.insertFile(fileDTO);

            log.info("[정산 PDF Saved] settlementId: {} -> {}", settlementId, savedFilename);
        } catch (IOException e) {
            log.error("정산 PDF 저장 중 오류 발생 - settlementId: {}", settlementId, e);
            throw new RuntimeException("정산 PDF 저장 처리 중 오류가 발생했습니다.", e);
        }
    }

    private ArchiveData gatherArchiveData(Long settlementId, Long userId, SettlementDetailResponse archiveDetail) {
        SettlementPaymentStatusResponse paymentStatus = settlementPaymentStatusService.getPaymentStatus(settlementId, userId);
        List<SettlementPaymentHistoryResponse> paymentHistory = settlementPaymentHistoryService.getPaymentHistory(settlementId, userId);
        Optional<SettlementAccountResponse> settlementAccount = findSettlementAccountIfExists(settlementId, userId);

        return new ArchiveData(
                archiveDetail,
                paymentStatus,
                paymentHistory,
                settlementAccount,
                SETTLEMENT_DISPLAY_ID_PREFIX + settlementId
        );
    }

    private record ArchiveData(
            SettlementDetailResponse archiveDetail,
            SettlementPaymentStatusResponse paymentStatus,
            List<SettlementPaymentHistoryResponse> paymentHistory,
            Optional<SettlementAccountResponse> settlementAccount,
            String settlementDisplayId
    ) {
    }

    private Optional<SettlementAccountResponse> findSettlementAccountIfExists(Long settlementId, Long userId) {
        try {
            return Optional.of(settlementAccountService.findCurrentAccount(userId, settlementId));
        } catch (DomainException e) {
            if (e.getErrorCode() == SettlementErrorCode.SETTLEMENT_ACCOUNT_NOT_FOUND) {
                log.warn("정산 수취 계좌 미설정 - settlementId: {}", settlementId);
                return Optional.empty();
            }
            throw e;
        }
    }

    private String loadPdfCss() {
        try (InputStream cssStream = getClass().getResourceAsStream("/static/css/archive/settlement-pdf.css")) {
            return StreamUtils.copyToString(cssStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("정산 PDF 스타일시트 로딩 중 오류가 발생했습니다.", e);
        }
    }
}