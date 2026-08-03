(function () {
  "use strict";

  const DRAFT_KEY = "loanContractDraft";

  const closeBtn = document.getElementById("button");
  const debtorEmailInput = document.getElementById("debtorEmail");
  const canvas = document.getElementById("signatureCanvas");
  const signPlaceholder = document.getElementById("container7");
  const clearBtn = document.getElementById("button2");
  const agreeCheckbox = document.getElementById("agreeCheckbox");
  const submitBtn = document.getElementById("button3");
  const statusEl = document.getElementById("formStatus");

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
    statusEl.hidden = !message;
    statusEl.classList.toggle("is-error", Boolean(isError));
  }


  let draft = null;
  try {
    draft = JSON.parse(sessionStorage.getItem(DRAFT_KEY) || "null");
  } catch (err) {
    draft = null;
  }

  if (!draft) {
    window.location.href = "/api/contracts";
    return;
  }

  closeBtn?.addEventListener("click", () => {
    window.location.href = "/api/contracts";
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

  async function createContract() {
    const response = await fetch("/api/contracts/write", {
      method: "POST",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify(
        Object.assign({}, draft, { debtorEmail: debtorEmailInput.value.trim() })
      ),
    });

    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new Error(body?.message || `HTTP ${response.status}`);
    }
    return response.json();
  }

  function canvasToBlob() {
    return new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
  }

  async function submitSignature(contractId) {
    const blob = await canvasToBlob();
    const formData = new FormData();
    formData.append("signature", blob, "signature.png");

    const response = await fetch(`/api/contracts/${contractId}/signature`, {
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
    if (!debtorEmailInput.value.trim() || !debtorEmailInput.checkValidity()) {
      debtorEmailInput.classList.add("is-invalid");
      showStatus("채무자의 이메일을 올바르게 입력해 주세요.", true);
      debtorEmailInput.focus();
      return;
    }
    debtorEmailInput.classList.remove("is-invalid");

    if (!hasSignature) {
      showStatus("서명 패드에 서명을 남겨 주세요.", true);
      return;
    }

    if (!agreeCheckbox?.checked) {
      showStatus("약정 내용 확인 및 전자 서명 동의에 체크해 주세요.", true);
      return;
    }

    submitBtn.disabled = true;
    showStatus("서명을 제출하는 중입니다...", false);

    try {
      const contractId = await createContract();
      await submitSignature(contractId);

      sessionStorage.removeItem(DRAFT_KEY);
      showStatus("서명이 제출되었습니다. 채무자에게 전송되었습니다.", false);
    } catch (err) {
      showStatus(err.message || "서명 제출에 실패했습니다. 잠시 후 다시 시도해 주세요.", true);
      submitBtn.disabled = false;
    }
  });
})();
