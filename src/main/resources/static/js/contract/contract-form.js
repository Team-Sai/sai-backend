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
    "relationType",
    "principalAmount",
    "interestRate",
    "startDate",
    "maturityDate",
    "repaymentDay",
    "creditorAddress",
    "contractAlias",
    "terms",
    "selectedLinkedAccountId",
  ];

  const relationTypeInput = document.getElementById("relationType");
  if (relationTypeInput && !viewMode) {
    relationTypeInput.value = params.get("relation") === "FAMILY" ? "FAMILY" : "ACQUAINTANCE";
  }

  const linkedAccountSelect = document.getElementById("selectedLinkedAccountId");
  const loanAccountSummary = document.getElementById("loanAccountSummary");
  const loanAccountBank = document.getElementById("loanAccountBank");
  const loanAccountNumber = document.getElementById("loanAccountNumber");
  const loanAccountHolder = document.getElementById("loanAccountHolder");

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

    const selectedLinkedAccountId = document.getElementById("selectedLinkedAccountId");

    if (!selectedLinkedAccountId || !selectedLinkedAccountId.value) {
      showStatus("대출금을 받을 계좌를 선택해 주세요.", true);
      if (selectedLinkedAccountId) selectedLinkedAccountId.focus();
      return false;
    }

    const rawPrincipal = principal.value ? principal.value.replace(/,/g, "") : "";
    if (!rawPrincipal || Number(rawPrincipal) <= 0) {
      showStatus("대출원금을 입력해 주세요.", true);
      principal.focus();
      return false;
    }

    const rate = Number(interestRate.value);
    if (!interestRate.value || !Number.isFinite(rate) || rate < 0.5 || rate > 20 || !Number.isInteger(rate * 2)) {
      showStatus("연이자율은 0.5% 이상 20% 이하이며, 0.5% 단위여야 합니다.", true);
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

  const STANDARD_INTEREST_RATE = 4.6;
  const GIFT_TAX_THRESHOLD = 10000000;

  const taxGuideModalOverlay = document.getElementById("taxGuideModalOverlay");
  const taxGuideCloseBtn = document.getElementById("taxGuideCloseBtn");
  const btnModalAction = document.getElementById("btnModalAction");
  const txtPrevAmount = document.getElementById("txtPrevAmount");
  const txtCurrentAmount = document.getElementById("txtCurrentAmount");
  const txtTotalAmount = document.getElementById("txtTotalAmount");
  const taxResultArea = document.getElementById("taxResultArea");
  const interestRateInput = document.getElementById("interestRate");

  let modalPreviousAmount = 0;
  let modalMode = "proceed";

  function formatWon(amount) {
    return `${Math.round(amount).toLocaleString("ko-KR")}원`;
  }


  function computeSafeInterestRate(totalAmount) {
    if (totalAmount <= 0) return 0.5;

    const minRate = STANDARD_INTEREST_RATE - (GIFT_TAX_THRESHOLD * 100) / totalAmount;
    const roundedUp = Math.ceil(minRate * 2 - 1e-9) / 2;

    return Math.min(20, Math.max(0.5, roundedUp));
  }

  async function fetchPreviousAmount() {
    const response = await authFetch("/api/contracts/previous-sum", {
      method: "GET",
      headers: { Accept: "application/json" },
    });

    if (!response.ok) {
      throw new Error(`이전 차용금 조회 실패 (HTTP ${response.status})`);
    }

    const amount = await response.json();
    return Number(amount) || 0;
  }

  function renderTaxGuide(previousAmount, currentAmount, interestRate) {
    const totalAmount = previousAmount + currentAmount;
    const standardInterest = totalAmount * (STANDARD_INTEREST_RATE / 100);
    const actualInterest = totalAmount * (interestRate / 100);
    const savedInterestRaw = standardInterest - actualInterest;
    const savedInterest = Math.max(0, Math.round(savedInterestRaw)); // 음수 표시 방지

    txtPrevAmount.textContent = formatWon(previousAmount);
    txtCurrentAmount.textContent = formatWon(currentAmount);
    txtTotalAmount.textContent = formatWon(totalAmount);

    const isSafe = savedInterestRaw < GIFT_TAX_THRESHOLD;
    const safeRate = computeSafeInterestRate(totalAmount);

    if (isSafe) {
      taxResultArea.innerHTML = `
        <p class="result-badge result-badge--safe">🟢 세금 안전 범위</p>
        <p class="result-safe-line">안전 이자선: 연 ${safeRate}% 이상</p>
        <p class="result-desc">연간 이자로 아낀 금액이 <strong>${formatWon(savedInterest)}</strong>으로 1,000만 원 미만이라 채무자(돈을 빌리는 분)에게 증여세가 발생하지 않아요!</p>
      `;
      btnModalAction.textContent = "이대로 작성 완료하기";
      modalMode = "proceed";
    } else {
      taxResultArea.innerHTML = `
        <p class="result-badge result-badge--danger">⚠️ 증여세 과세 위험</p>
        <p class="result-safe-line">증여세를 피하려면 연 ${safeRate}% 이상으로 설정해야 해요.</p>
        <p class="result-desc">⚠️ 연간 이자로 아낀 금액이 <strong>${formatWon(savedInterest)}</strong>으로 1,000만 원을 초과하여 채무자(돈을 빌리는 분)가 증여세 대상이 될 수 있어요!</p>
      `;
      btnModalAction.textContent = `안전 이자율(${safeRate}%) 적용하기`;
      modalMode = "apply-safe-rate";
    }
  }

  function proceedToNextStep() {
    if (taxGuideModalOverlay) taxGuideModalOverlay.style.display = "none";
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify(serializeForm()));
    window.location.href = `/identity-test?returnTo=${encodeURIComponent("/contracts/signature")}`;
  }

  nextBtn?.addEventListener("click", async () => {
    if (!validate()) return;

    const currentAmount = Number(document.getElementById("principalAmount").value.replace(/,/g, "")) || 0;
    const interestRate = Number(document.getElementById("interestRate").value) || 0;

    nextBtn.disabled = true;
    showStatus("이전 차용금 내역을 확인하는 중입니다...", false);

    try {
      modalPreviousAmount = await fetchPreviousAmount();

      renderTaxGuide(modalPreviousAmount, currentAmount, interestRate);
      showStatus("", false); // 상태 메시지 초기화

      if (taxGuideModalOverlay) taxGuideModalOverlay.style.display = "flex";
    } catch (err) {
      console.error("이전 차용금 조회 실패:", err);
      showStatus("이전 차용금 내역을 불러오지 못했습니다. 네트워크 상태를 확인 후 다시 시도해 주세요.", true);
    } finally {
      nextBtn.disabled = false;
    }
  });

  taxGuideCloseBtn?.addEventListener("click", () => {
    if (taxGuideModalOverlay) taxGuideModalOverlay.style.display = "none";
  });

  btnModalAction?.addEventListener("click", () => {
    if (modalMode === "apply-safe-rate") {
      const currentAmount = Number(document.getElementById("principalAmount").value.replace(/,/g, "")) || 0;
      const totalAmount = modalPreviousAmount + currentAmount;
      const safeRate = computeSafeInterestRate(totalAmount);

      interestRateInput.value = safeRate;
      renderTaxGuide(modalPreviousAmount, currentAmount, safeRate);
      return;
    }

    proceedToNextStep();
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

  function updateLoanAccountSummary(account) {
    if (!account) {
      loanAccountSummary.hidden = true;
      return;
    }
    loanAccountBank.textContent = account.bankName || "-";
    loanAccountNumber.textContent = account.maskedAccountNumber || "-";
    loanAccountHolder.textContent = account.accountHolderName || "-";
    loanAccountSummary.hidden = false;
  }

  async function loadLinkedAccounts() {
    if (!linkedAccountSelect) return;

    try {
      const response = await authFetch("/api/contracts/accounts", {
        method: "GET",
        headers: {
          Accept: "application/json"
        },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const accounts = await response.json();

      if (!Array.isArray(accounts) || accounts.length === 0) {
        linkedAccountSelect.innerHTML = '<option value="">연동된 계좌가 없습니다</option>';
        return;
      }

      linkedAccountSelect.innerHTML =
          '<option value="">계좌를 선택해 주세요</option>' +
          accounts.map((account) => `
            <option value="${account.linkedAccountId}">
              ${escapeHtml(account.bankName)} · ${escapeHtml(account.accountHolderName)} · ${escapeHtml(account.maskedAccountNumber)}
            </option>
          `).join("");

      linkedAccountSelect.addEventListener("change", () => {
        const selected = accounts.find(
            (account) => String(account.linkedAccountId) === linkedAccountSelect.value
        );
        updateLoanAccountSummary(selected);
      });
    } catch (err) {
      linkedAccountSelect.innerHTML = '<option value="">계좌 목록을 불러오지 못했습니다</option>';
    }
  }

  async function loadCreditorInfo() {
    try {
      const response = await authFetch("/api/users/me", {
        method: "GET",
        headers: {
          Accept: "application/json"
        },
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
      const response = await authFetch(
          `/api/contracts/${contractId}/listdetails`,
          {
            method: "GET",
            headers: {
              Accept: "application/json"
            },
          }
      );
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();

      if (relationTypeInput) relationTypeInput.value = data.relationType ?? "ACQUAINTANCE";
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

  loadLinkedAccounts();

  if (viewMode) {
    loadExistingContract();
  } else {
    loadCreditorInfo();
  }
})();
