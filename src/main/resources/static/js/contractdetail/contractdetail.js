const contractId = document.getElementById('contractId').value;
const canRequestChange = document.getElementById('canRequestChange').value === 'true';

fetch(`/api/contracts/${contractId}/contract-detail`)
    .then(response => {
        if(!response.ok) {
            throw new Error("금전차용계약서 조회 실패")
        }
        return response.json();
    })
    .then(data => {

        document.getElementById('principalAmount').textContent = data.contract.principalAmount;
        document.getElementById('interestRate').textContent = data.contract.interestRate;
        document.getElementById('startDate').textContent = data.contract.startDate;
        document.getElementById('maturityDate').textContent = data.contract.maturityDate;
        document.getElementById('repaymentType').textContent = data.contract.repaymentType;
        document.getElementById('repaymentDay').textContent = data.contract.repaymentDay;
        document.getElementById('address').textContent = data.contract.address;
        document.getElementById('terms').textContent = data.contract.terms;
    })
    .catch(() => {
        alert('계약 정보를 불러오는 중 오류가 발생했습니다.')
    });

if (!canRequestChange) {
    document.getElementById('changeButton').style.display = 'none';

}

document.getElementById('changeButton').addEventListener('click', function () {
    window.location.href = `/contracts/${contractId}/change-request`;
});