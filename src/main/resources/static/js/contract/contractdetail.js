const contractId = document.getElementById('contractId').value;
const changeButton = document.getElementById('changeButton');
const pendingChangeNotice = document.getElementById('pendingChangeNotice');

const REPAYMENT_TYPE_LABELS = {
    EQUAL_PRINCIPAL_AND_INTEREST: '원리금균등상환',
    EQUAL_PRINCIPAL: '원금균등상환',
    BULLET_REPAYMENT: '만기일시상환'
};

authFetch(
    `/api/contracts/${contractId}/contract-detail`
)
    .then(response => {
        if (!response.ok) {
            throw new Error("금전차용계약서 조회 실패");
        }
        return response.json();
    })
    .then(data => {
        document.getElementById('principalAmount').textContent = data.contract.principalAmount.toLocaleString(undefined, {maximumFractionDigits: 0});
        document.getElementById('interestRate').textContent = data.contract.interestRate;
        document.getElementById('startDate').textContent = data.contract.startDate;
        document.getElementById('maturityDate').textContent = data.contract.maturityDate;
        document.getElementById('repaymentType').textContent =
            REPAYMENT_TYPE_LABELS[data.contract.repaymentType] || data.contract.repaymentType;
        document.getElementById('repaymentDay').textContent = data.contract.repaymentDay;
        document.getElementById('termsSection').hidden = !data.contract.terms;
        document.getElementById('creditorName').textContent = data.contract.creditorName;
        document.getElementById('debtorName').textContent = data.contract.debtorName;
        document.getElementById('creditorNameCell').textContent = data.contract.creditorName;
        document.getElementById('debtorNameCell').textContent = data.contract.debtorName;

        changeButton.hidden = !data.canRequestChange;
        pendingChangeNotice.hidden = !(data.isCreditor && !data.canRequestChange);
    })
    .catch(() => {
        alert('계약 정보를 불러오는 중 오류가 발생했습니다.');
    });

changeButton.addEventListener('click', function () {
    window.location.href = `/contracts/${contractId}/change-request`;
});