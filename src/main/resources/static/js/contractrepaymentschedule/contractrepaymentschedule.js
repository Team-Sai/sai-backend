const contractId = document.getElementById('contractId').value;

let allSchedules = [];
let currentPage = 1;
const PAGE_SIZE = 5;

const REPAYMENT_TYPE_LABELS = {
    EQUAL_PRINCIPAL_AND_INTEREST: '원리금균등상환',
    EQUAL_PRINCIPAL: '원금균등상환',
    BULLET_REPAYMENT: '만기일시상환'
};

const CONTRACT_STATUS_LABELS = {
    DRAFT: '작성중',
    PENDING: '서명대기',
    COMPLETED: '진행중'
};

Promise.all([
    authFetch(
        `/api/contracts/${contractId}/schedules`
    ),
    authFetch(
        `/api/contracts/${contractId}/contract-detail`
    )
])
    .then(([scheduleRes, contractRes]) => {
        if (!scheduleRes.ok || !contractRes.ok) {
            throw new Error('조회 실패');
        }
        return Promise.all([scheduleRes.json(), contractRes.json()]);
    })
    .then(([scheduleData, contractData]) => {
        const contract = contractData.contract;

        document.getElementById('statusBadge').textContent =
            CONTRACT_STATUS_LABELS[contract.status] || contract.status;
        document.getElementById('contractAlias').textContent = contract.contractAlias;

        document.getElementById('totalScheduledAmount').textContent =
            scheduleData.totalScheduledAmount.toLocaleString(undefined, {maximumFractionDigits: 0}) + '원';
        document.getElementById('paidAmount').textContent =
            scheduleData.paidAmount.toLocaleString(undefined, {maximumFractionDigits: 0}) + '원';
        document.getElementById('remainingAmount').textContent =
            scheduleData.remainingAmount.toLocaleString(undefined, {maximumFractionDigits: 0}) + '원';
        document.getElementById('progressLabel').textContent =
            `${scheduleData.paidCount} / ${scheduleData.totalCount}회차`;

        const progressPercent = scheduleData.totalCount === 0
            ? 0
            : (scheduleData.paidCount / scheduleData.totalCount) * 100;
        document.getElementById('progressBarFill').style.width = `${progressPercent}%`;

        document.getElementById('createdAt').textContent = formatDateTimeKorean(contract.createdAt);
        document.getElementById('interestRate').textContent = contract.interestRate + '%';
        document.getElementById('maturityDate').textContent = contract.maturityDate;
        document.getElementById('nextDueDate').textContent = scheduleData.nextDueDate || '없음';
        document.getElementById('repaymentType').textContent =
            REPAYMENT_TYPE_LABELS[contract.repaymentType] || contract.repaymentType;

        allSchedules = scheduleData.schedules;
        renderSchedulePage();
    })
    .catch(() => {
        alert('정보를 불러오는 데 실패했습니다.');
    });

function renderSchedulePage() {
    const tbody = document.getElementById('scheduleTableBody');
    tbody.innerHTML = '';

    const startIndex = (currentPage - 1) * PAGE_SIZE;
    const pageItems = allSchedules.slice(startIndex, startIndex + PAGE_SIZE);

    pageItems.forEach(s => {
        const statusClass = s.status === 'PAID' ? 'status-paid' : 'status-pending';
        const statusText = s.status === 'PAID' ? '납부완료' : '납부예정';

        tbody.innerHTML += `
            <tr>
                <td>${s.sequence}회차</td>
                <td>${s.dueDate}</td>
                <td>${s.totalPaymentDue.toLocaleString(undefined, {maximumFractionDigits: 0})}원</td>
                <td>${s.paidAt ? s.paidAt : '-'}</td>
                <td><span class="${statusClass}">${statusText}</span></td>
            </tr>
        `;
    });

    renderPagination();
}

function renderPagination() {
    const pagination = document.getElementById('schedulePagination');
    pagination.innerHTML = '';

    const totalPages = Math.ceil(allSchedules.length / PAGE_SIZE);
    if (totalPages <= 1) return;

    for (let i = 1; i <= totalPages; i++) {
        const btn = document.createElement('button');
        btn.textContent = i;
        if (i === currentPage) btn.classList.add('is-active');
        btn.addEventListener('click', () => {
            currentPage = i;
            renderSchedulePage();
        });
        pagination.appendChild(btn);
    }
}

function formatDateTimeKorean(dateString) {
    const dated = new Date(dateString);
    const year = dated.getFullYear();
    const month = dated.getMonth() + 1;
    const date = dated.getDate();
    const hours = dated.getHours();
    const minutes = dated.getMinutes();
    const seconds = dated.getSeconds();
    return `${year}년 ${month}월 ${date}일  ${hours}시 ${minutes}분 ${seconds}초`;
}

document.getElementById('btnViewContract').addEventListener('click', function () {
    window.location.href = `/contracts/${contractId}/contract-detail`;
});