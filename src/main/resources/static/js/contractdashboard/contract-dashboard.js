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

    authFetch(url)
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
    document.getElementById('totalLentAmount').textContent = summary.totalLentAmount.toLocaleString(undefined, {maximumFractionDigits: 0}) + '원';
    document.getElementById('totalBorrowedAmount').textContent = summary.totalBorrowedAmount.toLocaleString(undefined, {maximumFractionDigits: 0}) + '원';

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
        const nextDueAmount = c.nextDueAmount ? c.nextDueAmount.toLocaleString(undefined, {maximumFractionDigits: 0}) : '-';

        tbody.innerHTML += `
            <tr>
                <td>${escapeHtml(c.contractAlias)}</td>
                <td><span class="role-badge ${roleClass}">${ROLE_LABELS[c.role]}</span></td>
                <td>${CATEGORY_LABELS[c.category]}</td>
                <td>${c.principalAmount.toLocaleString(undefined, {maximumFractionDigits: 0})} / ${c.totalRemainingAmount.toLocaleString(undefined, {maximumFractionDigits: 0})}</td>
                <td>${nextDueAmount}원</td>
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

const syncButton = document.getElementById('btnSyncTransactions');

syncButton.addEventListener('click', syncTransactions);

async function syncTransactions() {
    const originalText = syncButton.textContent;

    try {
        syncButton.disabled = true;
        syncButton.textContent = '동기화 중...';

        const response = await authFetch('/api/transactions/sync', {
            method: 'POST'
        });

        const result = await readJsonSafely(response);

        if (!response.ok) {
            throw new Error(
                result?.message || '거래내역 동기화에 실패했습니다.'
            );
        }

        if (!result) {
            throw new Error('거래내역 동기화 결과를 확인할 수 없습니다.');
        }

        alert(
            `거래내역 동기화가 완료되었습니다.\n` +
            `자동 반영: ${result.appliedCount}건\n` +
            `확인 필요: ${result.needsCheckCount}건\n` +
            `미매칭: ${result.unmatchedCount}건\n` +
            `중복: ${result.duplicateCount}건\n` +
            `실패: ${result.failedCount}건`
        );

        // 상환 금액과 납부 상태를 다시 조회한다.
        fetchDashboard();

        await MatchingReviewModal.open({
            reviewChannel: 'TRANSACTION_HISTORY',
            targetType: 'LOAN'
        });
    } catch (error) {
        console.error('거래내역 동기화 실패:', error);

        alert(
            error.message || '거래내역 동기화에 실패했습니다.'
        );
    } finally {
        syncButton.disabled = false;
        syncButton.textContent = originalText;
    }
}

async function readJsonSafely(response) {
    const text = await response.text();

    if (!text) {
        return null;
    }

    try {
        return JSON.parse(text);
    } catch {
        return null;
    }
}

document.getElementById('btnCreateContract').addEventListener('click', () => {
    const overlay = document.getElementById('relationModalOverlay');
    if (overlay) {
        overlay.style.display = 'flex';
    }
})

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

document.addEventListener('matching-review:closed', fetchDashboard);
