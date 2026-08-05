package org.teamsai.saibackend.domain.contractdetail.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;

@Getter@Builder

public class ContractDetailResponse {

   private LoanContractResponse contract;
   private boolean canRequestChange;
   private String address;
}
