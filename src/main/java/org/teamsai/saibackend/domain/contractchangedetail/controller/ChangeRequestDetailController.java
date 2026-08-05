package org.teamsai.saibackend.domain.contractchangedetail.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.contractchangedetail.dto.ChangeRequestDetailDTO;
import org.teamsai.saibackend.domain.contractchangedetail.service.ChangeRequestDetailService;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ChangeRequestDetailController {

    private final ChangeRequestDetailService changeRequestDetailService;


    @GetMapping("/{contractId}/change-requests/{changeRequestId}")
    public ChangeRequestDetailDTO requestDetail(
        @PathVariable Long contractId,
        @PathVariable Long changeRequestId,
        @AuthenticationPrincipal(expression = "userId") Long userId
    ){
        return changeRequestDetailService.getDetail(contractId, changeRequestId, userId);
    }
}
