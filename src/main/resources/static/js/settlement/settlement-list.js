document.addEventListener("DOMContentLoaded", () => {
    const settlementList = document.getElementById("settlement-list");
    const emptyState = document.getElementById("empty-state");
    const toast = document.getElementById("toast");

    const searchInput = document.getElementById("settlement-search");
    const typeFilter = document.getElementById("type-filter");
    const statusFilter = document.getElementById("status-filter");
    const sortFilter = document.getElementById("sort-filter");
    const syncButton = document.getElementById("sync-button");

    let settlements = [];

    statusFilter.previousElementSibling.textContent = "진행 상태";
    statusFilter.innerHTML = '<option value="ALL">전체</option><option value="IN_PROGRESS">진행 중</option><option value="CLOSED">완료</option>';
    sortFilter.previousElementSibling.textContent = "정렬";
    sortFilter.innerHTML = '<option value="LATEST">최신순</option><option value="AMOUNT_DESC">금액순</option><option value="DEADLINE">마감일순</option>';

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

            const response = await authFetch(
                "/api/transactions/sync",
                {
                    method: "POST"
                }
            );

            if (!response.ok) {
                throw new Error(
                    "거래내역 동기화에 실패했습니다."
                );
            }

            const result = await response.json();

            showToast(
                `동기화 완료: 자동반영 ${result.appliedCount}건, 확인필요
              ${result.needsCheckCount}건, 미매칭 ${result.unmatchedCount}건`
            );

            await loadSettlements();

            await new Promise(resolve => window.setTimeout(resolve, 1250));

            await MatchingReviewModal.open({
                reviewChannel: "TRANSACTION_HISTORY",
                targetType: "SETTLEMENT"
            });

        } catch (error) {
            console.error(error);
            showToast(error.message || "거래내역 동기화에 실패했습니다.", true);

        } finally {
            syncButton.disabled = false;
        }
    }

    function bindFilters() {
        [searchInput, typeFilter, statusFilter, sortFilter].forEach((element) => {
            element.addEventListener("input", applyFilters);
            element.addEventListener("change", applyFilters);
        });
    }

    function applyFilters() {
        const keyword = searchInput.value.trim().toLowerCase();
        const type = typeFilter.value;
        const status = statusFilter.value;
        const sort = sortFilter.value;

        const filtered = settlements.filter((settlement) => {
            const matchesKeyword =
                !keyword ||
                String(settlement.title || "").toLowerCase().includes(keyword);

            const matchesType =
                type === "ALL" || settlement.settlementType === type;

            const matchesStatus =
                status === "ALL" || settlement.settlementStatus === status;

            return matchesKeyword && matchesType && matchesStatus;
        });

        if (sort === "LATEST") filtered.sort((a, b) => String(b.createdAt || "").localeCompare(String(a.createdAt || "")));
        if (sort === "AMOUNT_DESC") filtered.sort((a, b) => Number(b.totalAmount || 0) - Number(a.totalAmount || 0));
        if (sort === "DEADLINE") filtered.sort((a, b) => String(a.dueDate || "").localeCompare(String(b.dueDate || "")));

        render(filtered);
    }

    function render(items) {
        settlementList.innerHTML = "";

        if (items.length === 0) {
            emptyState.hidden = false;
            settlementList.hidden = true;
            return;
        }

        emptyState.hidden = true;
        settlementList.hidden = false;

        items.forEach((settlement) => {
            settlementList.appendChild(createSettlementRow(settlement));
        });

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
        const scheduleText =
            settlement.settlementType === "RECURRING"
                ? formatPeriod(settlement.startDate, settlement.endDate)
                : formatDate(settlement.dueDate);
        row.innerHTML = `
            <div class="settlement-name">
                <span>${escapeHtml(settlement.title || "이름 없는 정산")}</span>
            </div>
            <span class="type-badge ${settlement.settlementType === "RECURRING" ? "badge-recurring" : "badge-role"}">${escapeHtml(typeText)}</span>
            <span class="status-badge ${settlement.role === "OWNER" ? "badge-role" : "badge-debtor"}">${escapeHtml(roleText)}</span>
            <span>${escapeHtml(settlement.settlementCategory || "-")}</span>
            <span class="split-badge ${settlement.splitType === "CUSTOM" ? "badge-custom" : "badge-split"}">${escapeHtml(splitText)}</span>
            <span class="status-badge ${settlement.settlementStatus === "CLOSED" ? "badge-completed" : "badge-progress"}">${escapeHtml(statusText)}</span>
            <span>${escapeHtml(scheduleText)}</span>
            <a
                class="detail-link"
                href="/settlements/${settlement.settlementId}"
                aria-label="${escapeHtml(settlement.title || "정산")} 상세 조회"
            >›</a>
        `;

        return row;
    }

    function updateSummary(summary) {
        document.getElementById("receivable-amount").textContent =
            formatAmount(summary.receivableAmount);
        document.getElementById("payable-amount").textContent =
            formatAmount(summary.payableAmount);
        document.getElementById("receivable-count").textContent =
            `${summary.receivableCount ?? 0}건`;
        document.getElementById("payable-count").textContent =
            `${summary.payableCount ?? 0}건`;
        document.getElementById("settlement-total-count").textContent = settlements.length;
    }

    async function loadSettlements() {
        await Promise.all([
            loadSettlementList(),
            loadSummary()
        ]);
    }

    document.addEventListener("matching-review:closed", loadSettlements);

    async function loadSettlementList() {
        try {
            const response = await authFetch(
                "/api/settlements"
            );

            if (!response.ok) {
                throw new Error("정산 목록 조회에 실패했습니다.");
            }

            settlements = await response.json();
            document.getElementById("settlement-total-count").textContent = settlements.length;
            render(settlements);
        } catch (error) {
            console.error("정산 목록 조회 실패:", error);
            settlements = [];
            render(settlements);
            showToast("정산 목록을 불러오지 못했습니다.", true);
        }
    }

    async function loadSummary() {
        try {
            const response = await authFetch(
                "/api/settlements/summary"
            );

            if (!response.ok) {
                throw new Error("정산 요약 조회에 실패했습니다.");
            }

            updateSummary(await response.json());
        } catch (error) {
            console.error("정산 요약 조회 실패:", error);
            updateSummary({});
            showToast("정산 요약을 불러오지 못했습니다.", true);
        }
    }

    function formatAmount(value) {
        return Number(value ?? 0).toLocaleString("ko-KR");
    }
    function showCreatedToast() {
        const params = new URLSearchParams(window.location.search);
        const settlementId = params.get("created");

        if (settlementId) {
            showToast(`정산 #${settlementId}이 생성되었습니다.`);

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

    function formatPeriod(startDate, endDate) {
        if (!startDate) return "-";
        if (!endDate) return `${formatDate(startDate)} ~ 계속`;
        return `${formatDate(startDate)} ~ ${formatDate(endDate)}`;
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
