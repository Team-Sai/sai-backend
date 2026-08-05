(function () {
  "use strict";

  const DRAFT_KEY = "loanContractDraft";

  const form = document.getElementById("contractForm");
  const statusEl = document.getElementById("formStatus");
  const statusBanner = document.getElementById("statusBanner");
  const nextBtn = document.getElementById("btnNext");
  const identityPanel = document.getElementById("identityPanel");
  const identityStatusText = document.getElementById("identityStatusText");
  const btnIdentityVerify = document.getElementById("btnIdentityVerify");
  const identityVerificationIdInput = document.getElementById("identityVerificationId");
  const selectedLinkedAccountIdSelect = document.getElementById("selectedLinkedAccountId");

  if (!form) return;

  const params = new URLSearchParams(window.location.search);
  let contractId = params.get("contractId") || null;
  const viewMode = Boolean(contractId);

  let identityVerified = false;

  const FIELD_IDS = [
    "principalAmount",
    "interestRate",
    "startDate",
    "maturityDate",
    "repaymentDay",
    "creditorAddress",
    "contractAlias",
    "terms",
    "identityVerificationId",
    "selectedLinkedAccountId",
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
    const day = Number(repaymentDay.value);
    if (!day || day < 1 || day > 31) {
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
    if (!selectedLinkedAccountIdSelect || !selectedLinkedAccountIdSelect.value) {
      showStatus("대출금을 지급할 계좌를 선택해 주세요.", true);
      selectedLinkedAccountIdSelect?.focus();
      return false;
    }
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

  function setIdentityStatus(message, isError) {
    if (!identityStatusText) return;
    identityStatusText.textContent = message;
    identityStatusText.classList.toggle("is-error", Boolean(isError));
  }

  function setIdentityVerifying(loading) {
    if (!btnIdentityVerify) return;
    btnIdentityVerify.disabled = loading;
    btnIdentityVerify.textContent = loading ? "본인인증 처리 중..." : "본인인증 시작";
  }

  async function startIdentityVerification() {
    if (typeof PortOne === "undefined" || typeof PortOne.requestIdentityVerification !== "function") {
      setIdentityStatus("포트원 SDK를 불러오지 못했습니다.", true);
      return;
    }

    setIdentityVerifying(true);
    setIdentityStatus("본인인증 요청을 준비하고 있습니다.");

    try {
      const prepareResponse = await fetch("/api/identity-verifications", {
        method: "POST",
        headers: authHeaders({ "Content-Type": "application/json" }),
        body: JSON.stringify({ purpose: "LOAN_CONTRACT" }),
      });
      const prepare = await prepareResponse.json().catch(() => null);
      if (!prepareResponse.ok || !prepare?.identityVerificationId || !prepare?.storeId || !prepare?.channelKey) {
        throw new Error(prepare?.message || "본인인증 준비에 실패했습니다.");
      }

      setIdentityStatus("본인인증 창을 여는 중입니다.");
      const verifyResult = await PortOne.requestIdentityVerification({
        storeId: prepare.storeId,
        channelKey: prepare.channelKey,
        identityVerificationId: prepare.identityVerificationId,
      });

      if (verifyResult?.code != null) {
        throw new Error(verifyResult.message || "본인인증에 실패했습니다.");
      }

      setIdentityStatus("인증 결과를 확인하고 있습니다.");
      const completeResponse = await fetch(
        `/api/identity-verifications/${encodeURIComponent(prepare.identityVerificationId)}/complete`,
        { method: "POST", headers: authHeaders() }
      );
      const completeResult = await completeResponse.json().catch(() => null);
      if (!completeResponse.ok || completeResult?.status !== "VERIFIED") {
        throw new Error(completeResult?.message || "본인인증 완료 확인에 실패했습니다.");
      }

      identityVerified = true;
      if (identityVerificationIdInput) identityVerificationIdInput.value = prepare.identityVerificationId;
      identityPanel?.classList.add("is-verified");
      setIdentityStatus("본인인증이 완료되었습니다.");
      if (nextBtn) nextBtn.disabled = false;

      await loadCreditorInfo();
      await loadSelectableAccounts();
    } catch (err) {
      identityVerified = false;
      setIdentityStatus(err.message || "본인인증 처리 중 오류가 발생했습니다.", true);
    } finally {
      setIdentityVerifying(false);
    }
  }

  btnIdentityVerify?.addEventListener("click", startIdentityVerification);

  nextBtn?.addEventListener("click", () => {
    if (!identityVerified) {
      showStatus("본인인증을 먼저 진행해 주세요.", true);
      return;
    }
    if (!validate()) return;

    try {
      sessionStorage.setItem(DRAFT_KEY, JSON.stringify(serializeForm()));
    } catch (err) {
      showStatus("작성 내용을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.", true);
      return;
    }

    window.location.href = "/api/contracts/signature";
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
      /* leave the "-" placeholders in place */
    }
  }

  async function loadSelectableAccounts() {
    if (!selectedLinkedAccountIdSelect) return;

    try {
      const response = await fetch("/api/contracts/accounts", {
        method: "GET",
        headers: authHeaders({ Accept: "application/json" }),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const accounts = await response.json();

      selectedLinkedAccountIdSelect.innerHTML = "";

      if (!accounts || accounts.length === 0) {
        const option = document.createElement("option");
        option.value = "";
        option.textContent = "연동된 활성 계좌가 없습니다. 마이페이지에서 계좌를 연동해 주세요.";
        selectedLinkedAccountIdSelect.appendChild(option);
        selectedLinkedAccountIdSelect.disabled = true;
        return;
      }

      const placeholder = document.createElement("option");
      placeholder.value = "";
      placeholder.textContent = "계좌를 선택하세요";
      selectedLinkedAccountIdSelect.appendChild(placeholder);

      accounts.forEach((account) => {
        const option = document.createElement("option");
        option.value = account.linkedAccountId;
        option.textContent = `${account.bankName} ${account.maskedAccountNumber} (${account.accountHolderName})`;
        selectedLinkedAccountIdSelect.appendChild(option);
      });

      selectedLinkedAccountIdSelect.disabled = false;
    } catch (err) {
      selectedLinkedAccountIdSelect.innerHTML = "";
      const option = document.createElement("option");
      option.value = "";
      option.textContent = "계좌 목록을 불러오지 못했습니다.";
      selectedLinkedAccountIdSelect.appendChild(option);
      selectedLinkedAccountIdSelect.disabled = true;
    }
  }

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
  } else if (nextBtn) {
    nextBtn.disabled = true;
  }
})();
