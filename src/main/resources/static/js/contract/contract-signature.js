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
  const identityPanel = document.getElementById("identityPanel");
  const identityStatusText = document.getElementById("identityStatusText");
  const btnIdentityVerify = document.getElementById("btnIdentityVerify");
  const contractFields = document.getElementById("contractFields");

  if (!canvas) return;

  let identityVerificationId = null;

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

  function setIdentityStatus(message, isError) {
    if (!identityStatusText) return;
    identityStatusText.textContent = message;
    identityStatusText.classList.toggle("is-error", Boolean(isError));
  }

  function setIdentityVerifying(loading) {
    if (!btnIdentityVerify) return;
    btnIdentityVerify.disabled = loading;
    btnIdentityVerify.textContent = loading ? "본인인증 처리 중..." : "본인인증 시작";
  }

  async function startIdentityVerification() {
    if (typeof PortOne === "undefined" || typeof PortOne.requestIdentityVerification !== "function") {
      setIdentityStatus("포트원 SDK를 불러오지 못했습니다.", true);
      return;
    }

    setIdentityVerifying(true);
    setIdentityStatus("본인인증 요청을 준비하고 있습니다.");

    try {
      const prepareResponse = await fetch("/api/identity-verifications", {
        method: "POST",
        headers: authHeaders({ "Content-Type": "application/json" }),
        body: JSON.stringify({ purpose: "LOAN_CONTRACT" }),
      });
      const prepare = await prepareResponse.json().catch(() => null);
      if (!prepareResponse.ok || !prepare?.identityVerificationId || !prepare?.storeId || !prepare?.channelKey) {
        throw new Error(prepare?.message || "본인인증 준비에 실패했습니다.");
      }

      setIdentityStatus("본인인증 창을 여는 중입니다.");
      const verifyResult = await PortOne.requestIdentityVerification({
        storeId: prepare.storeId,
        channelKey: prepare.channelKey,
        identityVerificationId: prepare.identityVerificationId,
      });

      if (verifyResult?.code != null) {
        throw new Error(verifyResult.message || "본인인증에 실패했습니다.");
      }

      setIdentityStatus("인증 결과를 확인하고 있습니다.");
      const completeResponse = await fetch(
          `/api/identity-verifications/${encodeURIComponent(prepare.identityVerificationId)}/complete`,
          { method: "POST", headers: authHeaders() }
      );
      const completeResult = await completeResponse.json().catch(() => null);
      if (!completeResponse.ok || completeResult?.status !== "VERIFIED") {
        throw new Error(completeResult?.message || "본인인증 완료 확인에 실패했습니다.");
      }

      identityVerificationId = prepare.identityVerificationId;

      setIdentityStatus("본인인증이 완료되었습니다.");
      if (identityPanel) identityPanel.classList.add("is-verified");

      await new Promise((resolve) => setTimeout(resolve, 600));

      if (identityPanel) identityPanel.hidden = true;
      if (contractFields) contractFields.hidden = false;
    } catch (err) {
      setIdentityStatus(err.message || "본인인증 처리 중 오류가 발생했습니다.", true);
    } finally {
      setIdentityVerifying(false);
    }
  }

  btnIdentityVerify?.addEventListener("click", startIdentityVerification);

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

    if (!identityVerificationId) {
      throw new Error("본인인증 정보를 찾을 수 없습니다. 본인인증을 다시 진행해 주세요.");
    }

    const payload = {
      ...draft,
      identityVerificationId,
    };

    const response = await fetch("/api/contracts/write", {
      method: "POST",
      headers: authHeaders({ "Content-Type": "application/json" }),
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
      const contractId = await createContract();
      await submitSignature(contractId);

      sessionStorage.removeItem(DRAFT_KEY);
      alert("차용증이 전송되었습니다.");
      window.location.href = "/mypage";
    } catch (err) {
      showStatus(err.message || "차용증 전송에 실패했습니다. 잠시 후 다시 시도해 주세요.", true);
      submitBtn.disabled = false;
    }
  });
})();