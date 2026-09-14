const dashboardState = {
    amountSummary: null,
    attentionItems: [],
    monthlySummary: null,
    recentTransactions: [],
    calendarDays: []
};

let calendarCursor = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
let currentTransactionFilter = "ALL";

const currencyFormatter = new Intl.NumberFormat("ko-KR");
const transactionStatusLabels = {
    COMPLETED: "완료",
    IN_PROGRESS: "진행 중"
};
const transactionStatusClasses = {
    COMPLETED: "done",
    IN_PROGRESS: "waiting"
};

function toAmount(value) {
    const amount = Number(value);
    return Number.isFinite(amount) ? amount : 0;
}

function formatWon(amount) {
    return `${currencyFormatter.format(toAmount(amount))}원`;
}

function formatSummaryAmount(amount) {
    return `${currencyFormatter.format(toAmount(amount))}원`
        .split("")
        .map((character, index) => character >= "0" && character <= "9"
            ? `<span class="summary-digit" style="--digit-index:${index}">${character}</span>`
            : character)
        .join("");
}

function toDateKey(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

function toYearMonth(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    return `${year}-${month}`;
}

async function loadDashboard(calendarOnly = false) {

    if (!calendarOnly) {
        setLoadingState();
    }

    try {
        const params = new URLSearchParams({
            yearMonth: toYearMonth(calendarCursor)
        });
        const response = await authFetch(
            `/api/integration/dashboard?${params}`
        );

        if (!response.ok) {
            throw new Error(`통합 대시보드 조회 실패: ${response.status}`);
        }

        const data = await response.json();
        dashboardState.calendarDays = data.calendarDays || [];

        if (calendarOnly) {
            renderCalendar();
            return;
        }

        dashboardState.monthlySummary = data.monthlySummary || null;
        dashboardState.amountSummary = data.amountSummary || null;
        dashboardState.attentionItems = data.attentionItems || [];
        dashboardState.recentTransactions = data.recentTransactions || [];
        renderDashboard();
        document.body.classList.remove("integration-loading");
    } catch (error) {
        console.error(error);
        if (!calendarOnly) {
            renderLoadError();
            document.body.classList.remove("integration-loading");
        }
    }
}

function setLoadingState() {
    const summary = document.getElementById("summaryRotatingText");
    if (summary) {
        summary.textContent = "불러오는 중";
    }
    document.getElementById("attentionList").innerHTML =
        '<div class="empty-state">내역을 불러오는 중입니다.</div>';
    document.getElementById("transactionList").innerHTML =
        '<div class="empty-state">거래를 불러오는 중입니다.</div>';
}

function renderDashboard() {
    renderAmountSummary();
    renderAttentionItems();
    renderMonthlySummary();
    renderCalendar();
    renderTransactions();
}

function renderAmountSummary() {
    const receivable = dashboardState.amountSummary?.receivable || {};
    const payable = dashboardState.amountSummary?.payable || {};

    const messages = [
        `현재 받을 금액은 <strong class="summary-amount-number">${formatSummaryAmount(receivable.totalAmount)}</strong>이고<br>현재 보낼 금액은 <strong class="summary-amount-number">${formatSummaryAmount(payable.totalAmount)}</strong>이에요.`,
        `정산 받을 금액은 <strong class="summary-amount-number">${formatSummaryAmount(receivable.settlementAmount)}</strong>이고<br>정산 보낼 금액은 <strong class="summary-amount-number">${formatSummaryAmount(payable.settlementAmount)}</strong>이에요.`,
        `대여금은 <strong class="summary-amount-number">${formatSummaryAmount(receivable.loanAmount)}</strong>이고<br>차입금은 <strong class="summary-amount-number">${formatSummaryAmount(payable.loanAmount)}</strong>이에요.`
    ];
    const target = document.getElementById("summaryRotatingText");
    if (!target) return;
    let index = 0;
    const show = () => {
        target.classList.remove("is-summary-entering");
        void target.offsetWidth;
        target.innerHTML = messages[index];
        target.classList.add("is-summary-entering");
        index = (index + 1) % messages.length;
    };
    show();
    window.clearInterval(window.summaryRotationTimer);
    window.summaryRotationTimer = window.setInterval(show, 3200);
}

function renderAttentionItems() {
    const list = document.getElementById("attentionList");
    list.innerHTML = "";

    if (dashboardState.attentionItems.length === 0) {
        list.innerHTML = '<div class="empty-state">확인이 필요한 내역이 없습니다.</div>';
        return;
    }

    dashboardState.attentionItems.forEach((item) => {
        const row = document.createElement("div");
        const button = document.createElement("button");
        row.className = "attention-item";
        row.innerHTML = `<span>${escapeHtml(getAttentionMessage(item))}</span>`;
        button.type = "button";
        button.className = "status-badge badge-progress attention-confirm-button";
        button.textContent = "확인";
        button.addEventListener("click", () => {
            if (item.actionUrl) window.location.href = item.actionUrl;
        });
        row.appendChild(button);
        list.appendChild(row);
    });
}

function getAttentionMessage(item) {
    if (item.type === "LOAN_DUE_SOON") {
        return item.remainingDays === 0
            ? "차용증 상환일이 오늘이에요"
            : `차용증 상환일이 ${item.remainingDays}일 남았어요`;
    }

    if (item.type === "SETTLEMENT_DUE_SOON") {
        return item.remainingDays === 0
            ? "정산 마감일이 오늘이에요"
            : `정산 마감일이 ${item.remainingDays}일 남았어요`;
    }

    return "확인이 필요한 내역이 있어요";
}

function renderMonthlySummary() {
    const summary = dashboardState.monthlySummary || {};
    const completionRate = Math.min(100, Math.max(0, toAmount(summary.transactionCompletionRate)));

    document.getElementById("completedCount").textContent = summary.completedTransactionCount || 0;
    document.getElementById("activeSettlementCount").textContent = summary.inProgressSettlementCount || 0;
    document.getElementById("loanRepaymentCount").textContent = summary.inProgressLoanRepaymentCount || 0;
    document.getElementById("completionRateText").textContent = `${completionRate}%`;
    document.getElementById("completionRateBar").style.width = `${completionRate}%`;
}

function getCalendarDates(cursor) {
    const year = cursor.getFullYear();
    const month = cursor.getMonth();
    const firstDay = new Date(year, month, 1);
    const startDate = new Date(year, month, 1 - firstDay.getDay());

    return Array.from({ length: 42 }, (_, index) => {
        const date = new Date(startDate);
        date.setDate(startDate.getDate() + index);
        return date;
    });
}

function getCalendarDaysByDate() {
    return dashboardState.calendarDays.reduce((acc, calendarDay) => {
        acc[calendarDay.date] = calendarDay;
        return acc;
    }, {});
}

function renderCalendar() {
    const label = document.getElementById("calendarMonthLabel");
    const grid = document.getElementById("calendarGrid");
    const calendarDaysByDate = getCalendarDaysByDate();
    const todayKey = toDateKey(new Date());
    const cursorMonth = calendarCursor.getMonth();

    label.textContent = `${calendarCursor.getFullYear()}년 ${calendarCursor.getMonth() + 1}월`;
    grid.innerHTML = "";

    getCalendarDates(calendarCursor).forEach((date) => {
        const dateKey = toDateKey(date);
        const calendarDay = calendarDaysByDate[dateKey] || {};
        const dayButton = document.createElement("button");
        dayButton.type = "button";
        dayButton.className = "calendar-day";
        dayButton.setAttribute("aria-label", `${date.getMonth() + 1}월 ${date.getDate()}일`);

        if (date.getMonth() !== cursorMonth) dayButton.classList.add("is-outside");
        if (date.getDay() === 0) dayButton.classList.add("is-sunday");
        if (date.getDay() === 6) dayButton.classList.add("is-saturday");
        if (dateKey === todayKey) dayButton.classList.add("is-today");

        dayButton.innerHTML = `
            <span class="calendar-day__number">${date.getDate()}</span>
            <span class="calendar-day__dots" aria-hidden="true">
                ${calendarDay.hasInbound ? '<i class="dot dot--inbound"></i>' : ""}
                ${calendarDay.hasOutbound ? '<i class="dot dot--outbound"></i>' : ""}
            </span>
        `;

        dayButton.addEventListener("click", () => {
            window.location.href = `/calendar?date=${dateKey}`;
        });

        grid.appendChild(dayButton);
    });
}

function renderTransactions() {
    const list = document.getElementById("transactionList");
    const filteredTransactions = (currentTransactionFilter === "ALL"
        ? dashboardState.recentTransactions
        : dashboardState.recentTransactions.filter(
            (transaction) => transaction.type === currentTransactionFilter
        )).slice(0, 5);

    list.innerHTML = "";

    if (filteredTransactions.length === 0) {
        list.innerHTML = '<div class="empty-state">표시할 거래 내역이 없습니다.</div>';
        return;
    }

    filteredTransactions.forEach((transaction) => {
        const row = document.createElement("div");
        const statusLabel = transactionStatusLabels[transaction.status] || transaction.status || "-";
        const statusClass = transactionStatusClasses[transaction.status] || "waiting";
        const detailUrl = transaction.detailUrl || "#";
        row.className = "transaction-row";
        row.setAttribute("role", "row");
        row.innerHTML = `
            <span>${escapeHtml(transaction.title)}</span>
            <span><span class="status-badge ${statusClass === "done" ? "badge-completed" : "badge-progress"}">${escapeHtml(statusLabel)}</span></span>
            <span>${formatWon(transaction.amount)}</span>
            <span><a class="detail-arrow" href="${escapeHtml(detailUrl)}" aria-label="${escapeHtml(transaction.title)} 상세보기">›</a></span>
        `;
        list.appendChild(row);
    });
}

function renderLoadError() {
    const summary = document.getElementById("summaryRotatingText");
    if (summary) summary.textContent = "금액을 불러오지 못했습니다.";
    document.getElementById("attentionList").innerHTML =
        '<div class="empty-state">내역을 불러오지 못했습니다.</div>';
    document.getElementById("transactionList").innerHTML =
        '<div class="empty-state">거래를 불러오지 못했습니다.</div>';
    dashboardState.calendarDays = [];
    dashboardState.monthlySummary = null;
    renderMonthlySummary();
    renderCalendar();
}

function bindEvents() {
    document.getElementById("prevMonthButton").addEventListener("click", async () => {
        calendarCursor = new Date(calendarCursor.getFullYear(), calendarCursor.getMonth() - 1, 1);
        await loadDashboard(true);
    });

    document.getElementById("nextMonthButton").addEventListener("click", async () => {
        calendarCursor = new Date(calendarCursor.getFullYear(), calendarCursor.getMonth() + 1, 1);
        await loadDashboard(true);
    });

    document.getElementById("calendarViewAllButton").addEventListener("click", () => {
        window.location.href = `/calendar?date=${toDateKey(calendarCursor)}`;
    });

    document.querySelectorAll(".filter-tab").forEach((button) => {
        button.addEventListener("click", () => {
            document.querySelectorAll(".filter-tab").forEach((tab) => tab.classList.remove("is-active"));
            button.classList.add("is-active");
            currentTransactionFilter = button.dataset.filter;
            renderTransactions();
        });
    });
}

function escapeHtml(value) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

bindEvents();
loadDashboard();
