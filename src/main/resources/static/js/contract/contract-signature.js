(function () {
  "use strict";

  const DRAFT_KEY = "loanContractDraft";

  const debtorUserTokenInput = document.getElementById("debtorUserToken");
  const canvas = document.getElementById("signatureCanvas");
  const signPlaceholder = document.getElementById("signPlaceholder");
  const clearBtn = document.getElementById("clearSignature");
  const agreeCheckbox = document.getElementById("agreeCheckbox");
  const submitBtn = document.getElementById("btnSubmit");
  const cancelBtn = document.getElementById("btnCancel");
  const statusEl = document.getElementById("formStatus");

  if (!canvas) return;

  const ctx = canvas.getContext("2d");
  ctx.lineWidth = 2.5;
  ctx.lineCap = "round";
  ctx.strokeStyle = "#181c1e";

  let hasSignature = false;
  let drawing = false;

  function showStatus(message, isError) {
    statusEl.textContent = message;
    statusEl.classList.toggle("is-error", Boolean(isError));
  }

  let draft = null;
  try {
    draft = JSON.parse(sessionStorage.getItem(DRAFT_KEY) || "null");
  } catch (err) {
    draft = null;
  }

  if (!draft) {
    alert("작성 중인 차용증 정보가 없습니다. 처음부터 다시 시도해 주세요.");
    window.location.href = "/contracts/new";
    return;
  }

  const returnUrl = "/contracts/signature";
  const identityVerificationId = new URLSearchParams(window.location.search).get("identityVerificationId");
  if (!identityVerificationId) {
    window.location.href = `/identity-test?returnTo=${encodeURIComponent(returnUrl)}`;
    return;
  }

  cancelBtn?.addEventListener("click", () => {
    window.location.href = "/contracts/new";
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
    const payload = {
      ...draft,
      identityVerificationId,
    };

    const response = await authFetch("/api/contracts/write", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload),
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
    formData.append("debtorUserToken", debtorUserTokenInput.value.trim());
    formData.append("signature", blob, "signature.png");

    const response = await authFetch(
        `/api/contracts/${contractId}/signature`,
        {
          method: "PATCH",
          body: formData,
        }
    );

    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new Error(body?.message || `HTTP ${response.status}`);
    }
    return response.json();
  }

  let createdContractId = null;

  submitBtn?.addEventListener("click", async () => {
    if (!debtorUserTokenInput.value.trim()) {
      showStatus("차용증을 받을 채무자의 회원 토큰을 입력해 주세요.", true);
      debtorUserTokenInput.focus();
      return;
    }

    if (!hasSignature) {
      showStatus("서명 패드에 서명을 남겨 주세요.", true);
      return;
    }

    if (!agreeCheckbox?.checked) {
      showStatus("약정 내용 확인 및 전자 서명 동의에 체크해 주세요.", true);
      return;
    }

    submitBtn.disabled = true;
    showStatus("차용증을 전송하는 중입니다...", false);

    try {
      // 본인인증은 1회용이라 계약 생성 성공 후 서명 업로드에서 실패하면
      // createContract를 재호출하지 않고 이미 생성된 contractId로 서명만 재시도한다.
      if (createdContractId == null) {
        createdContractId = await createContract();
      }
      await submitSignature(createdContractId);

      sessionStorage.removeItem(DRAFT_KEY);
      alert("차용증이 전송되었습니다.");
      window.location.href = "/mypage";
    } catch (err) {
      showStatus(err.message || "차용증 전송에 실패했습니다. 잠시 후 다시 시도해 주세요.", true);
      submitBtn.disabled = false;
    }
  });
})();