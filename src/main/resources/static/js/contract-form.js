(function () {
  "use strict";

  const STORAGE_KEY = "loanContractDraft";

  const form = document.getElementById("contractForm");
  const statusEl = document.getElementById("formStatus");
  const tempSaveBtn = document.getElementById("btnTempSave");

  if (!form) return;

  const API_BASE = form.dataset.apiBase || "/api/contracts";
  let contractId = form.dataset.contractId || null;

  function contractUrl(suffix) {
    const base = contractId ? `${API_BASE}/${contractId}` : API_BASE;
    return suffix ? `${base}/${suffix}` : base;
  }

  /** Collect all named/id'd form fields into a plain object. */
  function serializeForm() {
    const data = {};
    const fields = form.querySelectorAll("input[name], input[id]");
    fields.forEach((field) => {
      const key = field.name || field.id;
      if (!key) return;
      if (field.type === "radio") {
        if (field.checked) data[key] = field.value;
      } else {
        data[key] = field.value;
      }
    });
    return data;
  }

  /** Apply a plain {fieldName: value} object onto the form's inputs. */
  function applyValues(saved) {
    if (!saved) return;
    Object.keys(saved).forEach((key) => {
      const field = form.querySelector(`[name="${key}"], #${CSS.escape(key)}`);
      if (!field) return;
      if (field.type === "radio") {
        const target = form.querySelector(`[name="${key}"][value="${saved[key]}"]`);
        if (target) target.checked = true;
      } else {
        field.value = saved[key];
      }
    });
  }

  /**
   * Restore draft values into the form.
   * If a contractId is present, load from GET /api/contracts/{id} (source of
   * truth). Otherwise fall back to whatever was cached locally.
   */
  async function restoreDraft() {
    if (contractId) {
      try {
        const response = await fetch(contractUrl());
        if (response.ok) {
          const data = await response.json();
          applyValues(data);
          return;
        }
      } catch (err) {
        /* fall through to local cache */
      }
    }

    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
      applyValues(saved);
    } catch (err) {
      /* no draft available */
    }
  }

  function showStatus(message, isError) {
    statusEl.textContent = message;
    statusEl.classList.toggle("is-error", Boolean(isError));
  }

  function validate() {
    const principal = form.querySelector('[name="principal"]');
    const repaymentMethod = form.querySelector('[name="repaymentMethod"]:checked');
    const debtorName = form.querySelector('[name="debtor.name"]');

    if (principal && (!principal.value || Number(principal.value) <= 0)) {
      showStatus("차용 금액을 입력해 주세요.", true);
      principal.focus();
      return false;
    }
    if (!repaymentMethod) {
      showStatus("상환방식을 선택해 주세요.", true);
      return false;
    }
    if (debtorName && !debtorName.value.trim()) {
      showStatus("채무자 성명을 입력해 주세요.", true);
      debtorName.focus();
      return false;
    }
    return true;
  }

  // Temp save: send current form state to POST /api/contracts/{id}/temp-save
  // (also mirrored to localStorage so a draft survives a full page reload
  // even before the server round-trip completes).
  tempSaveBtn?.addEventListener("click", async () => {
    const data = serializeForm();

    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch (err) {
      /* local cache is best-effort only */
    }

    tempSaveBtn.disabled = true;
    showStatus("임시저장 중입니다...", false);

    try {
      const response = await fetch(contractUrl("temp-save"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);

      const result = await response.json().catch(() => null);
      if (result && result.id && !contractId) {
        contractId = String(result.id);
        form.dataset.contractId = contractId;
      }
      showStatus("임시저장되었습니다.", false);
    } catch (err) {
      showStatus("임시저장에 실패했습니다. 잠시 후 다시 시도해 주세요.", true);
    } finally {
      tempSaveBtn.disabled = false;
    }
  });

  // Submit: validate, then send to the unified /api/contracts endpoint.
  // POST /api/contracts        - new contract
  // PUT  /api/contracts/{id}   - existing contract
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!validate()) return;

    const submitBtn = document.getElementById("btnSubmit");
    submitBtn?.setAttribute("disabled", "true");
    showStatus("전송 중입니다...", false);

    const data = serializeForm();
    const method = contractId ? "PUT" : "POST";

    try {
      const response = await fetch(contractUrl(), {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);

      const result = await response.json().catch(() => null);
      if (result && result.id) {
        contractId = String(result.id);
        form.dataset.contractId = contractId;
      }

      try {
        localStorage.removeItem(STORAGE_KEY);
      } catch (err) {
        /* no-op */
      }

      showStatus("전송이 완료되었습니다.", false);
      // Move to the next step (상대확인) once the server confirms the save.
      if (result && result.nextStepUrl) {
        window.location.href = result.nextStepUrl;
      }
    } catch (err) {
      showStatus("전송에 실패했습니다. 잠시 후 다시 시도해 주세요.", true);
    } finally {
      submitBtn?.removeAttribute("disabled");
    }
  });

  // Auto-format numeric principal with thousands separators while typing.
  const principalInput = form.querySelector('[name="principal"]');
  if (principalInput) {
    principalInput.addEventListener("blur", () => {
      const raw = principalInput.value.replace(/[^\d]/g, "");
      if (raw) principalInput.value = Number(raw).toLocaleString("ko-KR");
    });
    principalInput.addEventListener("focus", () => {
      principalInput.value = principalInput.value.replace(/[^\d]/g, "");
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", restoreDraft);
  } else {
    restoreDraft();
  }
})();
