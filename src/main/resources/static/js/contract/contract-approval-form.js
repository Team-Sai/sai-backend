(function () {
    "use strict";

    const form = document.getElementById("approveForm");
    if (!form) return;

    const contractId = form.dataset.contractId;

    const statusEl = document.getElementById("formStatus");
    const statusBanner = document.getElementById("statusBanner");
    const nextBtn = document.getElementById("btnNext");

    const REPAYMENT_TYPE_LABEL = {
        EQUAL_PRINCIPAL_AND_INTEREST: "원리금균등상환",
        EQUAL_PRINCIPAL: "원금균등상환",
        BULLET_REPAYMENT: "만기일시상환",
    };

    const STATUS_LABEL = {
        PENDING: "상대방 서명 대기중",
        COMPLETED: "완료",
    };

    if (nextBtn) nextBtn.disabled = true;

    function showStatus(message, isError) {
        statusEl.textContent = message;
        statusEl.classList.toggle("is-error", Boolean(isError));
    }

    function showBanner(text) {
        statusBanner.textContent = text;
        statusBanner.hidden = !text;
    }

    function lockForm(message) {
        nextBtn.hidden = true;
        if (message) showStatus(message, false);
    }

    async function loadContract() {
        const response = await authFetch(
            `/api/contracts/${contractId}/listdetails`,
            {
                method: "GET",
                headers: {
                    Accept: "application/json"
                },
            }
        );
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
        document.getElementById("debtorNameDisplay").textContent = data.debtorName || "-";
        document.getElementById("debtorBirthDate").textContent = data.debtorBirthDate || "-";
        document.getElementById("debtorAddressCell").textContent = data.debtorAddress || "-";

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
            lockForm("이미 승인이 완료된 변경 건입니다.");
        } else if (nextBtn) {
            nextBtn.disabled = false;
        }
    }

    nextBtn?.addEventListener("click", () => {
        const returnUrl = `/contracts/${contractId}/change-approval/signature`;
        window.location.href = `/identity-test?returnTo=${encodeURIComponent(returnUrl)}`;
    });

    loadContract()
        .catch((err) => {
            if (err.status === 403) {
                showStatus("이 계약 변경 건에 접근할 권한이 없습니다.", true);
            } else {
                showStatus("계약서를 불러오지 못했습니다.", true);
            }
            lockForm();
        });
})();