package org.teamsai.saibackend.domain.archive.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.archive.dto.FileDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ArchiveMapper {


    void insertFile(FileDTO file);

    List<FileDTO> findFilesByReference(@Param("domainType") String domainType, @Param("referenceId") Long referenceId);

    List<FileDTO> findAllFilesByUserId(Long userId);

    Optional<FileDTO> findFileById(Long fileId);
}
