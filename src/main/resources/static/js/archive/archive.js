(function () {
    "use strict";

    const ROLE_LABELS = { CREDITOR: "채권자", DEBTOR: "채무자" };
    const STATUS_LABELS = { ONGOING: "진행중", COMPLETED: "완료" };
    const REPAYMENT_TYPE_LABELS = {
        EQUAL_PRINCIPAL_AND_INTEREST: "원리금균등상환",
        EQUAL_PRINCIPAL: "원금균등상환",
        BULLET_REPAYMENT: "만기일시상환"
    };

    const SETTLEMENT_TYPE_LABELS = { SHARED: "공동정산", RECURRING: "정기정산" };
    const SETTLEMENT_STATUS_LABELS = { IN_PROGRESS: "진행중", CLOSED: "완료" };

    let currentType = "contract";
    let currentPage = 1;

    function escapeHtml(value) {
        if (value == null) return "";
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;");
    }

    function setArchiveMetadata(element, text) {
        if (element) element.textContent = text;
    }

    function removeDuplicateStatusBadges(listEl) {
        listEl.querySelectorAll(".card-actions .status-pill").forEach((badge) => badge.remove());
    }

    function renderList(contracts) {
        const listEl = document.getElementById("archiveList");

        if (!contracts || contracts.length === 0) {
            listEl.innerHTML = `<div class="empty-state">보관된 차용증이 없습니다.</div>`;
            return;
        }

        listEl.innerHTML = contracts.map((c) => {
            const roleClass = c.role === "CREDITOR" ? "creditor" : "debtor";
            const statusClass = c.contractStatus === "ONGOING" ? "ongoing" : "completed";

            return `
                <div class="archive-card" data-contract-id="${c.contractId}">
                    <div class="archive-card-main">
                        <div class="archive-card-title">
                            ${escapeHtml(c.contractAlias)}
                            <span class="role-badge ${roleClass}">${ROLE_LABELS[c.role] || c.role}</span>
                            <span class="status-pill ${statusClass}">${STATUS_LABELS[c.contractStatus] || c.contractStatus}</span>
                        </div>
                        <div class="archive-card-sub">
                            원금 ${Number(c.principalAmount).toLocaleString()}원 · 만기 ${c.maturityDate}
                        </div>
                    </div>
                    <div class="card-actions">
                        <span class="status-pill ${statusClass}">${STATUS_LABELS[c.contractStatus] || c.contractStatus}</span>
                        <button type="button" class="btn-pdf-download" data-contract-id="${c.contractId}">PDF 다운로드</button>
                    </div>
                </div>
            `;
        }).join("");

        removeDuplicateStatusBadges(listEl);
        listEl.querySelectorAll(".archive-card").forEach((card, index) => {
            const contract = contracts[index];
            setArchiveMetadata(
                card.querySelector(".archive-card-sub"),
                `\uC6D0\uAE08: ${Number(contract.principalAmount || 0).toLocaleString()}\uC6D0 / \uB9CC\uAE30: ${contract.maturityDate || "-"}`
            );
        });

        listEl.querySelectorAll(".archive-card").forEach((card) => {
            card.addEventListener("click", () => {
                const contractId = card.dataset.contractId;
                window.location.href = `/contracts/${contractId}/contract-detail`;
            });
        });

        listEl.querySelectorAll(".btn-pdf-download").forEach((btn) => {
            btn.addEventListener("click", (event) => {
                event.stopPropagation();
                downloadContractPdf(btn.dataset.contractId);
            });
        });
    }


    async function downloadContractPdf(contractId) {
        try {
            const response = await authFetch(
                `/api/contracts/${contractId}/pdf`,
                {
                    headers: {
                        Accept: "application/pdf"
                    }
                }
            );

            if (!response.ok) {
                throw new Error("PDF 생성에 실패했습니다.");
            }

            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);

            const a = document.createElement("a");
            a.href = url;
            a.download = `차용증_${contractId}.pdf`;
            document.body.appendChild(a);
            a.click();
            a.remove();

            window.URL.revokeObjectURL(url);

        } catch (error) {
            console.error("[Archive] PDF 다운로드 실패", error);
            alert("차용증 PDF 생성 중 오류가 발생했습니다.");
        }
    }

    function renderSettlementList(settlements) {
        const listEl = document.getElementById("archiveList");

        if (!settlements || settlements.length === 0) {
            listEl.innerHTML = `<div class="empty-state">보관된 정산이 없습니다.</div>`;
            return;
        }

        listEl.innerHTML = settlements.map((s) => {
            const typeClass = s.settlementType === "RECURRING" ? "recurring" : "shared";
            const statusClass = s.settlementStatus === "CLOSED" ? "completed" : "ongoing";

            return `
                <div class="archive-card" data-settlement-id="${s.settlementId}">
                    <div class="archive-card-main">
                        <div class="archive-card-title">
                            ${escapeHtml(s.title)}
                            <span class="role-badge ${typeClass}">${SETTLEMENT_TYPE_LABELS[s.settlementType] || s.settlementType}</span>
                            <span class="status-pill ${statusClass}">${SETTLEMENT_STATUS_LABELS[s.settlementStatus] || s.settlementStatus}</span>
                        </div>
                        <div class="archive-card-sub">
                            ${s.settlementType === "RECURRING" && s.startDate && s.endDate
                                ? `기간 ${escapeHtml(s.startDate)} ~ ${escapeHtml(s.endDate)}`
                                : `마감일 ${escapeHtml(s.dueDate || s.endDate || "-")}`}
                        </div>
                    </div>
                    <div class="card-actions">
                        <span class="status-pill ${statusClass}">${SETTLEMENT_STATUS_LABELS[s.settlementStatus] || s.settlementStatus}</span>
                        <button type="button" class="btn-pdf-download" data-settlement-id="${s.settlementId}">PDF 다운로드</button>
                    </div>
                </div>
            `;
        }).join("");

        removeDuplicateStatusBadges(listEl);
        listEl.querySelectorAll(".archive-card").forEach((card, index) => {
            const settlement = settlements[index];
            const amountLabel = settlement.settlementType === "RECURRING"
                ? "\uD68C\uCC28\uBCC4 \uAE08\uC561"
                : "\uC815\uC0B0\uAE08\uC561";
            const deadline = settlement.settlementType === "RECURRING"
                ? `\uAE30\uAC04: ${settlement.startDate || "-"} ~ ${settlement.endDate || "-"}`
                : `\uB9C8\uAC10\uC77C: ${settlement.dueDate || settlement.endDate || "-"}`;
            setArchiveMetadata(
                card.querySelector(".archive-card-sub"),
                `${amountLabel}: ${Number(settlement.totalAmount || 0).toLocaleString()}\uC6D0 / ${deadline}`
            );
        });

        listEl.querySelectorAll(".archive-card").forEach((card) => {
            card.addEventListener("click", () => {
                const settlementId = card.dataset.settlementId;
                window.location.href = `/archive/settlements/${settlementId}`;
            });
        });

        listEl.querySelectorAll(".btn-pdf-download").forEach((btn) => {
            btn.addEventListener("click", (event) => {
                event.stopPropagation();
                downloadSettlementPdf(btn.dataset.settlementId);
            });
        });
    }

    async function downloadSettlementPdf(settlementId) {
        try {
            const response = await authFetch(
                `/api/settlements/${settlementId}/pdf`,
                {
                    headers: {
                        Accept: "application/pdf"
                    }
                }
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
            console.error("[Archive] 정산 PDF 다운로드 실패", error);
            alert("정산 PDF 생성 중 오류가 발생했습니다.");
        }
    }

    function renderPager(data) {
        const pager = document.getElementById("pager");
        const pageLabel = document.getElementById("pageLabel");
        const prevBtn = document.getElementById("prevPageBtn");
        const nextBtn = document.getElementById("nextPageBtn");

        if (!data.totalPages || data.totalPages <= 1) {
            pager.style.display = "none";
            return;
        }

        pager.style.display = "flex";
        pageLabel.textContent = `${data.currentPage} / ${data.totalPages}`;
        prevBtn.disabled = data.currentPage <= 1;
        nextBtn.disabled = data.currentPage >= data.totalPages;
    }

    async function loadContracts() {
        const listEl = document.getElementById("archiveList");

        try {
            const response = await authFetch(
                `/api/dashboard?roleFilter=ALL&page=${currentPage}`,
                {
                    headers: {
                        Accept: "application/json"
                    }
                }
            );

            if (!response.ok) {
                throw new Error("보관함 조회 실패");
            }

            const data = await response.json();

            renderList(data.contracts);
            renderPager(data);

        } catch (error) {
            console.error("[Archive] 조회 실패", error);
            listEl.innerHTML = `<div class="error-state">보관함을 불러올 수 없습니다.</div>`;
        }
    }

    async function loadSettlements() {
        const listEl = document.getElementById("archiveList");

        try {
            const response = await authFetch(
                `/api/settlements`,
                {
                    headers: {
                        Accept: "application/json"
                    }
                }
            );

            if (!response.ok) {
                throw new Error("보관함 조회 실패");
            }

            const settlements = await response.json();

            renderSettlementList(settlements);
            document.getElementById("pager").style.display = "none";

        } catch (error) {
            console.error("[Archive] 조회 실패", error);
            listEl.innerHTML = `<div class="error-state">보관함을 불러올 수 없습니다.</div>`;
        }
    }

    function loadArchive() {
        if (currentType === "settlement") {
            loadSettlements();
        } else {
            loadContracts();
        }
    }

    document.getElementById("prevPageBtn").addEventListener("click", () => {
        currentPage = Math.max(1, currentPage - 1);
        loadArchive();
    });

    document.getElementById("nextPageBtn").addEventListener("click", () => {
        currentPage += 1;
        loadArchive();
    });

    document.querySelectorAll("#archiveTabs .tab-btn").forEach((tab) => {
        tab.addEventListener("click", () => {
            if (tab.dataset.type === currentType) return;

            currentType = tab.dataset.type;
            currentPage = 1;

            document.querySelectorAll("#archiveTabs .tab-btn").forEach((t) => t.classList.remove("active"));
            tab.classList.add("active");

            loadArchive();
        });
    });

    loadArchive();
})();
