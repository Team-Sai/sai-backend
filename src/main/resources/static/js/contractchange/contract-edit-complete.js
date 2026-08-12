(function () {
  "use strict";

  const btnViewContract = document.getElementById("btnViewContract");
  const btnGoDashboard = document.getElementById("btnGoDashboard");

  const params = new URLSearchParams(window.location.search);
  const contractId = params.get("contractId");

  if (!contractId) {
    if (btnViewContract) btnViewContract.disabled = true;
  } else if (btnViewContract) {
    btnViewContract.addEventListener("click", () => {
      window.location.href = `/contracts/${encodeURIComponent(contractId)}/contract-detail`;
    });
  }

  btnGoDashboard?.addEventListener("click", () => {
    window.location.href = "/dashboard";
  });
})();