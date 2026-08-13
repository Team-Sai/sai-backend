(function () {
  "use strict";

  const DRAFT_KEY = "debtorApprovalDraft";

  const form = document.getElementById("approveForm");
  if (!form) return;

  const contractId = form.dataset.contractId;

  const statusEl = document.getElementById("formStatus");
  const statusBanner = document.getElementById("statusBanner");
  const debtorAddressInput = document.getElementById("debtorAddress");
  const nextBtn = document.getElementById("btnNext");

  const REPAYMENT_TYPE_LABEL = {
    EQUAL_PRINCIPAL_AND_INTEREST: "원리금균등상환",
    EQUAL_PRINCIPAL: "원금균등상환",
    BULLET_REPAYMENT: "만기일시상환",
  };

  const STATUS_LABEL = {
    DRAFT: "작성중 (아직 채권자가 전송하지 않았습니다)",
    PENDING: "전송됨 (채무자 확인 대기중)",
    COMPLETED: "완료",
  };

  if (nextBtn) nextBtn.disabled = true;

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

  function lockForm(message) {
    debtorAddressInput.disabled = true;
    nextBtn.hidden = true;
    if (message) showStatus(message, false);
  }

  async function loadContract() {
    const response = await fetch(`/api/contracts/${contractId}/listdetails`, {
      method: "GET",
      headers: authHeaders({ Accept: "application/json" }),
    });
    if (!response.ok) {
      const error = new Error(`HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    const data = await response.json();

    document.getElementById("creditorAddress").textContent = data.creditorAddress || "-";
    document.getElementById("creditorNameDisplay").textContent = data.creditorName || "-";
    document.getElementById("creditorNameCell").textContent = data.creditorName || "-";
    document.getElementById("creditorBirthDateCell").textContent = data.creditorBirthDate || "-";
    document.getElementById("debtorName").textContent = data.debtorName || "-";
    document.getElementById("debtorBirthDate").textContent = data.debtorBirthDate || "-";
    debtorAddressInput.value = data.debtorAddress || "";

    document.getElementById("principalAmount").textContent =
      data.principalAmount != null ? Number(data.principalAmount).toLocaleString("ko-KR") : "-";
    document.getElementById("interestRate").textContent =
      data.interestRate != null ? data.interestRate : "-";
    document.getElementById("repaymentType").textContent =
      REPAYMENT_TYPE_LABEL[data.repaymentType] || data.repaymentType || "-";
    document.getElementById("startDate").textContent = data.startDate || "-";
    document.getElementById("maturityDate").textContent = data.maturityDate || "-";
    document.getElementById("repaymentDay").textContent = data.repaymentDay ?? "-";
    document.getElementById("contractAlias").textContent = data.contractAlias || "-";
    document.getElementById("terms").textContent = data.terms || "특약사항 없음";

    showBanner(`현재 상태: ${STATUS_LABEL[data.status] || data.status}`);

    if (data.status === "COMPLETED") {
      lockForm("이미 서명이 완료된 계약입니다.");
    } else if (data.status === "DRAFT") {
      lockForm("채권자가 아직 계약서를 전송하지 않았습니다. 전송 후 다시 확인해 주세요.");
    } else if (nextBtn) {
      nextBtn.disabled = false;
    }
  }

  async function linkAsDebtor() {
    const response = await fetch(`/api/contracts/${contractId}/debtor`, {
      method: "PATCH",
      headers: authHeaders(),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new Error(body?.message || "계약서에 채무자로 연결하지 못했습니다.");
    }
  }

  nextBtn?.addEventListener("click", () => {
    const debtorAddress = debtorAddressInput.value.trim();
    if (!debtorAddress) {
      showStatus("본인 주소를 입력해 주세요.", true);
      debtorAddressInput.focus();
      return;
    }

    try {
      sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ debtorAddress }));
    } catch (err) {
      showStatus("입력 내용을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.", true);
      return;
    }

    const returnUrl = `/contracts/${contractId}/approve/signature`;
    window.location.href = `/identity-test?returnTo=${encodeURIComponent(returnUrl)}`;
  });

  loadContract()
    .catch(async (err) => {
      if (err.status === 403) {

        try {
          await linkAsDebtor();
          await loadContract();
        } catch (linkErr) {
          showStatus(linkErr.message || "계약서에 채무자로 연결하지 못했습니다.", true);
          lockForm();
        }
        return;
      }
      showStatus("계약서를 불러오지 못했습니다.", true);
      lockForm();
    });
})();
