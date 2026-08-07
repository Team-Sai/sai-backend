function authHeaders(extra) {
    const token = sessionStorage.getItem("accessToken");
    return Object.assign(
        token ? { Authorization: `Bearer ${token}` } : {},
        extra || {}
    );
}

const contractId = document.getElementById('contractId').value;

Promise.all([
    fetch(`/api/contracts/${contractId}/schedules`, { headers: authHeaders() }),
    fetch(`/api/contracts/${contractId}/contract-detail`, { headers: authHeaders() })
])
    .then(([scheduleRes, contractRes]) => {
        if (!scheduleRes.ok || !contractRes.ok) {
            throw new Error('조회 실패');
        }
        return Promise.all([scheduleRes.json(), contractRes.json()]);
    })
    .then(([scheduleData, contractData]) => {
        const contract = contractData.contract;

        const CONTRACT_STATUS_LABELS = {
            DRAFT: '작성중',
            PENDING: '서명대기',
            COMPLETED: '진행중'
        };

        document.getElementById('statusBadge').textContent =
            CONTRACT_STATUS_LABELS[contract.status] || contract.status;
        document.getElementById('contractAlias').textContent = contract.contractAlias;

        document.getElementById('totalScheduledAmount').textContent =
            scheduleData.totalScheduledAmount.toLocaleString() + '원';
        document.getElementById('paidAmount').textContent =
            scheduleData.paidAmount.toLocaleString() + '원';
        document.getElementById('remainingAmount').textContent =
            scheduleData.remainingAmount.toLocaleString() + '원';
        document.getElementById('progressLabel').textContent =
            `${scheduleData.paidCount} / ${scheduleData.totalCount}회차`;

        const progressPercent = scheduleData.totalCount === 0
            ? 0
            : (scheduleData.paidCount / scheduleData.totalCount) * 100;
        document.getElementById('progressBarFill').style.width = `${progressPercent}%`;

        document.getElementById('createdAt').textContent = contract.createdAt;
        document.getElementById('interestRate').textContent = contract.interestRate + '%';
        document.getElementById('maturityDate').textContent = contract.maturityDate;
        const REPAYMENT_TYPE_LABELS = {
            EQUAL_PRINCIPAL_AND_INTEREST: '원리금균등상환',
            EQUAL_PRINCIPAL: '원금균등상환',
            BULLET_REPAYMENT: '만기일시상환'
        };

        document.getElementById('repaymentType').textContent =
            REPAYMENT_TYPE_LABELS[contract.repaymentType] || contract.repaymentType;

        const tbody = document.getElementById('scheduleTableBody');
        scheduleData.schedules.forEach(s => {
            const statusClass = s.status === 'PAID' ? 'status-paid' : 'status-pending';
            const statusText = s.status === 'PAID' ? '납부완료' : '납부예정';

            tbody.innerHTML += `
                <tr>
                    <td>${s.sequence}회차</td>
                    <td>${s.dueDate}</td>
                    <td>${s.totalPaymentDue.toLocaleString()}원</td>
                    <td>${s.paidAt ? s.paidAt : '-'}</td>
                    <td><span class="${statusClass}">${statusText}</span></td>
                </tr>
            `;
        });
    })
    .catch(() => {
        alert('정보를 불러오는 데 실패했습니다.');
    });

document.getElementById('btnViewContract').addEventListener('click', function () {
    window.location.href = `/contracts/${contractId}/contract-detail`;
});