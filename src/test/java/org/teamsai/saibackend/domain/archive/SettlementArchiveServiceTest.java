package org.teamsai.saibackend.domain.archive;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saibackend.domain.archive.dto.ArchiveStatus;
import org.teamsai.saibackend.domain.archive.dto.FileDTO;
import org.teamsai.saibackend.domain.archive.mapper.ArchiveMapper;
import org.teamsai.saibackend.domain.archive.service.HtmlToPdfRenderer;
import org.teamsai.saibackend.domain.archive.service.SettlementArchiveService;
import org.teamsai.saibackend.domain.payment.type.SourceType;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementDetailResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentHistoryResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentObligationResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentStatusResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.service.SettlementAccountService;
import org.teamsai.saibackend.domain.settlement.service.SettlementPaymentHistoryService;
import org.teamsai.saibackend.domain.settlement.service.SettlementPaymentStatusService;
import org.teamsai.saibackend.domain.settlement.service.SettlementQueryService;
import org.teamsai.saibackend.global.exception.DomainException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementArchiveService 단위 테스트")
class SettlementArchiveServiceTest {

    private static final Long SETTLEMENT_ID = 1L;
    private static final Long USER_ID = 10L;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private ArchiveMapper archiveMapper;

    @Mock
    private SettlementQueryService settlementQueryService;

    @Mock
    private SettlementPaymentStatusService settlementPaymentStatusService;

    @Mock
    private SettlementPaymentHistoryService settlementPaymentHistoryService;

    @Mock
    private SettlementAccountService settlementAccountService;

    @InjectMocks
    private SettlementArchiveService settlementArchiveService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(settlementArchiveService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(settlementArchiveService, "htmlToPdfRenderer", new HtmlToPdfRenderer());
    }

    @Nested
    @DisplayName("아카이브 캐시 동작")
    class CachingBehavior {

        private void stubRenderingDependencies() {
            given(settlementPaymentStatusService.getPaymentStatus(SETTLEMENT_ID, USER_ID))
                    .willReturn(paymentStatus());
            given(settlementPaymentHistoryService.getPaymentHistory(SETTLEMENT_ID, USER_ID))
                    .willReturn(List.of());
            given(settlementAccountService.findCurrentAccount(USER_ID, SETTLEMENT_ID))
                    .willThrow(SettlementErrorCode.SETTLEMENT_ACCOUNT_NOT_FOUND.toException());
            given(templateEngine.process(eq("archive/settlement-pdf"), any()))
                    .willReturn("<html><body>settlement pdf</body></html>");
        }

