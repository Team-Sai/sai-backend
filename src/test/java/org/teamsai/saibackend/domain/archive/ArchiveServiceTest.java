package org.teamsai.saibackend.domain.archive;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.archive.dto.ArchiveStatus;
import org.teamsai.saibackend.domain.archive.dto.FileDTO;
import org.teamsai.saibackend.domain.archive.mapper.ArchiveMapper;
import org.teamsai.saibackend.domain.archive.service.ArchiveService;
import org.teamsai.saibackend.domain.archive.service.HtmlToPdfRenderer;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArchiveService 단위 테스트")
class ArchiveServiceTest {

    private static final String DOMAIN_TYPE = "CONTRACT";
    private static final Long REFERENCE_ID = 1L;
    private static final Long FILE_ID = 1L;

    @Mock
    private ArchiveMapper archiveMapper;

    @InjectMocks
    private ArchiveService archiveService;

    private Path savedPath;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(archiveService, "uploadDir", tempDir.toString());
    }

    @AfterEach
    void cleanUp() throws IOException {
        if (savedPath != null) {
            Files.deleteIfExists(savedPath);
            savedPath = null;
        }
    }

    @Nested
    @DisplayName("파일 저장")
    class SaveFile {

        @Test
        @DisplayName("파일을 디스크에 저장하고 메타데이터를 기록한다")
        void saveFileSuccess() throws IOException {
            MultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", "file-bytes".getBytes()
            );

            FileDTO result = archiveService.saveFile(DOMAIN_TYPE, REFERENCE_ID, file);
            savedPath = Path.of(archiveService.getUploadDir()).resolve(result.getSavedFilename());

            assertThat(savedPath).exists();
            assertThat(Files.readAllBytes(savedPath)).isEqualTo("file-bytes".getBytes());
            assertThat(result.getSavedFilename()).startsWith(DOMAIN_TYPE + "_" + REFERENCE_ID + "_");
            assertThat(result.getSavedFilename()).endsWith(".pdf");
            assertThat(result.getOriginalFilename()).isEqualTo("contract.pdf");
            assertThat(result.getDomainType()).isEqualTo(ArchiveStatus.CONTRACT);

            verify(archiveMapper).insertFile(result);
        }

        @Test
        @DisplayName("파일이 없으면 예외가 발생하고 저장하지 않는다")
        void saveFileFailsWhenFileIsEmpty() {
            MultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);

            assertThatThrownBy(() -> archiveService.saveFile(DOMAIN_TYPE, REFERENCE_ID, emptyFile))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(archiveMapper, org.mockito.Mockito.never()).insertFile(any());
        }

        @Test
        @DisplayName("파일 저장 중 입출력 오류가 발생하면 예외가 발생한다")
        void saveFileFailsOnIOException() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            given(file.isEmpty()).willReturn(false);
            given(file.getOriginalFilename()).willReturn("contract.pdf");
            given(file.getInputStream()).willThrow(new IOException("disk error"));

            assertThatThrownBy(() -> archiveService.saveFile(DOMAIN_TYPE, REFERENCE_ID, file))
                    .isInstanceOf(RuntimeException.class);

            verify(archiveMapper, org.mockito.Mockito.never()).insertFile(any());
        }
    }

    @Nested
    @DisplayName("파일 조회")
    class FindFiles {

        @Test
        @DisplayName("도메인 타입과 참조 ID로 파일 목록을 조회한다")
        void findFilesByReferenceSuccess() {
            List<FileDTO> files = List.of(createFile());
            given(archiveMapper.findFilesByReference(DOMAIN_TYPE, REFERENCE_ID)).willReturn(files);

            List<FileDTO> result = archiveService.findFilesByReference(DOMAIN_TYPE, REFERENCE_ID);

            assertThat(result).isEqualTo(files);
        }

        @Test
        @DisplayName("사용자 ID로 파일 목록을 조회한다")
        void findAllFilesByUserIdSuccess() {
            List<FileDTO> files = List.of(createFile());
            given(archiveMapper.findAllFilesByUserId(REFERENCE_ID)).willReturn(files);

            List<FileDTO> result = archiveService.findAllFilesByUserId(REFERENCE_ID);

            assertThat(result).isEqualTo(files);
        }

        @Test
        @DisplayName("파일 ID로 조회해 파일 DTO를 반환한다")
        void getFileByIdSuccess() {
            FileDTO file = createFile();
            given(archiveMapper.findFileById(FILE_ID)).willReturn(Optional.of(file));

            FileDTO result = archiveService.getFileById(FILE_ID);

            assertThat(result).isEqualTo(file);
        }

        @Test
        @DisplayName("파일을 찾을 수 없으면 예외가 발생한다")
        void getFileByIdFailsWhenNotFound() {
            given(archiveMapper.findFileById(FILE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> archiveService.getFileById(FILE_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("계약서 PDF 생성")
    class RenderContractPdf {

        @BeforeEach
        void setUpTemplateEngine() {
            ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/");
            resolver.setSuffix(".html");
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCharacterEncoding("UTF-8");
            resolver.setCacheable(false);

            SpringTemplateEngine templateEngine = new SpringTemplateEngine();
            templateEngine.setTemplateResolver(resolver);

            ReflectionTestUtils.setField(archiveService, "templateEngine", templateEngine);
            ReflectionTestUtils.setField(archiveService, "htmlToPdfRenderer", new HtmlToPdfRenderer());
        }

        @Test
        @DisplayName("계약 정보를 채워 PDF 바이트를 생성한다")
        void renderContractPdfSuccess() throws IOException {
            LoanContractResponse contract = createContract(RepaymentMethod.EQUAL_PRINCIPAL_AND_INTEREST);

            byte[] pdfBytes = archiveService.renderContractPdf(contract);

            assertThat(pdfBytes).isNotEmpty();
            assertThat(new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

            try (PDDocument document = PDDocument.load(pdfBytes)) {
                String text = new PDFTextStripper().getText(document);
                assertThat(text).contains(contract.getCreditorName());
                assertThat(text).contains(contract.getDebtorName());
                assertThat(text).contains(RepaymentMethod.EQUAL_PRINCIPAL_AND_INTEREST.getDescription());
            }
        }

        @Test
        @DisplayName("상환 방식이 바뀌면 PDF 내용에도 반영된다")
        void renderContractPdfReflectsRepaymentType() throws IOException {
            LoanContractResponse contract = createContract(RepaymentMethod.BULLET_REPAYMENT);

            byte[] pdfBytes = archiveService.renderContractPdf(contract);

            try (PDDocument document = PDDocument.load(pdfBytes)) {
                String text = new PDFTextStripper().getText(document);
                assertThat(text).contains(RepaymentMethod.BULLET_REPAYMENT.getDescription());
            }
        }

        @Test
        @DisplayName("알파 채널이 없는 서명 이미지는 흰 배경만 투명 처리되고 획만 빨갛게 남는다")
        void recolorsAlphaLessSignatureKeepingOnlyStrokeVisible() throws Exception {
            BufferedImage opaque = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = opaque.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 4, 4);
            g.setColor(Color.BLACK);
            g.fillRect(1, 1, 1, 1);
            g.dispose();

            byte[] pngBytes = ReflectionTestUtils.invokeMethod(archiveService, "recolorToSealRed", opaque);
            BufferedImage result = ImageIO.read(new ByteArrayInputStream(pngBytes));

            int bgArgb = result.getRGB(0, 0);
            int strokeArgb = result.getRGB(1, 1);

            assertThat((bgArgb >>> 24) & 0xFF).isEqualTo(0);
            assertThat((strokeArgb >>> 24) & 0xFF).isEqualTo(255);
            assertThat(strokeArgb & 0xFFFFFF).isEqualTo(0xC0272D);
        }

        @Test
        @DisplayName("알파 채널이 있는 일반 서명 이미지는 투명도를 유지한 채 획만 빨갛게 바뀐다")
        void recolorsAlphaSignatureToSealRedPreservingTransparency() throws Exception {
            BufferedImage transparent = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = transparent.createGraphics();
            // 배경은 그대로 두어 완전 투명 유지
            g.setColor(new Color(0x18, 0x1c, 0x1e, 255));
            g.fillRect(1, 1, 1, 1); // 서명 획(불투명 검정)
            g.dispose();

            byte[] pngBytes = ReflectionTestUtils.invokeMethod(archiveService, "recolorToSealRed", transparent);
            BufferedImage result = ImageIO.read(new ByteArrayInputStream(pngBytes));

            int bgArgb = result.getRGB(0, 0);
            int strokeArgb = result.getRGB(1, 1);

            assertThat((bgArgb >>> 24) & 0xFF).isEqualTo(0);
            assertThat((strokeArgb >>> 24) & 0xFF).isEqualTo(255);
            assertThat(strokeArgb & 0xFFFFFF).isEqualTo(0xC0272D);
        }

        @Test
        @DisplayName("loadSignatureDataUri는 디스크의 서명 파일을 읽어 빨간색 data URI로 변환한다")
        void loadSignatureDataUriProducesRedSealDataUri() throws Exception {
            BufferedImage transparent = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = transparent.createGraphics();
            g.setColor(new Color(0x18, 0x1c, 0x1e, 255));
            g.fillRect(0, 0, 2, 2);
            g.dispose();

            String savedFilename = "sig_" + UUID.randomUUID() + ".png";
            ImageIO.write(transparent, "png", tempDir.resolve(savedFilename).toFile());

            String dataUri = ReflectionTestUtils.invokeMethod(archiveService, "loadSignatureDataUri", savedFilename);

            assertThat(dataUri).startsWith("data:image/png;base64,");

            byte[] decodedPng = java.util.Base64.getDecoder()
                    .decode(dataUri.substring("data:image/png;base64,".length()));
            BufferedImage result = ImageIO.read(new ByteArrayInputStream(decodedPng));

            assertThat(result.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xC0272D);
        }

        private LoanContractResponse createContract(RepaymentMethod repaymentMethod) {
            return LoanContractResponse.builder()
                    .contractId(1L)
                    .creditorName("김채권")
                    .creditorBirthDate("1980-01-01")
                    .creditorAddress("서울시 강남구")
                    .debtorName("이채무")
                    .debtorBirthDate("1990-05-05")
                    .debtorAddress("서울시 서초구")
                    .principalAmount(new BigDecimal("10000000"))
                    .interestRate(new BigDecimal("5.0"))
                    .repaymentType(repaymentMethod)
                    .startDate(LocalDate.of(2026, 1, 1))
                    .maturityDate(LocalDate.of(2027, 1, 1))
                    .repaymentDay(25)
                    .contractAlias("전세자금 대여")
                    .terms("특약 없음")
                    .build();
        }
    }

    @Nested
    @DisplayName("파일 리소스 로드")
    class LoadFileAsResource {

        @Test
        @DisplayName("저장된 파일을 리소스로 반환한다")
        void loadFileAsResourceSuccess() throws IOException {
            String savedFilename = "CONTRACT_1_test.pdf";
            Path filePath = tempDir.resolve(savedFilename);
            Files.write(filePath, "file-bytes".getBytes());

            Resource resource = archiveService.loadFileAsResource(savedFilename);

            assertThat(resource.exists()).isTrue();
            assertThat(resource.isReadable()).isTrue();
            assertThat(resource.getFile().toPath()).isEqualTo(filePath);
        }

        @Test
        @DisplayName("파일이 존재하지 않으면 예외가 발생한다")
        void loadFileAsResourceFailsWhenNotFound() {
            assertThatThrownBy(() -> archiveService.loadFileAsResource("not-exists.pdf"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    private FileDTO createFile() {
        return FileDTO.builder()
                .fileId(FILE_ID)
                .domainType(ArchiveStatus.CONTRACT)
                .referenceId(REFERENCE_ID)
                .originalFilename("contract.pdf")
                .savedFilename("uuid_contract.pdf")
                .fileSize(1024L)
                .fileType("application/pdf")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
