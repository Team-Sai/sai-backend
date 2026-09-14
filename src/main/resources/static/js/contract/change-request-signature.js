(function () {
  "use strict";

  const contractId = document.getElementById("contractId").value;
  const changeRequestId = document.getElementById("changeRequestId").value;

  const canvas = document.getElementById("signatureCanvas");
  const signPlaceholder = document.getElementById("signPlaceholder");
  const clearBtn = document.getElementById("clearSignature");
  const agreeCheckbox = document.getElementById("agreeCheckbox");
  const submitBtn = document.getElementById("btnSubmit");
  const cancelBtn = document.getElementById("btnCancel");
  const statusEl = document.getElementById("formStatus");
  const returnUrl = `/contracts/${contractId}/change-requests/${changeRequestId}/signature`;
  const identityVerificationId = new URLSearchParams(window.location.search).get("identityVerificationId");
  if (!identityVerificationId) {
    window.location.href = `/identity-test?returnTo=${encodeURIComponent(returnUrl)}`;
    return;
  }

  if (!canvas) return;

  const ctx = canvas.getContext("2d");
  ctx.lineWidth = 2.5;
  ctx.lineCap = "round";
  ctx.strokeStyle = "#181c1e";

  let hasSignature = false;
  let drawing = false;

  function authHeaders(extra) {
    const token = sessionStorage.getItem("accessToken");
    return Object.assign(
        token ? { Authorization: `Bearer ${token}` } : {},
        extra || {}
    );
  }

  function showStatus(message, isError) {
    statusEl.textContent = message;
    statusEl.classList.toggle("is-error", Boolean(isError));
  }

  if (!contractId || !changeRequestId) {
    alert("잘못된 접근입니다. 변경 요청 화면으로 돌아갑니다.");
    window.location.href = "/contract";
    return;
  }

  cancelBtn?.addEventListener("click", async () => {
    const confirmed = confirm("작성 중인 변경 요청을 취소하시겠습니까?");
    if (!confirmed) return;

    try {
      const response = await fetch(`/api/contracts/${contractId}/change-requests/${changeRequestId}`, {
        method: "DELETE",
        headers: authHeaders(),
      });

      if (!response.ok) {
        const body = await response.json().catch(() => null);
        showStatus(body?.message || "취소에 실패했습니다. 잠시 후 다시 시도해 주세요.", true);
        return;
      }
    } catch (err) {
      showStatus("취소 요청 중 오류가 발생했습니다. 네트워크를 확인해 주세요.", true);
      return;
    }

    window.location.href = `/contracts/${encodeURIComponent(contractId)}/contract-detail`;
  });

  function canvasPoint(event) {
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const source = event.touches ? event.touches[0] : event;
    return {
      x: (source.clientX - rect.left) * scaleX,
      y: (source.clientY - rect.top) * scaleY,
    };
  }

  function startDraw(event) {
    event.preventDefault();
    drawing = true;
    if (signPlaceholder) signPlaceholder.hidden = true;
    const { x, y } = canvasPoint(event);
    ctx.beginPath();
    ctx.moveTo(x, y);
  }

  function moveDraw(event) {
    if (!drawing) return;
    event.preventDefault();
    const { x, y } = canvasPoint(event);
    ctx.lineTo(x, y);
    ctx.stroke();
    hasSignature = true;
  }

  function endDraw() {
    drawing = false;
  }

  canvas.addEventListener("mousedown", startDraw);
  canvas.addEventListener("mousemove", moveDraw);
  window.addEventListener("mouseup", endDraw);

  canvas.addEventListener("touchstart", startDraw, { passive: false });
  canvas.addEventListener("touchmove", moveDraw, { passive: false });
  canvas.addEventListener("touchend", endDraw);

  function clearSignature() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    hasSignature = false;
    if (signPlaceholder) signPlaceholder.hidden = false;
  }

  clearBtn?.addEventListener("click", clearSignature);

  function canvasToBlob() {
    return new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
  }

  async function submitSignature() {
    const blob = await canvasToBlob();
    const formData = new FormData();
    formData.append("signature", blob, "signature.png");
    formData.append("identityVerificationId", identityVerificationId);

    const response = await fetch(
        `/api/contracts/${contractId}/change-requests/${changeRequestId}/signature`,
        { method: "PATCH", headers: authHeaders(), body: formData }
    );

    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new Error(body?.message || `HTTP ${response.status}`);
    }
    return response.json();
  }

  submitBtn?.addEventListener("click", async () => {
    if (!hasSignature) {
      showStatus("서명 패드에 서명을 남겨 주세요.", true);
      return;
    }

    if (!agreeCheckbox?.checked) {
      showStatus("변경 내용 확인 및 전자 서명 동의에 체크해 주세요.", true);
      return;
    }

    submitBtn.disabled = true;
    showStatus("변경 요청을 전송하는 중입니다...", false);

    try {
      await submitSignature();

      window.location.href =
          `/contracts/edit-complete?contractId=${encodeURIComponent(contractId)}`;
    } catch (err) {
      showStatus(err.message || "변경 요청 전송에 실패했습니다. 잠시 후 다시 시도해 주세요.", true);
      submitBtn.disabled = false;
    }
  });
})();