        @Test
        @DisplayName("종료된 정산이고 저장된 PDF가 없으면 새로 렌더링하고 아카이브에 저장한다")
        void rendersAndSavesWhenClosedSettlementHasNoCachedFile() {
            stubRenderingDependencies();
            given(settlementQueryService.getSettlementDetail(SETTLEMENT_ID, USER_ID))
                    .willReturn(detail("CLOSED"));
            given(archiveMapper.findFilesByReference(ArchiveStatus.SETTLEMENT.name(), SETTLEMENT_ID))
                    .willReturn(List.of());

            byte[] pdfBytes = settlementArchiveService.generateSettlementPdfBytes(SETTLEMENT_ID, USER_ID);

            assertThat(pdfBytes).isNotEmpty();
            assertThat(new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

            ArgumentCaptor<FileDTO> captor = ArgumentCaptor.forClass(FileDTO.class);
            verify(archiveMapper).insertFile(captor.capture());

            FileDTO saved = captor.getValue();
            assertThat(saved.getDomainType()).isEqualTo(ArchiveStatus.SETTLEMENT);
            assertThat(saved.getReferenceId()).isEqualTo(SETTLEMENT_ID);
            assertThat(tempDir.resolve(saved.getSavedFilename())).exists();
        }

        @Test
        @DisplayName("종료된 정산이고 저장된 PDF가 있으면 재렌더링 없이 캐시된 파일을 반환한다")
        void returnsCachedPdfWithoutRerenderingWhenClosedSettlementHasCachedFile() throws IOException {
            given(settlementQueryService.getSettlementDetail(SETTLEMENT_ID, USER_ID))
                    .willReturn(detail("CLOSED"));

            String savedFilename = "SETTLEMENT_1_cached.pdf";
            byte[] cachedBytes = "%PDF-cached-bytes".getBytes(StandardCharsets.UTF_8);
            Files.write(tempDir.resolve(savedFilename), cachedBytes);

            FileDTO cachedFile = FileDTO.builder()
                    .savedFilename(savedFilename)
                    .build();
            given(archiveMapper.findFilesByReference(ArchiveStatus.SETTLEMENT.name(), SETTLEMENT_ID))
                    .willReturn(List.of(cachedFile));

            byte[] result = settlementArchiveService.generateSettlementPdfBytes(SETTLEMENT_ID, USER_ID);

            assertThat(result).isEqualTo(cachedBytes);

            verify(settlementPaymentStatusService, never()).getPaymentStatus(any(), any());
            verify(settlementPaymentHistoryService, never()).getPaymentHistory(any(), any());
            verify(templateEngine, never()).process(anyString(), any());
            verify(archiveMapper, never()).insertFile(any());
        }

        @Test
        @DisplayName("진행 중인 정산은 캐시를 조회하지 않고 매번 새로 렌더링하며 저장하지도 않는다")
        void alwaysRerendersAndNeverCachesInProgressSettlement() {
            stubRenderingDependencies();
            given(settlementQueryService.getSettlementDetail(SETTLEMENT_ID, USER_ID))
                    .willReturn(detail("IN_PROGRESS"));

            byte[] pdfBytes = settlementArchiveService.generateSettlementPdfBytes(SETTLEMENT_ID, USER_ID);

            assertThat(pdfBytes).isNotEmpty();

            verify(archiveMapper, never()).findFilesByReference(any(), any());
            verify(archiveMapper, never()).insertFile(any());
            verify(settlementPaymentStatusService).getPaymentStatus(SETTLEMENT_ID, USER_ID);
        }
    }

    @Nested
    @DisplayName("정산 수취 계좌 조회 예외 처리")
    class SettlementAccountHandling {

        @BeforeEach
        void stubRenderingDependencies() {
            given(settlementQueryService.getSettlementDetail(SETTLEMENT_ID, USER_ID))
                    .willReturn(detail("IN_PROGRESS"));
            given(settlementPaymentStatusService.getPaymentStatus(SETTLEMENT_ID, USER_ID))
                    .willReturn(paymentStatus());
            given(settlementPaymentHistoryService.getPaymentHistory(SETTLEMENT_ID, USER_ID))
                    .willReturn(List.of());
        }

        @Test
        @DisplayName("정산 수취 계좌가 설정되어 있지 않아도 예외 없이 PDF를 생성한다")
        void generatesPdfWhenSettlementAccountIsNotSet() {
            given(settlementAccountService.findCurrentAccount(USER_ID, SETTLEMENT_ID))
                    .willThrow(SettlementErrorCode.SETTLEMENT_ACCOUNT_NOT_FOUND.toException());
            given(templateEngine.process(eq("archive/settlement-pdf"), any()))
                    .willReturn("<html><body>settlement pdf</body></html>");

            byte[] pdfBytes = settlementArchiveService.generateSettlementPdfBytes(SETTLEMENT_ID, USER_ID);

            assertThat(pdfBytes).isNotEmpty();
        }

        @Test
        @DisplayName("계좌 미설정이 아닌 다른 예외는 그대로 전파한다")
        void propagatesNonAccountNotFoundExceptions() {
            given(settlementAccountService.findCurrentAccount(USER_ID, SETTLEMENT_ID))
                    .willThrow(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED.toException());

            assertThatThrownBy(() ->
                    settlementArchiveService.generateSettlementPdfBytes(SETTLEMENT_ID, USER_ID)
            ).isInstanceOfSatisfying(
                    DomainException.class,
                    exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED)
            );
        }
    }

    @Nested
    @DisplayName("정산 PDF 렌더링 내용 검증")
    class RenderSettlementPdfContent {

        @BeforeEach
        void setUpRealTemplateEngine() {
            ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/");
            resolver.setSuffix(".html");
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCharacterEncoding("UTF-8");
            resolver.setCacheable(false);

            SpringTemplateEngine realTemplateEngine = new SpringTemplateEngine();
            realTemplateEngine.setTemplateResolver(resolver);

            ReflectionTestUtils.setField(settlementArchiveService, "templateEngine", realTemplateEngine);
        }

