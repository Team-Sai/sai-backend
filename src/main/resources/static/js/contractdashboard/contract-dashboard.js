function authHeaders(extra) {
    const token = sessionStorage.getItem("accessToken");
    return Object.assign(
        token ? { Authorization: `Bearer ${token}` } : {},
        extra || {}
    );
}

let currentKeyword = '';
let currentRoleFilter = 'ALL';
let currentSortType = '';
let currentPage = 1;
let isFirstLoad = true;

const ROLE_LABELS = { CREDITOR: '채권자', DEBTOR: '채무자' };
const CATEGORY_LABELS = { RECEIVE: '수취', PAY: '납부' };
const CONTRACT_STATUS_LABELS = { ONGOING: '진행중', COMPLETED: '완료' };
const PAYMENT_STATUS_LABELS = { WAITING: '대기', NO_DUE_THIS_MONTH: '이번달 없음', PAID: '납부', NONE: '-' };

function fetchDashboard() {
    const url = `/api/dashboard?keyword=${encodeURIComponent(currentKeyword)}`
        + `&roleFilter=${currentRoleFilter}`
        + `&sortType=${currentSortType}`
        + `&page=${currentPage}`;

    fetch(url, { headers: authHeaders() })
        .then(response => {
            if (!response.ok) throw new Error('조회 실패');
            return response.json();
        })
        .then(data => {
            const shouldRender = renderSummary(data.summary);
            if (!shouldRender) return;

            renderTable(data.contracts);
            renderPagination(data.currentPage, data.totalPages);
        })
        .catch(() => {
            alert('대시보드 정보를 불러오는 데 실패했습니다.');
        });
}

function renderSummary(summary) {
    if (isFirstLoad) {
        isFirstLoad = false;
        currentRoleFilter = summary.defaultFilter;

        document.querySelectorAll('.role-toggle button').forEach(btn => {
            btn.classList.toggle('is-active', btn.dataset.role === currentRoleFilter);
        });

        fetchDashboard();
        return false;
    }

    document.getElementById('totalContractCount').textContent = summary.totalContractCount + '건';
    document.getElementById('totalLentAmount').textContent = summary.totalLentAmount.toLocaleString() + '원';
    document.getElementById('totalBorrowedAmount').textContent = summary.totalBorrowedAmount.toLocaleString() + '원';
    document.getElementById('thisMonthDueAmount').textContent = summary.thisMonthDueAmount.toLocaleString() + '원';

    const badge = document.getElementById('dDayBadge');
    if (summary.nearestDueDate) {
        const today = new Date();
        const due = new Date(summary.nearestDueDate);
        const diffDays = Math.ceil((due - today) / 86400000);
        badge.textContent = `D-${diffDays}`;
    } else {
        badge.textContent = '';
    }
    return true;
}

function renderTable(contracts) {
    const tbody = document.getElementById('contractTableBody');
    tbody.innerHTML = '';

    if (contracts.length === 0) {
        tbody.innerHTML = `<tr><td colspan="11" class="empty-state">표시할 계약이 없습니다.</td></tr>`;
        return;
    }

    contracts.forEach(c => {
        const roleClass = c.role === 'CREDITOR' ? 'creditor' : 'debtor';
        const statusClass = c.contractStatus === 'ONGOING' ? 'ongoing' : 'completed';
        const paymentClass = c.paymentStatus.toLowerCase();
        const nearestDue = c.nearestScheduleDueDate || '-';

        tbody.innerHTML += `
            <tr>
                <td>${escapeHtml(c.contractAlias)}</td>
                <td><span class="role-badge ${roleClass}">${ROLE_LABELS[c.role]}</span></td>
                <td>${CATEGORY_LABELS[c.category]}</td>
                <td>${c.principalAmount.toLocaleString()} / ${c.totalRemainingAmount.toLocaleString()}</td>
                <td>${c.thisMonthDueAmount.toLocaleString()}원</td>
                <td>${escapeHtml(c.maskedAccount) || '-'}</td>
                <td>${nearestDue}</td>
                <td><span class="status-pill ${statusClass}">${CONTRACT_STATUS_LABELS[c.contractStatus]}</span></td>
                <td><span class="payment-pill ${paymentClass}">${PAYMENT_STATUS_LABELS[c.paymentStatus]}</span></td>
                <td>${c.maturityDate}</td>
                <td><a class="detail-link" href="/contracts/${c.contractId}/schedule">보기</a></td>
            </tr>
                    `;
    });
}

function renderPagination(currentPageNum, totalPages) {
    const pagination = document.getElementById('pagination');
    pagination.innerHTML = '';

    for (let i = 1; i <= totalPages; i++) {
        const btn = document.createElement('button');
        btn.textContent = i;
        if (i === currentPageNum) btn.classList.add('is-active');
        btn.addEventListener('click', () => {
            currentPage = i;
            fetchDashboard();
        });
        pagination.appendChild(btn);
    }
}

document.getElementById('searchInput').addEventListener('input', (e) => {
    currentKeyword = e.target.value;
    currentPage = 1;
    fetchDashboard();
});

document.querySelectorAll('.role-toggle button').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.role-toggle button').forEach(b => b.classList.remove('is-active'));
        btn.classList.add('is-active');
        currentRoleFilter = btn.dataset.role;
        currentPage = 1;
        fetchDashboard();
    });
});

document.getElementById('sortSelect').addEventListener('change', (e) => {
    currentSortType = e.target.value;
    currentPage = 1;
    fetchDashboard();
});

function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

fetchDashboard();