(function () {
  "use strict";

  const DRAFT_KEY = "debtorApprovalDraft";

  const card = document.getElementById("signatureCard");
  if (!card) return;

  const contractId = card.dataset.contractId;

  const statusEl = document.getElementById("formStatus");
  const agreeCheckbox = document.getElementById("agreeCheckbox");
  const submitBtn = document.getElementById("btnSubmit");

  const canvas = document.getElementById("signatureCanvas");
  const signPlaceholder = document.getElementById("signPlaceholder");
  const clearBtn = document.getElementById("clearSignature");
  const ctx = canvas.getContext("2d");
  ctx.lineWidth = 2.5;
  ctx.lineCap = "round";
  ctx.strokeStyle = "#181c1e";

  let hasSignature = false;
  let drawing = false;

  let draft = null;
  try {
    draft = JSON.parse(sessionStorage.getItem(DRAFT_KEY) || "null");
  } catch (err) {
    draft = null;
  }

  if (!draft || !draft.debtorAddress) {
    window.location.href = `/contracts/${contractId}/approve`;
    return;
  }

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

  clearBtn?.addEventListener("click", () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    hasSignature = false;
    if (signPlaceholder) signPlaceholder.hidden = false;
  });

  function canvasToBlob() {
    return new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
  }

  async function submitApproval() {
    const blob = await canvasToBlob();
    const formData = new FormData();
    formData.append("debtorAddress", draft.debtorAddress);
    formData.append("signature", blob, "signature.png");

    const response = await fetch(`api/contracts/${contractId}/approve`, {
      method: "PATCH",
      headers: authHeaders(),
      body: formData,
    });

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
      showStatus("전자 서명 동의에 체크해 주세요.", true);
      return;
    }

    submitBtn.disabled = true;
    showStatus("서명을 제출하는 중입니다...", false);

    try {
      await submitApproval();
      sessionStorage.removeItem(DRAFT_KEY);
      showStatus("서명이 제출되었습니다. 계약이 완료되었습니다.", false);
      window.location.href = `/contracts/complete?contractId=${encodeURIComponent(contractId)}`;
    } catch (err) {
      showStatus(err.message || "서명 제출에 실패했습니다. 잠시 후 다시 시도해 주세요.", true);
      submitBtn.disabled = false;
    }
  });
})();
