package org.teamsai.saibackend.domain.contractchange.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ContractChangePageController {

    @GetMapping("/contracts/{contractId}/change-request")
    public String changeRequestPage(@PathVariable Long contractId) {
        return "contractchange/request";
    }
}