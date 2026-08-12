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
        document.getElementById('requestedAt').textContent = formatDateTimeKorean(detail.requestedAt);
        document.getElementById('status').textContent = detail.status;

        document.getElementById('currentMaturityDate').textContent = detail.currentMaturityDate;
        document.getElementById('newMaturityDate').textContent = detail.newMaturityDate;

        document.getElementById('currentInterestRate').textContent = detail.currentInterestRate + '%';
        document.getElementById('newInterestRate').textContent = detail.newInterestRate + '%';

        document.getElementById('currentMonthlyPayment').textContent = formatCurrency(detail.currentMonthlyPayment);
        document.getElementById('newMonthlyPayment').textContent = formatCurrency(detail.newMonthlyPayment);

        document.getElementById('currentRepaymentType').textContent = detail.currentRepaymentType;
        document.getElementById('newRepaymentType').textContent = detail.newRepaymentType;

        document.getElementById('currentTerms').textContent = detail.currentTerms || '-';
        document.getElementById('newTerms').textContent = detail.newTerms || '-';

        document.getElementById('changeReason').textContent = detail.changeReason;

        document.getElementById('extendedMonths').textContent = formatExtendedMonths(detail.extendedMonths);
    })
    .catch(() => {
        alert('변경 요청 정보를 불러오는 중 오류가 발생했습니다.');
    });

function formatCurrency(amount) {
    return '₩' + Number(amount).toLocaleString(undefined, {maximumFractionDigits: 0});
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
    window.location.href = `/contracts/${contractId}/approve`;
});

function formatDateTimeKorean(dateString) {
    const dated = new Date(dateString);
    const year = dated.getFullYear();
    const month = dated.getMonth() + 1;
    const date = dated.getDate();
    const hours = dated.getHours();
    const minutes = dated.getMinutes();
    const seconds = dated.getSeconds();
    return `${year}년 ${month}월 ${date}일 ${hours}시 ${minutes}분 ${seconds}초`;
}

const rejectButton = document.getElementById('rejectButton');
const rejectModal = document.getElementById('rejectModal');
const rejectCancelButton = document.getElementById('rejectCancelButton');
const rejectConfirmButton = document.getElementById('rejectConfirmButton');
const returnReasonInput = document.getElementById('returnReasonInput');
const rejectError = document.getElementById('rejectError');

rejectButton.addEventListener('click', function () {
rejectModal.hidden = false;
});

rejectCancelButton.addEventListener('click', function () {
    rejectModal.hidden = true;
    returnReasonInput.value = '';
    rejectError.hidden = true;
});

rejectConfirmButton.addEventListener('click', function () {
    const returnReason = returnReasonInput.value.trim();

    if (!returnReason) {
        rejectError.textContent = '반려 사유를 입력해주세요.';
        rejectError.hidden = false;
        return;
    }

    fetch(`/api/contracts/${contractId}/change-requests/${changeRequestId}/reject`, {
        method: 'PATCH',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({returnReason: returnReason})
    })
.then(response => {
        if (!response.ok) {
            throw new Error('반려 처리 실패');
        }
        alert('변경 요청을 반려했습니다.');
        window.location.href = '/dashboard';
    })
        .catch(() => {
        rejectError.textContent = '반려 처리에 실패했습니다. 다시 시도해주세요.'
        rejectError.hidden = false;
        });
});