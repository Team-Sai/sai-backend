const contractId = document.getElementById('contractId').value;
const changeRequestId = document.getElementById('changeRequestId').value;

function authHeaders(extra) {
    const token = sessionStorage.getItem("accessToken");
    return Object.assign(
        token ? { Authorization: `Bearer ${token}` } : {},
        extra || {}
    );
}

fetch(`/api/contracts/${contractId}/change-requests/${changeRequestId}`, { headers: authHeaders() })
    .then(response => {
        if (!response.ok) {
            throw new Error('변경 요청 조회 실패');
        }
        return response.json();
    })
    .then(detail => {
        document.getElementById('requesterName').textContent = detail.requesterName;
        document.getElementById('requesterNameSide').textContent = detail.requesterName;
        document.getElementById('requestedAt').textContent = detail.requestedAt;
        document.getElementById('status').textContent = detail.status;

        document.getElementById('currentMaturityDate').textContent = detail.currentMaturityDate;
        document.getElementById('newMaturityDate').textContent = detail.newMaturityDate;

        document.getElementById('currentInterestRate').textContent = detail.currentInterestRate + '%';
        document.getElementById('newInterestRate').textContent = detail.newInterestRate + '%';

        document.getElementById('currentMonthlyPayment').textContent = formatCurrency(detail.currentMonthlyPayment);
        document.getElementById('newMonthlyPayment').textContent = formatCurrency(detail.newMonthlyPayment);

        document.getElementById('currentRepaymentType').textContent = detail.currentRepaymentType;
        document.getElementById('newRepaymentType').textContent = detail.newRepaymentType;

        document.getElementById('changeReason').textContent = detail.changeReason;

        document.getElementById('extendedMonths').textContent = formatExtendedMonths(detail.extendedMonths);
    })
    .catch(() => {
        alert('변경 요청 정보를 불러오는 중 오류가 발생했습니다.');
    });

function formatCurrency(amount) {
    return '₩' + Number(amount).toLocaleString();
}

function formatExtendedMonths(months) {
    if (months > 0) {
        return `${months}개월 연장`;
    } else if (months < 0) {
        return `${Math.abs(months)}개월 단축`;
    } else {
        return '변경 없음';
    }
}

document.getElementById('approveButton').addEventListener('click', function () {
    alert('승인 기능은 준비 중입니다.');
});
