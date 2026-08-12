package org.teamsai.saibackend.domain.archive;

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
import org.teamsai.saibackend.domain.archive.dto.ArchiveStatus;
import org.teamsai.saibackend.domain.archive.dto.FileDTO;
import org.teamsai.saibackend.domain.archive.mapper.ArchiveMapper;
import org.teamsai.saibackend.domain.archive.service.ArchiveService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