        @Test
        @DisplayName("정산 기본정보, 이행현황, 상세 납부내역이 PDF 본문에 그대로 반영된다")
        void rendersSettlementDataIntoPdfBody() throws IOException {
            given(settlementQueryService.getSettlementDetail(SETTLEMENT_ID, USER_ID))
                    .willReturn(detail("CLOSED"));
            given(archiveMapper.findFilesByReference(ArchiveStatus.SETTLEMENT.name(), SETTLEMENT_ID))
                    .willReturn(List.of());
            given(settlementPaymentStatusService.getPaymentStatus(SETTLEMENT_ID, USER_ID))
                    .willReturn(paymentStatusWithObligation());
            given(settlementPaymentHistoryService.getPaymentHistory(SETTLEMENT_ID, USER_ID))
                    .willReturn(List.of(historyRecord()));
            given(settlementAccountService.findCurrentAccount(USER_ID, SETTLEMENT_ID))
                    .willThrow(SettlementErrorCode.SETTLEMENT_ACCOUNT_NOT_FOUND.toException());

            byte[] pdfBytes = settlementArchiveService.generateSettlementPdfBytes(SETTLEMENT_ID, USER_ID);

            try (PDDocument document = PDDocument.load(pdfBytes)) {
                String text = new PDFTextStripper().getText(document);

                assertThat(text).contains("여행 정산");
                assertThat(text).contains("공동정산");
                assertThat(text).contains("완료");
                assertThat(text).contains("균등분담");
                assertThat(text).contains("완납");
                assertThat(text).contains("자동매칭");
                assertThat(text).contains("설정된 정산 수취 계좌가 없습니다");
                assertThat(text).contains("ST-1");
            }
        }
    }

    private SettlementDetailResponse detail(String settlementStatus) {
        return new SettlementDetailResponse(
                SETTLEMENT_ID,
                "여행 정산",
                "홍길동",
                "생활비",
                "SHARED",
                settlementStatus,
                "EQUAL",
                LocalDate.of(2026, 8, 31),
                null,
                null,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                "OWNER"
        );
    }

    private SettlementPaymentStatusResponse paymentStatus() {
        return SettlementPaymentStatusResponse.builder()
                .settlementId(SETTLEMENT_ID)
                .obligations(List.of())
                .totalExpectedAmount(BigDecimal.ZERO)
                .totalPaidAmount(BigDecimal.ZERO)
                .totalRemainingAmount(BigDecimal.ZERO)
                .paidCount(0)
                .partiallyPaidCount(0)
                .unpaidCount(0)
                .progressRate(BigDecimal.ZERO)
                .closable(false)
                .build();
    }

    private SettlementPaymentStatusResponse paymentStatusWithObligation() {
        return SettlementPaymentStatusResponse.builder()
                .settlementId(SETTLEMENT_ID)
                .obligations(List.of(
                        SettlementPaymentObligationResponse.builder()
                                .paymentObligationId(100L)
                                .participantId(200L)
                                .participantName("홍길동")
                                .expectedAmount(new BigDecimal("10000"))
                                .paidAmount(new BigDecimal("10000"))
                                .remainingAmount(BigDecimal.ZERO)
                                .paymentStatus(org.teamsai.saibackend.domain.payment.type.PaymentStatus.PAID)
                                .build()
                ))
                .totalExpectedAmount(new BigDecimal("10000"))
                .totalPaidAmount(new BigDecimal("10000"))
                .totalRemainingAmount(BigDecimal.ZERO)
                .paidCount(1)
                .partiallyPaidCount(0)
                .unpaidCount(0)
                .progressRate(new BigDecimal("100.00"))
                .closable(true)
                .build();
    }

    private SettlementPaymentHistoryResponse historyRecord() {
        return SettlementPaymentHistoryResponse.builder()
                .paymentRecordId(1L)
                .recordedAt(LocalDateTime.of(2026, 8, 18, 12, 0))
                .payerName("홍길동")
                .amount(new BigDecimal("10000"))
                .sourceType(SourceType.AUTO_MATCH)
                .bankTransactionId(300L)
                .counterpartyName("카카오뱅크 홍길동")
                .externalTransactionId("TX-EXTERNAL-1")
                .build();
    }
}
