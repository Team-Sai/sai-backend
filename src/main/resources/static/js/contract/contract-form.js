(function () {
  "use strict";

  const DRAFT_KEY = "loanContractDraft";

  const form = document.getElementById("contractForm");
  const statusEl = document.getElementById("formStatus");
  const statusBanner = document.getElementById("statusBanner");
  const nextBtn = document.getElementById("btnNext");

  if (!form) return;

  const params = new URLSearchParams(window.location.search);
  let contractId = params.get("contractId") || null;
  const viewMode = Boolean(contractId);

  const FIELD_IDS = [
    "principalAmount",
    "interestRate",
    "startDate",
    "maturityDate",
    "repaymentDay",
    "creditorAddress",
    "contractAlias",
    "terms",
    "accountNumber",
    // "selectedLinkedAccountId", // 계좌 연동 기능 완성 전까지 임시로 주석 처리
  ];

  // 계좌 연동 기능 완성 전까지 임시로 주석 처리 (테스트용 수동 입력으로 대체)
  // const linkedAccountSelect = document.getElementById("selectedLinkedAccountId");
  // const loanAccountSummary = document.getElementById("loanAccountSummary");
  // const loanAccountBank = document.getElementById("loanAccountBank");
  // const loanAccountNumber = document.getElementById("loanAccountNumber");
  // const loanAccountHolder = document.getElementById("loanAccountHolder");

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


    const rawPrincipal = principal.value ? principal.value.replace(/,/g, "") : "";
    if (!rawPrincipal || Number(rawPrincipal) <= 0) {
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

  function escapeHtml(value) {
    if (value == null) return "";
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
  }

  // 계좌 연동 기능 완성 전까지 임시로 주석 처리 (테스트용 수동 입력으로 대체)
  // function updateLoanAccountSummary(account) {
  //   if (!account) {
  //     loanAccountSummary.hidden = true;
  //     return;
  //   }
  //   loanAccountBank.textContent = account.bankName || "-";
  //   loanAccountNumber.textContent = account.maskedAccountNumber || "-";
  //   loanAccountHolder.textContent = account.accountHolderName || "-";
  //   loanAccountSummary.hidden = false;
  // }
  //
  // async function loadLinkedAccounts() {
  //   if (!linkedAccountSelect) return;
  //
  //   try {
  //     const response = await fetch("/api/contracts/accounts", {
  //       method: "GET",
  //       headers: authHeaders({ Accept: "application/json" }),
  //     });
  //     if (!response.ok) throw new Error(`HTTP ${response.status}`);
  //     const accounts = await response.json();
  //
  //     if (!Array.isArray(accounts) || accounts.length === 0) {
  //       linkedAccountSelect.innerHTML = '<option value="">연동된 계좌가 없습니다</option>';
  //       return;
  //     }
  //
  //     linkedAccountSelect.innerHTML =
  //         '<option value="">계좌를 선택해 주세요</option>' +
  //         accounts.map((account) => `
  //           <option value="${account.linkedAccountId}">
  //             ${escapeHtml(account.bankName)} · ${escapeHtml(account.accountHolderName)} · ${escapeHtml(account.maskedAccountNumber)}
  //           </option>
  //         `).join("");
  //
  //     linkedAccountSelect.addEventListener("change", () => {
  //       const selected = accounts.find(
  //           (account) => String(account.linkedAccountId) === linkedAccountSelect.value
  //       );
  //       updateLoanAccountSummary(selected);
  //     });
  //   } catch (err) {
  //     linkedAccountSelect.innerHTML = '<option value="">계좌 목록을 불러오지 못했습니다</option>';
  //   }
  // }

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

  // loadLinkedAccounts(); // 계좌 연동 기능 완성 전까지 임시로 주석 처리

  if (viewMode) {
    loadExistingContract();
  } else {
    loadCreditorInfo();
  }
})();