(function () {
    "use strict";

    const SETTLEMENT_TYPE_LABELS = { SHARED: "공동정산", RECURRING: "정기정산" };
    const SETTLEMENT_STATUS_LABELS = { IN_PROGRESS: "진행중", CLOSED: "완료" };
    const SPLIT_TYPE_LABELS = { EQUAL: "균등분담", CUSTOM: "직접입력" };
    const SOURCE_TYPE_LABELS = { AUTO_MATCH: "자동매칭", MANUAL: "수동" };

    const settlementId = document.getElementById("settlementId").value;

    function escapeHtml(value) {
        if (value == null) return "";
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;");
    }

    function formatAmount(amount) {
        return `${Number(amount || 0).toLocaleString()}원`;
    }

    function formatDateTime(isoString) {
        if (!isoString) return "-";
        const date = new Date(isoString);
        if (Number.isNaN(date.getTime())) return "-";

        const pad = (n) => String(n).padStart(2, "0");
        return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function setText(id, text) {
        document.getElementById(id).textContent = text;
    }

    function renderObligations(obligations) {
        const tbody = document.getElementById("obligationTableBody");

        if (!obligations || obligations.length === 0) {
            tbody.innerHTML = `<tr><td colspan="4" class="data-table__empty">참여자 납부 내역이 없습니다.</td></tr>`;
            return;
        }

        tbody.innerHTML = obligations.map((obligation) => {
            let badgeClass = "status-badge--unpaid";
            let badgeLabel = "미납";

            if (obligation.paymentStatus === "PAID") {
                badgeClass = "status-badge--paid";
                badgeLabel = "완납";
            } else if (obligation.paymentStatus === "PARTIALLY_PAID") {
                badgeClass = "status-badge--partial";
                badgeLabel = "부분납부";
            }

            return `
                <tr>
                    <td>${escapeHtml(obligation.participantName)}</td>
                    <td class="data-table__amount">${formatAmount(obligation.expectedAmount)}</td>
                    <td class="data-table__amount">${formatAmount(obligation.paidAmount)}</td>
                    <td><span class="status-badge ${badgeClass}">${badgeLabel}</span></td>
                </tr>
            `;
        }).join("");
    }

    function renderHistory(paymentHistory) {
        const tbody = document.getElementById("historyTableBody");

        if (!paymentHistory || paymentHistory.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" class="data-table__empty">확인된 납부·거래 내역이 없습니다.</td></tr>`;
            return;
        }

        tbody.innerHTML = paymentHistory.map((record) => `
            <tr>
                <td>${formatDateTime(record.recordedAt)}</td>
                <td>${escapeHtml(record.payerName)}</td>
                <td class="data-table__amount">${formatAmount(record.amount)}</td>
                <td>${SOURCE_TYPE_LABELS[record.sourceType] || "수동"}</td>
                <td>${escapeHtml(record.counterpartyName) || "-"}</td>
            </tr>
        `).join("");
    }

    function renderAccount(account) {
        const table = document.getElementById("accountTable");
        const notice = document.getElementById("noAccountNotice");

        if (!account) {
            table.hidden = true;
            notice.hidden = false;
            return;
        }

        table.hidden = false;
        notice.hidden = true;

        setText("bankName", account.bankName);
        setText("accountHolderName", account.accountHolderName);
        setText("maskedAccountNumber", account.maskedAccountNumber);
    }

    function renderPreview(preview) {
        setText("settlementDisplayId", preview.settlementDisplayId);
        setText("documentVersion", preview.documentVersion);

        setText("settlementId-cell", preview.settlementId);
        setText("title", preview.title);
        setText("ownerName", preview.ownerName);
        setText("createdAt", formatDateTime(preview.createdAt));

        setText("settlementType", SETTLEMENT_TYPE_LABELS[preview.settlementType] || preview.settlementType);
        setText("splitType", SPLIT_TYPE_LABELS[preview.splitType] || preview.splitType);
        setText("settlementStatus", SETTLEMENT_STATUS_LABELS[preview.settlementStatus] || preview.settlementStatus);
        setText("dueDate", preview.dueDate || "-");

        const paymentStatus = preview.paymentStatus || {};
        setText("totalExpectedAmount", formatAmount(paymentStatus.totalExpectedAmount));
        setText("totalPaidAmount", formatAmount(paymentStatus.totalPaidAmount));
        setText("totalRemainingAmount", formatAmount(paymentStatus.totalRemainingAmount));
        setText("progressRate", `${paymentStatus.progressRate || 0}%`);
        setText("paidCount", `${paymentStatus.paidCount || 0}명`);
        setText("partialAndUnpaidCount", `${paymentStatus.partiallyPaidCount || 0}명 / ${paymentStatus.unpaidCount || 0}명`);

        renderAccount(preview.settlementAccount);
        renderObligations(paymentStatus.obligations);
        renderHistory(preview.paymentHistory);

        document.title = `${preview.title} 정산 내역서 미리보기 | 사이원장`;
    }

    async function loadPreview() {
        try {
            const response = await authFetch(
                `/api/settlements/${settlementId}/archive-preview`,
                { headers: { Accept: "application/json" } }
            );

            if (!response.ok) {
                throw new Error("정산 내역 조회 실패");
            }

            const preview = await response.json();
            renderPreview(preview);

        } catch (error) {
            console.error("[SettlementArchiveDetail] 조회 실패", error);
            document.getElementById("docRoot").innerHTML = `<div class="doc__notice">정산 내역을 불러올 수 없습니다.</div>`;
        }
    }

    async function downloadPdf() {
        const button = document.getElementById("downloadPdfButton");
        button.disabled = true;

        try {
            const response = await authFetch(
                `/api/settlements/${settlementId}/pdf`,
                { headers: { Accept: "application/pdf" } }
            );

            if (!response.ok) {
                throw new Error("PDF 생성에 실패했습니다.");
            }

            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);

            const a = document.createElement("a");
            a.href = url;
            a.download = `정산_${settlementId}.pdf`;
            document.body.appendChild(a);
            a.click();
            a.remove();

            window.URL.revokeObjectURL(url);

        } catch (error) {
            console.error("[SettlementArchiveDetail] PDF 다운로드 실패", error);
            alert("정산 PDF 생성 중 오류가 발생했습니다.");
        } finally {
            button.disabled = false;
        }
    }

    document.getElementById("downloadPdfButton").addEventListener("click", downloadPdf);

    loadPreview();
})();
