package org.teamsai.saibackend.domain.contract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.contract.dto.LoanContractFileDTO;
import org.teamsai.saibackend.domain.contract.exception.LoanContractFileErrorCode;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractFileMapper;
import org.teamsai.saibackend.domain.contract.service.contract.LoanContractFileService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanContractFileService 단위 테스트")
class LoanContractFileServiceTest {

    private static final Long CONTRACT_ID = 1L;

    @Mock
    private LoanContractFileMapper fileMapper;

    @InjectMocks
    private LoanContractFileService fileService;

    private Path savedPath;

    @AfterEach
    void cleanUp() throws IOException {
        if (savedPath != null) {
            Files.deleteIfExists(savedPath);
            savedPath = null;
        }
    }

    @Nested
    @DisplayName("계약서 파일 저장")
    class InsertContractFile {

        @Test
        @DisplayName("파일 정보를 저장하고 저장된 fileId를 반환한다")
        void insertContractFileSuccess() {
            LoanContractFileDTO file = createFile();

            Long fileId = fileService.insertContractFile(file);

            verify(fileMapper).insertContractFile(file);
            assertThat(fileId).isEqualTo(file.getFileId());
        }
    }

    @Nested
    @DisplayName("계약서 파일 조회")
    class FindFileByContractId {

        @Test
        @DisplayName("계약서 ID로 조회해 파일 DTO를 반환한다")
        void findFileByContractIdSuccess() {
            LoanContractFileDTO file = createFile();
            given(fileMapper.findFileByContractId(CONTRACT_ID)).willReturn(Optional.of(file));

            LoanContractFileDTO result = fileService.findFileByContractId(CONTRACT_ID);

            assertThat(result).isEqualTo(file);
        }

        @Test
        @DisplayName("파일을 찾을 수 없으면 예외가 발생한다")
        void findFileByContractIdFailsWhenNotFound() {
            given(fileMapper.findFileByContractId(CONTRACT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.findFileByContractId(CONTRACT_ID))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractFileErrorCode.CONTRACT_FILE_NOT_FOUND)
                    );
        }
    }

    @Nested
    @DisplayName("서명 파일 저장")
    class SaveSignatureFile {

        @Test
        @DisplayName("서명 이미지를 디스크에 저장하고 저장된 경로를 반환한다")
        void saveSignatureFileSuccess() throws IOException {
            MultipartFile signature = new MockMultipartFile(
                    "signature", "signature.png", "image/png", "signature-bytes".getBytes()
            );

            String resultPath = fileService.saveSignatureFile(CONTRACT_ID, signature);
            savedPath = Path.of(resultPath);

            assertThat(savedPath).exists();
            assertThat(Files.readAllBytes(savedPath)).isEqualTo("signature-bytes".getBytes());
            assertThat(resultPath).contains(CONTRACT_ID + "_");
        }

        @Test
        @DisplayName("파일 저장 중 오류가 발생하면 예외가 발생한다")
        void saveSignatureFileFailsOnIOException() throws IOException {
            MultipartFile signature = mock(MultipartFile.class);
            given(signature.getOriginalFilename()).willReturn("signature.png");
            given(signature.getInputStream()).willThrow(new IOException("disk error"));

            assertThatThrownBy(() -> fileService.saveSignatureFile(CONTRACT_ID, signature))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LoanContractFileErrorCode.SIGNATURE_UPLOAD_FAILED)
                    );
        }
    }

    private LoanContractFileDTO createFile() {
        return LoanContractFileDTO.builder()
                .fileId(1L)
                .contractId(CONTRACT_ID)
                .originalFilename("signature.png")
                .savedFilename("uuid_signature.png")
                .fileSize(1024L)
                .fileType("image/png")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
