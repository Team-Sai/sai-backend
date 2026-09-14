package org.teamsai.saibackend.domain.contract.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.teamsai.saibackend.domain.archive.dto.ArchiveStatus;
import org.teamsai.saibackend.domain.archive.service.ArchiveService;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.event.ContractCompletedEvent;

import java.io.ByteArrayInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContractArchiveEventListener {

    private final ArchiveService archiveService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleContractCompleted(ContractCompletedEvent event) {
        LoanContractResponse completedContract = event.getCompletedContract();
        Long contractId = completedContract.getContractId();

        try {
            byte[] contractPdf = archiveService.renderContractPdf(completedContract);
            archiveService.saveFile(
                    ArchiveStatus.CONTRACT.name(),
                    contractId,
                    "차용증_" + contractId + ".pdf",
                    "application/pdf",
                    new ByteArrayInputStream(contractPdf),
                    contractPdf.length
            );
        } catch (Exception e) {
            log.error("PDF 아카이빙 실패 (계약 ID: {})", contractId, e);
        }
    }
}