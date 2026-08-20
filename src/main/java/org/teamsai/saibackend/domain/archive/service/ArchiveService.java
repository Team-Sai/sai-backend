package org.teamsai.saibackend.domain.archive.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.archive.dto.ArchiveStatus;
import org.teamsai.saibackend.domain.archive.dto.FileDTO;
import org.teamsai.saibackend.domain.archive.mapper.ArchiveMapper;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveService {

    private final ArchiveMapper archiveMapper;
    private final TemplateEngine templateEngine;

    @Getter
    @Value("${file.upload-dir}")
    private String uploadDir;

    public List<FileDTO> findFilesByReference(String domainType, Long referenceId) {
        return archiveMapper.findFilesByReference(domainType, referenceId);
    }

    public List<FileDTO> findAllFilesByUserId(Long userId) {
        return archiveMapper.findAllFilesByUserId(userId);
    }

    @Transactional
    public FileDTO saveFile(String domainType, Long referenceId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 존재하지 않습니다.");
        }

        try {
            return saveFile(domainType, referenceId, file.getOriginalFilename(), file.getContentType(),
                    file.getInputStream(), file.getSize());
        } catch (IOException e) {
            log.error("파일 저장 중 오류 발생", e);
            throw new RuntimeException("파일 저장 처리 중 오류가 발생했습니다.", e);
        }
    }

    @Transactional
    public FileDTO saveFile(String domainType, Long referenceId, String originalFilename, String contentType,
                             InputStream content, long fileSize) {
        try {
            Path dirPath = Paths.get(uploadDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            }

            String savedFilename = domainType + "_" + referenceId + "_" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

            Path savePath = dirPath.resolve(savedFilename);
            Files.copy(content, savePath, StandardCopyOption.REPLACE_EXISTING);

            FileDTO fileDTO = FileDTO.builder()
                    .domainType(ArchiveStatus.valueOf(domainType))
                    .referenceId(referenceId)
                    .originalFilename(originalFilename)
                    .savedFilename(savedFilename)
                    .fileSize(fileSize)
                    .fileType(contentType)
                    .createdAt(LocalDateTime.now())
                    .build();

            archiveMapper.insertFile(fileDTO);

            log.info("[File Saved] Domain: {}, RefId: {}, Original: {} -> Saved: {}",
                    domainType, referenceId, originalFilename, savedFilename);

            return fileDTO;

        } catch (IOException e) {
            log.error("파일 저장 중 오류 발생", e);
            throw new RuntimeException("파일 저장 처리 중 오류가 발생했습니다.", e);
        }
    }

    public byte[] renderContractPdf(LoanContractResponse contract) {
        String pdfCss;
        try (InputStream cssStream = getClass().getResourceAsStream("/static/css/archive/contract-pdf.css")) {
            pdfCss = StreamUtils.copyToString(cssStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("PDF 스타일시트 로딩 중 오류가 발생했습니다.", e);
        }

        Context context = new Context();
        context.setVariable("contract", contract);
        context.setVariable("repaymentTypeLabel", contract.getRepaymentType().getDescription());
        context.setVariable("pdfCss", pdfCss);
        context.setVariable("creditorSignatureDataUri", loadSignatureDataUri(contract.getCreditorSignature()));
        context.setVariable("debtorSignatureDataUri", loadSignatureDataUri(contract.getDebtorSignature()));

        String html = templateEngine.process("archive/contract-pdf", context);

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

            builder.useDefaultPageSize(210, 297, BaseRendererBuilder.PageSizeUnits.MM);
            builder.withHtmlContent(html, "");
            builder.toStream(pdfBuffer);
            builder.run();

        } catch (Exception e) {
            log.error("PDF 생성 실패 - contractId: {}", contract.getContractId(), e);
            throw new RuntimeException("PDF 생성 중 오류가 발생했습니다.", e);
        }

        return pdfBuffer.toByteArray();
    }

    private String loadSignatureDataUri(String savedFilename) {
        if (savedFilename == null || savedFilename.isBlank()) {
            return null;
        }

        try {
            Path filePath = Paths.get(uploadDir).resolve(savedFilename).normalize();
            byte[] bytes = Files.readAllBytes(filePath);
            String mimeType = savedFilename.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.warn("서명 이미지 로딩 실패 - fileName: {}", savedFilename, e);
            return null;
        }
    }

    public FileDTO getFileById(Long fileId) {
        return archiveMapper.findFileById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 파일입니다. fileId=" + fileId));
    }


    public Resource loadFileAsResource(String savedFilename) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(savedFilename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                log.error("파일을 찾을 수 없거나 읽을 수 없습니다: {}", filePath);
                throw new RuntimeException("파일을 찾을 수 없거나 읽을 수 없습니다.");
            }
        } catch (MalformedURLException e) {
            log.error("파일 경로 해석 오류: {}", savedFilename, e);
            throw new RuntimeException("파일 경로 해석 중 오류가 발생했습니다.", e);
        }
    }
}
