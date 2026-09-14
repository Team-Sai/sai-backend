package org.teamsai.saibackend.domain.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.archive.dto.ArchiveStatus;
import org.teamsai.saibackend.domain.archive.service.ArchiveService;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.event.ContractCompletedEvent;
import org.teamsai.saibackend.domain.contract.service.ContractArchiveEventListener;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContractArchiveEventListener 단위 테스트")
class ContractArchiveEventListenerTest {

    private static final Long CONTRACT_ID = 1L;

    @Mock
    private ArchiveService archiveService;

    @InjectMocks
    private ContractArchiveEventListener contractArchiveEventListener;

    private LoanContractResponse completedContract() {
        return LoanContractResponse.builder()
                .contractId(CONTRACT_ID)
                .build();
    }

    @Test
    @DisplayName("계약 완료 이벤트를 받으면 PDF를 렌더링해서 저장한다")
    void handleContractCompletedSavesRenderedPdf() {
        byte[] pdfBytes = new byte[]{1, 2, 3};
        LoanContractResponse contract = completedContract();
        given(archiveService.renderContractPdf(contract)).willReturn(pdfBytes);

        contractArchiveEventListener.handleContractCompleted(new ContractCompletedEvent(contract));

        verify(archiveService).saveFile(
                eq(ArchiveStatus.CONTRACT.name()),
                eq(CONTRACT_ID),
                eq("차용증_" + CONTRACT_ID + ".pdf"),
                eq("application/pdf"),
                any(InputStream.class),
                eq((long) pdfBytes.length)
        );
    }

    @Test
    @DisplayName("PDF 렌더링 중 예외가 발생해도 예외를 전파하지 않고 로깅만 한다")
    void handleContractCompletedSwallowsExceptionOnRenderFailure() {
        LoanContractResponse contract = completedContract();
        willThrow(new RuntimeException("렌더링 실패"))
                .given(archiveService).renderContractPdf(contract);

        assertThatCode(() ->
                contractArchiveEventListener.handleContractCompleted(new ContractCompletedEvent(contract))
        ).doesNotThrowAnyException();

        verify(archiveService, never()).saveFile(any(), any(), any(), any(), any(), anyLong());
    }
}
