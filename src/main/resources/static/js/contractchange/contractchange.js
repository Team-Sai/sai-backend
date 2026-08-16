const contractId = document.getElementById('contractId').value;
let fullTerms = '';

const REPAYMENT_TYPE_LABELS = {
    EQUAL_PRINCIPAL_AND_INTEREST: '원리금균등상환',
    EQUAL_PRINCIPAL: '원금균등상환',
    BULLET_REPAYMENT: '만기일시상환'
};

authFetch(
    `/api/contracts/${contractId}`)
    .then(response => {
        if (!response.ok) {
            throw new Error('계약 조회 실패');
        }
        return response.json();
    })
    .then(contract => {
        document.getElementById('principalAmount').textContent = contract.principalAmount.toLocaleString(undefined, {maximumFractionDigits: 0}) + '원';
        document.getElementById('interestRate').textContent = contract.interestRate + '%';
        document.getElementById('maturityDate').textContent = contract.maturityDate;
        document.getElementById('repaymentType').textContent =
            REPAYMENT_TYPE_LABELS[contract.repaymentType] || contract.repaymentType;
        document.getElementById('repaymentDay').textContent = '매월 ' + contract.repaymentDay + '일';

        fullTerms = contract.terms || '';
        renderTermsPreview();
    })
    .catch(() => {
        alert('계약 정보를 불러오는 중 오류가 발생했습니다.');
    });

function renderTermsPreview() {
    const preview = document.getElementById('currentTermsPreview');
    const toggleBtn = document.getElementById('termsToggleBtn');
    const LIMIT = 20;

    if (!fullTerms) {
        preview.textContent = '없음';
        toggleBtn.hidden = true;
        return;
    }

    if (fullTerms.length <= LIMIT) {
        preview.textContent = fullTerms;
        toggleBtn.hidden = true;
        return;
    }

    preview.textContent = fullTerms.slice(0, LIMIT) + '...';
    toggleBtn.hidden = false;
    toggleBtn.textContent = '더보기';

    let expanded = false;
    toggleBtn.onclick = () => {
        expanded = !expanded;
        preview.textContent = expanded ? fullTerms : fullTerms.slice(0, LIMIT) + '...';
        toggleBtn.textContent = expanded ? '접기' : '더보기';
    };
}

function validate() {
    const reason = document.getElementById('changeReason').value.trim();
    const newMaturityDate = document.getElementById('newMaturityDate').value;
    const newInterestRate = document.getElementById('newInterestRate').value;
    const newRepaymentType = document.getElementById('newRepaymentType').value;
    const newRepaymentDate = document.getElementById('newRepaymentDate').value;
    const newTerms = document.getElementById('newTerms').value.trim();

    const hasAnyChange = newMaturityDate || newInterestRate || newRepaymentType || newRepaymentDate || newTerms;

    if (!hasAnyChange) {
        const wantsToCancel = confirm('변경하려는 내용이 없습니다. 계약 조건 변경을 취소하시겠습니까?');
        if (wantsToCancel) {
            document.getElementById('changeRequestForm').reset();
        }
        return false;
    }

    if (!reason) {
        alert('변경 사유를 입력해주세요.');
        return false;
    }

    if (newInterestRate !== '') {
        const rate = Number(newInterestRate);
        if (!Number.isFinite(rate) || rate < 0.5 || rate > 20 || !Number.isInteger(rate * 2)) {
            alert('이율은 0.5% 이상 20% 이하이며, 0.5% 단위여야 합니다.');
            return false;
        }
    }

    if (newRepaymentDate !== '') {
        const day = Number(newRepaymentDate);
        if (day < 1 || day > 31) {
            alert('상환일은 1일에서 31일 사이여야 합니다.');
            return false;
        }
    }

    return true;
}

document.getElementById('changeRequestForm').addEventListener('submit', function (event) {
    event.preventDefault();

    if (!validate()) return;

    const requestBody = {
        changeReason: document.getElementById('changeReason').value,
        newMaturityDate: document.getElementById('newMaturityDate').value || null,
        newInterestRate: document.getElementById('newInterestRate').value || null,
        newRepaymentType: document.getElementById('newRepaymentType').value || null,
        newRepaymentDate: document.getElementById('newRepaymentDate').value || null,
        newTerms: document.getElementById('newTerms').value.trim() || null,
    };

    authFetch(`/api/contracts/${contractId}/change-requests`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestBody)
    })
        .then(response => {
            if (!response.ok) {
                throw new Error('요청 실패');
            }
            return response.json();
        })
        .then(() => {
            window.location.href = `/contracts/edit-complete?contractId=${encodeURIComponent(contractId)}`;
        })
        .catch(() => {
            alert('변경 요청 중 오류가 발생했습니다.');
        });
});

document.getElementById('cancelButton').addEventListener('click', function () {
    document.getElementById('changeRequestForm').reset();
});
