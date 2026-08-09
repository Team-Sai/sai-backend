(function () {
  "use strict";

  const DRAFT_KEY = "loanContractDraft";

  const form = document.getElementById("contractForm");
  const statusEl = document.getElementById("formStatus");
  const statusBanner = document.getElementById("statusBanner");
  const nextBtn = document.getElementById("btnNext");
  const identityVerificationIdInput = document.getElementById("identityVerificationId");
  // 계좌 연동이 아직 해결되지 않아 임시로 주석 처리 (사용자가 계좌정보를 직접 입력하는 것으로 대체)
  // const selectedLinkedAccountIdSelect = document.getElementById("selectedLinkedAccountId");

  if (!form) return;

  const params = new URLSearchParams(window.location.search);
  let contractId = params.get("contractId") || null;
  const viewMode = Boolean(contractId);

  const FIELD_IDS = [
    "identityVerificationId",
    "principalAmount",
    "interestRate",
    "startDate",
    "maturityDate",
    "repaymentDay",
    "creditorAddress",
    "contractAlias",
    "terms",
    // "selectedLinkedAccountId", // 계좌 연동이 아직 해결되지 않아 임시로 주석 처리
  ];

  function authHeaders(extra) {
    const token = sessionStorage.getItem("accessToken");
    return Object.assign(
      token ? { Authorization: `Bearer ${token}` } : {},
      extra || {}
    );
  }

  function showStatus(message, isError) {
    statusEl.textContent = message;
    statusEl.classList.toggle("is-error", Boolean(isError));
  }

  function showBanner(text) {
    statusBanner.textContent = text;
    statusBanner.hidden = !text;
  }

  function serializeForm() {
    const data = {};
    FIELD_IDS.forEach((id) => {
      const field = document.getElementById(id);
      if (!field) return;
      data[id] = field.value.trim();
    });

    const checkedType = form.querySelector('input[name="repaymentType"]:checked');
    data.repaymentType = checkedType ? checkedType.value : null;

    if (data.principalAmount) {
      data.principalAmount = data.principalAmount.replace(/,/g, "");
    }
    if (data.repaymentDay) {
      data.repaymentDay = Number(data.repaymentDay);
    }
    if (data.selectedLinkedAccountId) {
      data.selectedLinkedAccountId = Number(data.selectedLinkedAccountId);
    }
    if (!data.terms) {
      data.terms = null;
    }

    return data;
  }

  function validate() {
    const principal = document.getElementById("principalAmount");
    const interestRate = document.getElementById("interestRate");
    const repaymentType = form.querySelector('input[name="repaymentType"]:checked');
    const startDate = document.getElementById("startDate");
    const maturityDate = document.getElementById("maturityDate");
    const repaymentDay = document.getElementById("repaymentDay");
    const creditorAddress = document.getElementById("creditorAddress");
    const contractAlias = document.getElementById("contractAlias");

    if (!principal.value || Number(principal.value.replace(/,/g, "")) <= 0) {
      showStatus("대출원금을 입력해 주세요.", true);
      principal.focus();
      return false;
    }
    if (!interestRate.value || Number(interestRate.value) <= 0 || Number(interestRate.value) > 20) {
      showStatus("연이자율은 0보다 크고 20% 이하여야 합니다.", true);
      interestRate.focus();
      return false;
    }
    if (!repaymentType) {
      showStatus("상환방식을 선택해 주세요.", true);
      return false;
    }
    if (!startDate.value || !maturityDate.value) {
      showStatus("대출 시작일과 만기일을 입력해 주세요.", true);
      (startDate.value ? maturityDate : startDate).focus();
      return false;
    }
    if (!(new Date(maturityDate.value) > new Date(startDate.value))) {
      showStatus("대출 만기일은 시작일 이후여야 합니다.", true);
      maturityDate.focus();
      return false;
    }
    const dayValue = repaymentDay.value.trim();

    if (dayValue === "" || isNaN(dayValue)) {
      showStatus("상환일을 입력해 주세요.", true);
      repaymentDay.focus();
      return false;
    }

    const day = Number(dayValue);

    if (day < 1 || day > 31) {
      showStatus("상환일은 1일에서 31일 사이여야 합니다.", true);
      repaymentDay.focus();
      return false;
    }
    if (!creditorAddress.value.trim()) {
      showStatus("채권자 주소를 입력해 주세요.", true);
      creditorAddress.focus();
      return false;
    }
    if (!contractAlias.value.trim()) {
      showStatus("계약의 목적을 입력해 주세요.", true);
      contractAlias.focus();
      return false;
    }
    // 계좌 연동이 아직 해결되지 않아 임시로 주석 처리 (사용자가 계좌정보를 직접 입력)
    // if (!selectedLinkedAccountIdSelect || !selectedLinkedAccountIdSelect.value) {
    //   showStatus("대출금을 지급할 계좌를 선택해 주세요.", true);
    //   selectedLinkedAccountIdSelect?.focus();
    //   return false;
    // }
    return true;
  }

  function lockForm() {
    FIELD_IDS.forEach((id) => {
      const field = document.getElementById(id);
      if (field) field.disabled = true;
    });
    form.querySelectorAll('input[name="repaymentType"]').forEach((el) => {
      el.disabled = true;
    });
  }

  nextBtn?.addEventListener("click", () => {
    if (!validate()) return;

    sessionStorage.setItem(DRAFT_KEY, JSON.stringify(serializeForm()));
    window.location.href = "/contracts/signature";
  });

  const principalInput = document.getElementById("principalAmount");
  if (principalInput) {
    principalInput.addEventListener("blur", () => {
      const raw = principalInput.value.replace(/[^\d]/g, "");
      if (raw) principalInput.value = Number(raw).toLocaleString("ko-KR");
    });
    principalInput.addEventListener("focus", () => {
      principalInput.value = principalInput.value.replace(/[^\d]/g, "");
    });
  }

  async function loadCreditorInfo() {
    try {
      const response = await fetch("/api/users/me", {
        method: "GET",
        headers: authHeaders({ Accept: "application/json" }),
      });
      if (!response.ok) return;
      const user = await response.json();

      document.getElementById("creditorNameDisplay").textContent = user.name || "-";
      document.getElementById("creditorNameCell").textContent = user.name || "-";
      document.getElementById("creditorBirthDateCell").textContent = user.birthDate || "-";
    } catch (err) {

    }
  }

  // TEST ONLY: mock 계정(sai.mock.identity.verified-user-ids)의 본인인증을 자동으로 채운다.
  // 나중에 mock 관련 코드 지울 때 이 함수와 호출부도 함께 제거할 것.
  async function autoFillMockIdentityVerification() {
    if (!identityVerificationIdInput) return;

    try {
      const response = await fetch("/api/mock/identity-verifications/complete", {
        method: "POST",
        headers: authHeaders(),
      });
      if (!response.ok) return;
      const data = await response.json();
      if (data.identityVerificationId) {
        identityVerificationIdInput.value = data.identityVerificationId;
      }
    } catch (err) {
      // mock 엔드포인트가 없는 환경(dev 프로필이 아닌 경우 등)에서는 조용히 무시
    }
  }

  // 계좌 연동이 아직 해결되지 않아 임시로 주석 처리 (사용자가 계좌정보를 직접 입력하는 것으로 대체)
  // async function loadSelectableAccounts() {
  //   if (!selectedLinkedAccountIdSelect) return;
  //
  //   try {
  //     const response = await fetch("/api/contracts/accounts", {
  //       method: "GET",
  //       headers: authHeaders({ Accept: "application/json" }),
  //     });
  //     if (!response.ok) throw new Error(`HTTP ${response.status}`);
  //     const accounts = await response.json();
  //
  //     selectedLinkedAccountIdSelect.innerHTML = "";
  //
  //     if (!accounts || accounts.length === 0) {
  //       const option = document.createElement("option");
  //       option.value = "";
  //       option.textContent = "연동된 활성 계좌가 없습니다. 마이페이지에서 계좌를 연동해 주세요.";
  //       selectedLinkedAccountIdSelect.appendChild(option);
  //       selectedLinkedAccountIdSelect.disabled = true;
  //       return;
  //     }
  //
  //     const placeholder = document.createElement("option");
  //     placeholder.value = "";
  //     placeholder.textContent = "계좌를 선택하세요";
  //     selectedLinkedAccountIdSelect.appendChild(placeholder);
  //
  //     accounts.forEach((account) => {
  //       const option = document.createElement("option");
  //       option.value = account.linkedAccountId;
  //       option.textContent = `${account.bankName} ${account.maskedAccountNumber} (${account.accountHolderName})`;
  //       selectedLinkedAccountIdSelect.appendChild(option);
  //     });
  //
  //     selectedLinkedAccountIdSelect.disabled = false;
  //   } catch (err) {
  //     selectedLinkedAccountIdSelect.innerHTML = "";
  //     const option = document.createElement("option");
  //     option.value = "";
  //     option.textContent = "계좌 목록을 불러오지 못했습니다.";
  //     selectedLinkedAccountIdSelect.appendChild(option);
  //     selectedLinkedAccountIdSelect.disabled = true;
  //   }
  // }

  async function loadExistingContract() {
    nextBtn.hidden = true;

    try {
      const response = await fetch(`/api/contracts/${contractId}/listdetails`, {
        method: "GET",
        headers: authHeaders({ Accept: "application/json" }),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();

      document.getElementById("principalAmount").value = data.principalAmount ?? "";
      document.getElementById("interestRate").value = data.interestRate ?? "";
      document.getElementById("startDate").value = data.startDate ?? "";
      document.getElementById("maturityDate").value = data.maturityDate ?? "";
      document.getElementById("repaymentDay").value = data.repaymentDay ?? "";
      document.getElementById("creditorAddress").value = data.creditorAddress ?? "";
      document.getElementById("contractAlias").value = data.contractAlias ?? "";
      document.getElementById("terms").value = data.terms ?? "";

      if (data.repaymentType) {
        const target = form.querySelector(`input[name="repaymentType"][value="${data.repaymentType}"]`);
        if (target) target.checked = true;
      }

      document.getElementById("creditorNameDisplay").textContent = data.creditorName || "-";
      document.getElementById("creditorNameCell").textContent = data.creditorName || "-";
      document.getElementById("creditorBirthDateCell").textContent = data.creditorBirthDate || "-";
      document.getElementById("debtorName").value = data.debtorName || "";
      document.getElementById("debtorAddress").value = data.debtorAddress || "";

      const STATUS_LABEL = { DRAFT: "작성중", PENDING: "전송됨 (채무자 확인 대기중)", COMPLETED: "완료" };
      showBanner(`현재 상태: ${STATUS_LABEL[data.status] || data.status}`);

      lockForm();
    } catch (err) {
      showStatus("차용증 조회에 실패했습니다.", true);
    }
  }

  if (viewMode) {
    loadExistingContract();
  } else {
    loadCreditorInfo();
    autoFillMockIdentityVerification(); // TEST ONLY
    // 계좌 연동이 아직 해결되지 않아 임시로 주석 처리
    // loadSelectableAccounts();
  }
})();
