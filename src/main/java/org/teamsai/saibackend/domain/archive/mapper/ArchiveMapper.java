package org.teamsai.saibackend.domain.archive.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.archive.dto.FileDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ArchiveMapper {

    //공용
    void insertFile(FileDTO file);
    List<FileDTO> findFilesByReference(@Param("domainType") String domainType, @Param("referenceId") Long referenceId);

    //차용증
    List<FileDTO> findAllFilesByUserId(@Param("userId") Long userId);
    Optional<FileDTO> findFileById(@Param("fileId") Long fileId);
}
