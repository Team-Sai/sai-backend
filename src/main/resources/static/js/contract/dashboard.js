document.addEventListener('DOMContentLoaded', () => {
    const list = document.getElementById('contract-list');
    const empty = document.getElementById('contract-empty-state');
    const search = document.getElementById('contract-search');
    const status = document.getElementById('contract-status');
    const sort = document.getElementById('contract-sort');
    const toast = document.getElementById('toast');
    let rows = [];
    let currentPage = 1;

    const escapeHtml = value => String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');

    function updateSyncTime() {
        document.getElementById('sync-time').textContent = new Intl.DateTimeFormat('ko-KR', {
            hour: '2-digit', minute: '2-digit'
        }).format(new Date());
    }

    function showToast(message, isError = false) {
        toast.textContent = message;
        toast.classList.toggle('error', isError);
        toast.classList.add('visible');
        window.clearTimeout(showToast.timer);
        showToast.timer = window.setTimeout(() => toast.classList.remove('visible'), 4000);
    }

    function render(items) {
        list.innerHTML = '';
        empty.hidden = items.length !== 0;
        list.hidden = items.length === 0;
        items.forEach(c => {
            const row = document.createElement('article');
            row.className = 'settlement-table settlement-row contract2-row-wide';
            row.innerHTML = `<div class="settlement-name"><span>${escapeHtml(c.contractAlias || '차용증')}</span></div>
                <span class="status-badge ${c.role === 'CREDITOR' ? 'badge-role' : 'badge-debtor'}">${c.role === 'CREDITOR' ? '채권자' : '채무자'}</span>
                <span>${Number(c.principalAmount || 0).toLocaleString()}원 / ${Number(c.totalRemainingAmount || 0).toLocaleString()}원</span>
                <span>${Number(c.nextDueAmount || 0).toLocaleString()}원</span>
                <span>${escapeHtml(c.nearestScheduleDueDate || '-')}</span>
                <span class="status-badge ${c.repaymentStatus === 'COMPLETED' ? 'badge-completed' : 'badge-progress'}">${c.repaymentStatus === 'COMPLETED' ? '완료' : '진행 중'}</span>
                <span>${escapeHtml(c.maturityDate || '-')}</span>
                <a class="detail-link" href="/contracts/${encodeURIComponent(c.contractId)}/schedule" aria-label="계약 상세 보기">›</a>`;
            list.appendChild(row);
        });
    }

    function apply() {
        const keyword = search.value.trim().toLowerCase();
        render(rows.filter(c => (!keyword || String(c.contractAlias || '').toLowerCase().includes(keyword))
            && (status.value === 'ALL' || (status.value === 'COMPLETED' ? c.repaymentStatus === 'COMPLETED' : c.repaymentStatus !== 'COMPLETED'))));
    }

    async function load() {
        const url = `/api/dashboard?keyword=${encodeURIComponent(search.value)}&roleFilter=ALL&statusFilter=${status.value}&sortType=${sort.value}&page=${currentPage}`;
        const response = await authFetch(url);
        if (!response.ok) throw new Error('차용증 조회에 실패했습니다.');
        const data = await response.json();
        rows = data.contracts || [];
        document.getElementById('contract-total-count').textContent = data.summary?.totalContractCount || rows.length;
        document.getElementById('lent-amount').textContent = Number(data.summary?.totalLentAmount || 0).toLocaleString();
        document.getElementById('borrowed-amount').textContent = Number(data.summary?.totalBorrowedAmount || 0).toLocaleString();
        document.getElementById('receivable-count').textContent = `${data.summary?.receivableCount ?? 0}건`;
        document.getElementById('payable-count').textContent = `${data.summary?.payableCount ?? 0}건`;
        renderPagination(data.currentPage || 1, data.totalPages || 1);
        apply();
    }

    search.addEventListener('input', apply);
    status.addEventListener('change', () => { currentPage = 1; load(); });
    sort.addEventListener('change', () => { currentPage = 1; load(); });
    const openRelationModal = () => {
        const overlay = document.getElementById('relationModalOverlay');
        if (overlay) overlay.style.display = 'flex';
    };
    document.getElementById('create-contract-button').addEventListener('click', openRelationModal);
    document.getElementById('empty-create-button').addEventListener('click', openRelationModal);
    document.getElementById('sync-button').addEventListener('click', syncTransactions);

    function renderPagination(currentPageNum, totalPages) {
        const pagination = document.getElementById('pagination');
        pagination.innerHTML = '';
        pagination.hidden = totalPages <= 1;
        if (totalPages <= 1) return;

        for (let i = 1; i <= totalPages; i++) {
            const btn = document.createElement('button');
            btn.textContent = i;
            if (i === currentPageNum) btn.classList.add('is-active');
            btn.addEventListener('click', () => {
                currentPage = i;
                load();
            });
            pagination.appendChild(btn);
        }
    }

    async function syncTransactions() {
        const button = document.getElementById('sync-button');
        try {
            button.disabled = true;
            const response = await authFetch('/api/transactions/sync', { method: 'POST' });
            if (!response.ok) {
                const result = await response.json().catch(() => null);
                throw new Error(result?.message || '거래내역 동기화에 실패했습니다.');
            }
            const result = await response.json();
            showToast(`동기화 완료: 자동반영 ${result.appliedCount ?? 0}건 · 확인필요 ${result.needsCheckCount ?? 0}건 · 미매칭 ${result.unmatchedCount ?? 0}건`);
            updateSyncTime();
            await load();
            await new Promise(resolve => window.setTimeout(resolve, 1250));
            await MatchingReviewModal.open({ reviewChannel: 'TRANSACTION_HISTORY', targetType: 'LOAN' });
        } catch (error) {
            console.error('차용증 거래내역 동기화 실패:', error);
            showToast(error.message || '거래내역 동기화에 실패했습니다.', true);
        } finally {
            button.disabled = false;
        }
    }

    updateSyncTime();
    load().catch(() => { alert('차용증 정보를 불러오는 데 실패했습니다.'); });
});
