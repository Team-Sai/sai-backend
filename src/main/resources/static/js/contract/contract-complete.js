(function () {
  "use strict";

  const btnViewContract = document.getElementById("btnViewContract");
  const btnGoToDashboard = document.getElementById("btnGoToDashboard");
  const successTitle = document.getElementById("successTitle");
  if (!btnViewContract) return;

  const params = new URLSearchParams(window.location.search);
  const contractId = params.get("contractId");

  if (!contractId) {
    btnViewContract.disabled = true;
    return;
  }
  btnViewContract.addEventListener("click", () => {
    window.location.href = `/contracts/${encodeURIComponent(contractId)}/contract-detail`;
  });

  btnGoToDashboard?.addEventListener("click", () => {
    window.location.href = "/contract";
  });

  authFetch(`/api/contracts/${contractId}/listdetails`, {
    method: "GET",
    headers: {
      Accept: "application/json"
    },
  })
      .then((response) => (response.ok ? response.json() : null))
      .then((data) => {
        if (data && data.previousContractId && successTitle) {
          successTitle.textContent = "계약 변경이 완료되었습니다!";
        }
      })
      .catch(() => {});
})();
