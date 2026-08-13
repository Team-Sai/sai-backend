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

  function authHeaders(extra) {
    const token = sessionStorage.getItem("accessToken");
    return Object.assign(
        token ? { Authorization: `Bearer ${token}` } : {},
        extra || {}
    );
  }

  btnViewContract.addEventListener("click", () => {
    window.location.href = `/contracts/${encodeURIComponent(contractId)}/contract-detail`;
  });

  btnGoToDashboard?.addEventListener("click", () => {
    window.location.href = "/dashboard";
  });

  fetch(`/api/contracts/${contractId}/listdetails`, {
    method: "GET",
    headers: authHeaders({ Accept: "application/json" }),
  })
      .then((response) => (response.ok ? response.json() : null))
      .then((data) => {
        if (data && data.previousContractId && successTitle) {
          successTitle.textContent = "계약 변경이 완료되었습니다!";
        }
      })
      .catch(() => {});
})();