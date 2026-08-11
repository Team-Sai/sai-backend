document.addEventListener("DOMContentLoaded", () => {
    const settlementList = document.getElementById("settlement-list");
    const emptyState = document.getElementById("empty-state");
    const toast = document.getElementById("toast");

    const searchInput = document.getElementById("settlement-search");
    const typeFilter = document.getElementById("type-filter");
    const splitFilter = document.getElementById("split-filter");
    const statusFilter = document.getElementById("status-filter");
    const syncButton = document.getElementById("sync-button");

    let settlements = [];

    document.getElementById("sync-time").textContent =
        new Intl.DateTimeFormat("ko-KR", {
            hour: "2-digit",
            minute: "2-digit"
        }).format(new Date());

    loadSettlements();
    showCreatedToast();
    bindFilters();

    syncButton.addEventListener("click", syncTransactions);

    async function syncTransactions() {
        try {
            syncButton.disabled = true;

            const token = sessionStorage.getItem("accessToken");

            if (!token) {
                window.location.href = "/login?required=true";
                return;
            }

            const response = await fetch("/api/transactions/sync", {
                method: "POST",
                headers: {
                    "Authorization": `Bearer ${token}`
                }
            });

            if (!response.ok) {
                if (response.status === 401) {
                    sessionStorage.removeItem("accessToken");
                    window.location.href = "/login?required=true";
                    return;
                }

                throw new Error("거래내역 동기화에 실패했습니다.");
            }

            const result = await response.json();

            showToast(
                `동기화 완료: 자동반영 ${result.appliedCount}건, 확인필요
              ${result.needsCheckCount}건, 미매칭 ${result.unmatchedCount}건`
            );

            await loadSettlements();

        } catch (error) {
            console.error(error);
            showToast(error.message || "거래내역 동기화에 실패했습니다.", true);

        } finally {
            syncButton.disabled = false;
        }
    }

    function bindFilters() {
        [searchInput, typeFilter, splitFilter, statusFilter].forEach((element) => {
            element.addEventListener("input", applyFilters);
            element.addEventListener("change", applyFilters);
        });
    }

    function applyFilters() {
        const keyword = searchInput.value.trim().toLowerCase();
        const type = typeFilter.value;
        const split = splitFilter.value;
        const status = statusFilter.value;

        const filtered = settlements.filter((settlement) => {
            const matchesKeyword =
                !keyword ||
                String(settlement.title || "").toLowerCase().includes(keyword);

            const matchesType =
                type === "ALL" || settlement.settlementType === type;

            const matchesSplit =
                split === "ALL" || settlement.splitType === split;

            const matchesStatus =
                status === "ALL" || settlement.settlementStatus === status;

            return matchesKeyword && matchesType && matchesSplit && matchesStatus;
        });

        render(filtered);
    }

    function render(items) {
        settlementList.innerHTML = "";

        if (items.length === 0) {
            emptyState.hidden = false;
            settlementList.hidden = true;
            updateSummary([]);
            return;
        }

        emptyState.hidden = true;
        settlementList.hidden = false;

        items.forEach((settlement) => {
            settlementList.appendChild(createSettlementRow(settlement));
        });

        updateSummary(items);
    }

    function createSettlementRow(settlement) {
        const row = document.createElement("article");
        row.className = "settlement-table settlement-row";

        const typeText =
            settlement.settlementType === "RECURRING" ? "정기" : "공동";

        const splitText =
            settlement.splitType === "CUSTOM" ? "직접 설정" : "균등";

        const statusText =
            settlement.settlementStatus === "CLOSED" ? "완료" : "진행 중";

        const roleText =
            settlement.role === "OWNER" ? "정산자" : "참여자";

        row.innerHTML = `
            <div class="settlement-name">
                <span class="type-badge">${escapeHtml(typeText)}</span>
                <span>${escapeHtml(settlement.title || "이름 없는 정산")}</span>
            </div>
            <span>${escapeHtml(roleText)}</span>
            <span>${escapeHtml(settlement.settlementCategory || "-")}</span>
            <span class="split-badge">${escapeHtml(splitText)}</span>
            <span class="status-badge">${escapeHtml(statusText)}</span>
            <span>${escapeHtml(formatDate(settlement.dueDate))}</span>
            <a
                class="detail-link"
                href="/settlements/${settlement.settlementId}"
                aria-label="${escapeHtml(settlement.title || "정산")} 상세 조회"
            >›</a>
        `;

        return row;
    }

    function updateSummary(items) {
        const inProgressCount = items.filter(
            (item) => item.settlementStatus === "IN_PROGRESS"
        ).length;

        document.getElementById("receivable-amount").textContent = "0";
        document.getElementById("payable-amount").textContent = "0";
        document.getElementById("receivable-count").textContent = "0건";
        document.getElementById("payable-count").textContent = "0건";
        document.getElementById("attention-count").textContent =
            String(inProgressCount);
    }

    async function loadSettlements() {
        try {
            const token = sessionStorage.getItem("accessToken");

            if (!token) {
                window.location.href = "/login?required=true";
                return;
            }

            const response = await fetch("/api/settlements", {
                method: "GET",
                headers: {
                    "Authorization": `Bearer ${token}`
                }
            });

            if (!response.ok) {
                if (response.status === 401) {
                    sessionStorage.removeItem("accessToken");
                    window.location.href = "/login?required=true";
                    return;
                }

                throw new Error("정산 목록 조회에 실패했습니다.");
            }

            settlements = await response.json();
            render(settlements);

        } catch (error) {
            console.error(error);
            settlements = [];
            render(settlements);
            showToast("정산 목록을 불러오지 못했습니다.", true);
        }
    }
    function showCreatedToast() {
        const params = new URLSearchParams(window.location.search);
        const settlementId = params.get("created");

        if (settlementId) {
            showToast(`공동정산 #${settlementId}이 생성되었습니다.`);

            const cleanUrl =
                window.location.pathname + window.location.hash;

            window.history.replaceState({}, "", cleanUrl);
        }
    }

    function formatDate(value) {
        if (!value) {
            return "-";
        }

        const [year, month, day] = value.split("-");
        return `${year}.${month}.${day}`;
    }

    function escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function showToast(message, isError = false) {
        toast.textContent = message;
        toast.classList.toggle("error", isError);
        toast.classList.add("visible");

        window.clearTimeout(showToast.timer);
        showToast.timer = window.setTimeout(() => {
            toast.classList.remove("visible");
        }, 3000);
    }
});
