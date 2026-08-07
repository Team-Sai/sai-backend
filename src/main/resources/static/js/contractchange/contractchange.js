const contractId = document.getElementById('contractId').value;

fetch(`/api/contracts/${contractId}`)
    .then(response => {
        if (!response.ok) {
            throw new Error(('계약 조회 실패'));
        }
        return response.json();
    })
    .then(contract => {
        document.getElementById('principalAmount').textContent = contract.principalAmount;
        document.getElementById('interestRate').textContent = contract.interestRate;
        document.getElementById('maturityDate').textContent = contract.maturityDate;
        document.getElementById('repaymentType').textContent = contract.repaymentType;
        document.getElementById('repaymentDay').textContent = contract.repaymentDay;
    })
    .catch(() => {
        alert('계약 정보를 불러오는 중 오류가 발생했습니다.');
    });


document.getElementById('changeRequestForm').addEventListener('submit', function (event) {
    event.preventDefault();

    const requestBody = {
        changeReason: document.getElementById('changeReason').value,
        newMaturityDate: document.getElementById('newMaturityDate').value,
        newInterestRate: document.getElementById('newInterestRate').value,
        newRepaymentType: document.getElementById('newRepaymentType').value,
        newRepaymentDate: document.getElementById('newRepaymentDate').value,

    };

    fetch(`/api/contracts/${contractId}/change-requests`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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