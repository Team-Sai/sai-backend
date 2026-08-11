(function () {
  "use strict";

  const btnViewContract = document.getElementById("btnViewContract");
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
})();
