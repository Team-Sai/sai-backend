document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("shared-settlement-form");
    const submitButton = document.getElementById("submit-button");
    const toast = document.getElementById("toast");

    const titleInput = document.getElementById("title");
    const categorySelect = document.getElementById("settlement-category");
    const dueDateInput = document.getElementById("due-date");
    const totalAmountInput = document.getElementById("total-amount");
    const settlementAccountSelect = document.getElementById("settlement-account");

    const participantTokenInput = document.getElementById("participant-token");
    const lookupParticipantButton = document.getElementById("lookup-participant-button");
    const participantChips = document.getElementById("participant-chips");
    const participantEmptyMessage = document.getElementById("participant-empty-message");
    const ownerChip = document.getElementById("owner-chip");

    const cycleRuleSelect = document.getElementById("cycle-rule");
    const recurringStartDateInput = document.getElementById("recurring-start-date");
    const recurringEndDateInput = document.getElementById("recurring-end-date");
    const sharedDateSection = document.getElementById("shared-date-section");
    const recurringSettingSection = document.getElementById("recurring-setting-section");

    const tabs = document.querySelectorAll(".settlement-type-tabs .type-tab");
    const sharedTab = tabs[0];
    const recurringTab = tabs[1];

    const createDescription = document.querySelector(".create-heading p");
    const summaryHeader = document.querySelector(".summary-header");
    const summaryTitle = document.getElementById("summary-title");
    const summaryCategory = document.getElementById("summary-category");
    const summaryDueDate = document.getElementById("summary-due-date");
    const summaryDueDateLabel = summaryDueDate?.closest("div")?.querySelector("dt");
    const summarySplitType = document.getElementById("summary-split-type");
    const summaryTotalAmount = document.getElementById("summary-total-amount");
    const summaryParticipantCount = document.getElementById("summary-participant-count");
    const summaryPerPersonAmount = document.getElementById("summary-per-person-amount");
    const summaryAccount = document.getElementById("summary-account");

    const selectedParticipants = new Map();

    const categoryOptions = {
        SHARED: [
            ["", "선택해 주세요"],
            ["여행", "여행"],
            ["생활비", "생활비"],
            ["회식", "회식"],
            ["공동구매", "공동구매"],
            ["모임", "모임"],
            ["기타", "기타"]
        ],
        RECURRING: [
            ["", "선택해 주세요"],
            ["OTT·구독", "OTT·구독"],
            ["정기회비", "정기회비"],
            ["공과금", "공과금"],
            ["공동생활비", "공동생활비"],
            ["간병·가족비용", "간병·가족비용"],
            ["교육·스터디", "교육·스터디"],
            ["정기공동구매", "정기공동구매"],
            ["기타", "기타"]
        ]
    };

    let settlementType = "SHARED";
    let submitting = false;

    setMinimumDueDate();
    bindSettlementTabs();
    bindSummary();
    bindChoiceCards();
    bindTotalAmount();
    bindParticipantLookup();
    bindSettlementAccount();
    bindRecurringDates();
    updateParticipantView();
    changeSettlementType("SHARED");
    loadCurrentUser();
    loadLinkedAccounts();

    form.addEventListener("submit", submitSettlement);

    function setMinimumDueDate() {
        const today = new Date();
        const year = today.getFullYear();
        const month = String(today.getMonth() + 1).padStart(2, "0");
        const day = String(today.getDate()).padStart(2, "0");
        dueDateInput.min = `${year}-${month}-${day}`;
    }

    function bindSettlementTabs() {
        sharedTab.addEventListener("click", () => changeSettlementType("SHARED"));
        recurringTab.addEventListener("click", () => changeSettlementType("RECURRING"));
    }

    function changeSettlementType(type) {
        settlementType = type;
        const shared = type === "SHARED";

        sharedTab.classList.toggle("active", shared);
        recurringTab.classList.toggle("active", !shared);
        sharedTab.setAttribute("aria-selected", String(shared));
        recurringTab.setAttribute("aria-selected", String(!shared));

        sharedDateSection.hidden = !shared;
        recurringSettingSection.hidden = shared;

        dueDateInput.required = shared;
        cycleRuleSelect.required = !shared;
        recurringStartDateInput.required = !shared;

        clearError("dueDate");
        clearError("cycleRule");
        clearError("startDate");
        clearError("endDate");

        updateFormByType();
        updateSubmitButton();
    }

    function updateFormByType() {
        const shared = settlementType === "SHARED";
        updateTotalAmountLabel(shared
            ? "\uCD1D \uAE08\uC561 (\uD544\uC218)"
            : "\uD68C\uCC28\uBCC4 \uCD1D\uAE08\uC561 (\uD544\uC218)");

        categorySelect.innerHTML = categoryOptions[settlementType]
            .map(([value, label]) => `<option value="${value}">${label}</option>`)
            .join("");

        categorySelect.value = "";
        summaryCategory.textContent = "선택 전";

        if (shared) {
            titleInput.placeholder = "예: 제주 여행 경비";
            summaryHeader.textContent = "공동 정산 요약";
            summaryDueDateLabel.textContent = "정산 마감일";

            if (createDescription) {
                createDescription.textContent =
                    "총 금액과 참여자를 선택하면 참여자별 납부 예정 금액을 균등하게 계산합니다.";
            }

            summaryDueDate.textContent =
                formatDate(dueDateInput.value) || "선택 전";
        } else {
            titleInput.placeholder = "예: 부모님 간병비 월 분담";
            summaryHeader.textContent = "정기 정산 요약";
            summaryDueDateLabel.textContent = "정기 시작일";

            if (createDescription) {
                createDescription.textContent =
                    "구독료, 회비, 공과금, 간병비처럼 반복되는 공동 비용을 정기적으로 관리합니다.";
            }

            summaryDueDate.textContent =
                formatDate(recurringStartDateInput.value) || "선택 전";
        }
    }

    function updateTotalAmountLabel(labelText) {
        const amountInput = document.getElementById("total-amount");
        const cardTitle = amountInput?.closest("section")
            ?.querySelector(".card-title h2");
        const label = amountInput?.closest("label")?.querySelector("span")
            || amountInput?.parentElement?.querySelector("span");
        if (cardTitle) {
            cardTitle.textContent = labelText.replace(" (필수)", "");
        }
        if (label) label.textContent = labelText;
    }

    function bindSummary() {
        titleInput.addEventListener("input", () => {
            summaryTitle.textContent = titleInput.value.trim() || "입력 전";
        });

        categorySelect.addEventListener("change", () => {
            summaryCategory.textContent =
                categorySelect.options[categorySelect.selectedIndex]?.text || "선택 전";
        });

        dueDateInput.addEventListener("change", () => {
            if (settlementType === "SHARED") {
                summaryDueDate.textContent = formatDate(dueDateInput.value) || "선택 전";
            }
        });

        document.querySelectorAll('input[name="splitType"]').forEach(radio => {
            radio.addEventListener("change", () => {
                summarySplitType.textContent =
                    radio.value === "CUSTOM" ? "직접 설정" : "균등 분배";
            });
        });
    }

    function bindRecurringDates() {
        recurringStartDateInput.addEventListener("change", () => {
            recurringEndDateInput.min = recurringStartDateInput.value || "";

            if (
                recurringEndDateInput.value &&
                recurringStartDateInput.value &&
                recurringEndDateInput.value < recurringStartDateInput.value
            ) {
                recurringEndDateInput.value = "";
            }

            if (settlementType === "RECURRING") {
                summaryDueDate.textContent =
                    formatDate(recurringStartDateInput.value) || "선택 전";
            }
        });
    }

    function bindChoiceCards() {
        document.querySelectorAll(".choice-card").forEach(card => {
            card.addEventListener("click", () => {
                const radio = card.querySelector('input[type="radio"]');

                if (!radio || radio.disabled) return;

                document.querySelectorAll(".choice-card").forEach(item => {
                    item.classList.remove("selected");
                });

                card.classList.add("selected");
                radio.checked = true;
                radio.dispatchEvent(new Event("change"));
            });
        });
    }

    function bindTotalAmount() {
        totalAmountInput.addEventListener("input", () => {
            const rawAmount = extractAmount(totalAmountInput.value).slice(0, 13);
            totalAmountInput.value = formatAmountInput(rawAmount);
            updateAmountSummary();
        });
    }

    function bindParticipantLookup() {
        lookupParticipantButton.addEventListener("click", lookupAndAddParticipant);

        participantTokenInput.addEventListener("keydown", event => {
            if (event.key !== "Enter") return;
            event.preventDefault();
            lookupAndAddParticipant();
        });
    }

    async function lookupAndAddParticipant() {
        clearError("participantLookup");
        clearError("participants");

        const userToken = participantTokenInput.value.trim();

        if (!userToken) {
            setError("participantLookup", "조회할 회원 코드를 입력해 주세요.");
            participantTokenInput.focus();
            return;
        }

        if (selectedParticipants.has(userToken)) {
            setError("participantLookup", "이미 추가한 참여자입니다.");
            return;
        }

        setParticipantLookupLoading(true);

        try {
            const response = await authFetch(
                `/api/users/by-token/${encodeURIComponent(userToken)}`
            );

            const responseBody = await readJsonSafely(response);

            if (!response.ok) {
                throw new Error(
                    getValidationMessage(responseBody) ||
                    responseBody?.message ||
                    "회원 정보를 찾을 수 없습니다."
                );
            }

            const participant = normalizeLookupUser(responseBody, userToken);

            if (selectedParticipants.has(participant.userToken)) {
                setError("participantLookup", "이미 추가한 참여자입니다.");
                return;
            }

            selectedParticipants.set(participant.userToken, participant);
            participantTokenInput.value = "";
            updateParticipantView();

            showToast(`${participant.name} 님을 참여자로 추가했습니다.`);
        } catch (error) {
            setError(
                "participantLookup",
                error.message || "회원 조회 중 오류가 발생했습니다."
            );
        } finally {
            setParticipantLookupLoading(false);
        }
    }

    async function loadLinkedAccounts() {
        try {
            const response = await authFetch("/api/linked-accounts", {
                method: "GET",
                headers: { "Accept": "application/json" }
            });

            if (!response.ok) {
                throw new Error("연동 계좌를 불러오지 못했습니다.");
            }

            const responseBody = await readJsonSafely(response);
            const accounts = responseBody?.data ?? responseBody ?? [];

            settlementAccountSelect.innerHTML =
                '<option value="">계좌를 선택해 주세요</option>';

            accounts.forEach(account => {
                const option = document.createElement("option");

                option.value = account.linkedAccountId;
                option.textContent = [
                    account.bankName,
                    account.accountAlias,
                    account.maskedAccountNumber
                ].filter(Boolean).join(" ");

                settlementAccountSelect.appendChild(option);
            });
        } catch (error) {
            console.error("연동 계좌 조회 실패", error);
        }
    }

    function normalizeLookupUser(user, requestedToken) {
        return {
            userId: user?.userId ?? user?.id ?? null,
            userToken: user?.userToken || user?.token || requestedToken,
            name: user?.name || user?.userName || user?.nickname || "이름 없는 회원"
        };
    }

    function updateParticipantView() {
        participantChips
            .querySelectorAll(".participant-chip.removable")
            .forEach(chip => chip.remove());

        selectedParticipants.forEach(participant => {
            const chip = document.createElement("span");
            chip.className = "participant-chip removable";
            chip.dataset.userToken = participant.userToken;

            const name = document.createElement("span");
            name.className = "participant-chip-name";
            name.textContent = participant.name;

            const token = document.createElement("span");
            token.className = "participant-chip-token";
            token.textContent = participant.userToken;

            const removeButton = document.createElement("button");
            removeButton.type = "button";
            removeButton.className = "participant-remove-button";
            removeButton.setAttribute(
                "aria-label",
                `${participant.name} 참여자에서 제거`
            );
            removeButton.textContent = "×";

            removeButton.addEventListener("click", () => {
                selectedParticipants.delete(participant.userToken);
                updateParticipantView();
            });

            chip.append(name, token, removeButton);
            participantChips.appendChild(chip);
        });

        participantEmptyMessage.hidden = selectedParticipants.size > 0;
        summaryParticipantCount.textContent =
            `${selectedParticipants.size + 1}명 (본인 포함)`;

        updateAmountSummary();
    }

    function updateAmountSummary() {
        const rawAmount = extractAmount(totalAmountInput.value);
        const totalAmount = rawAmount ? Number(rawAmount) : 0;

        summaryTotalAmount.textContent =
            totalAmount > 0
                ? `${totalAmount.toLocaleString("ko-KR")}원`
                : "입력 전";

        const participantCount = selectedParticipants.size + 1;

        const perPersonAmount =
            totalAmount > 0
                ? Math.floor(totalAmount / participantCount)
                : 0;

        summaryPerPersonAmount.textContent =
            perPersonAmount > 0
                ? `${perPersonAmount.toLocaleString("ko-KR")}원`
                : "계산 전";
    }

    async function loadCurrentUser() {
        try {
            const response = await authFetch("/api/users/me");

            if (!response.ok) return;

            const user = await response.json();
            const name = user.name || user.userName;

            if (name) {
                ownerChip.textContent = `${name} (생성자)`;
            }
        } catch (error) {
            console.warn("사용자 정보를 불러오지 못했습니다.", error);
        }
    }

    function bindSettlementAccount() {
        settlementAccountSelect.addEventListener("change", () => {
            const selectedOption =
                settlementAccountSelect.options[settlementAccountSelect.selectedIndex];

            summaryAccount.textContent =
                settlementAccountSelect.value
                    ? selectedOption.text
                    : "아직 설정되지 않음";
        });
    }

    function submitSettlement(event) {
        if (settlementType === "RECURRING") {
            submitRecurringSettlement(event);
        } else {
            submitSharedSettlement(event);
        }
    }

    async function submitSharedSettlement(event) {
        event.preventDefault();
        clearErrors();

        const payload = buildCommonPayload();
        payload.dueDate = dueDateInput.value;

        if (!validateShared(payload)) {
            showToast("필수 입력값을 확인해 주세요.", true);
            return;
        }

        setSubmitting(true);

        try {
            const response = await authFetch("/api/settlements/shared", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });

            const responseBody = await readJsonSafely(response);

            if (!response.ok) {
                throw new Error(
                    getValidationMessage(responseBody) ||
                    responseBody?.message ||
                    "공동정산 생성에 실패했습니다."
                );
            }

            sessionStorage.setItem(
                "recentCreatedSettlement",
                JSON.stringify({
                    ...responseBody,
                    settlementCategory: payload.settlementCategory,
                    splitType: "EQUAL",
                    dueDate: payload.dueDate,
                    totalAmount: payload.totalAmount,
                    participantCount: payload.participants.length + 1,
                    linkedAccountId: payload.linkedAccountId
                })
            );

            window.location.href =
                `/settlements?created=${encodeURIComponent(responseBody.settlementId)}`;
        } catch (error) {
            showToast(
                error.message || "요청 처리 중 오류가 발생했습니다.",
                true
            );
        } finally {
            setSubmitting(false);
        }
    }

    async function submitRecurringSettlement(event) {
        event.preventDefault();
        clearErrors();

        const payload = {
            ...buildCommonPayload(),
            cycleRule: cycleRuleSelect.value,
            startDate: recurringStartDateInput.value,
            endDate: recurringEndDateInput.value || null
        };

        if (!validateRecurring(payload)) {
            showToast("필수 입력값을 확인해 주세요.", true);
            return;
        }

        setSubmitting(true);

        try {
            const response = await authFetch("/api/settlements/recurring", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });

            const responseBody = await readJsonSafely(response);

            if (!response.ok) {
                throw new Error(
                    getValidationMessage(responseBody) ||
                    responseBody?.message ||
                    "정기정산 생성에 실패했습니다."
                );
            }

            const settlementId =
                responseBody?.firstSettlementId ??
                responseBody?.settlementId;

            sessionStorage.setItem(
                "recentCreatedSettlement",
                JSON.stringify({
                    ...responseBody,
                    settlementId,
                    settlementType: "RECURRING",
                    settlementCategory: payload.settlementCategory,
                    splitType: "EQUAL",
                    dueDate: null,
                    cycleDate: payload.startDate,
                    cycleRule: payload.cycleRule,
                    startDate: payload.startDate,
                    endDate: payload.endDate,
                    totalAmount: payload.totalAmount,
                    participantCount: payload.participants.length + 1,
                    linkedAccountId: payload.linkedAccountId
                })
            );

            window.location.href = settlementId
                ? `/settlements?created=${encodeURIComponent(settlementId)}`
                : "/settlements";
        } catch (error) {
            showToast(
                error.message || "정기정산 생성 중 오류가 발생했습니다.",
                true
            );
        } finally {
            setSubmitting(false);
        }
    }

    function buildCommonPayload() {
        const rawTotalAmount = extractAmount(totalAmountInput.value);

        return {
            settlementCategory: categorySelect.value,
            title: titleInput.value.trim(),
            totalAmount: rawTotalAmount ? Number(rawTotalAmount) : null,
            linkedAccountId: settlementAccountSelect.value
                ? Number(settlementAccountSelect.value)
                : null,
            participants: Array.from(selectedParticipants.values()).map(
                participant => ({
                    userToken: participant.userToken
                })
            )
        };
    }

    function validateShared(payload) {
        let valid = validateCommon(payload);

        if (!payload.dueDate) {
            setError("dueDate", "정산 마감일을 입력해 주세요.");
            valid = false;
        } else if (payload.dueDate < dueDateInput.min) {
            setError("dueDate", "정산 마감일은 오늘 이후여야 합니다.");
            valid = false;
        }

        return valid;
    }

    function validateRecurring(payload) {
        let valid = validateCommon(payload);

        if (!payload.cycleRule) {
            setError("cycleRule", "반복 주기를 선택해 주세요.");
            valid = false;
        }

        if (!payload.startDate) {
            setError("startDate", "시작일을 입력해 주세요.");
            valid = false;
        }

        if (
            payload.startDate &&
            payload.endDate &&
            payload.endDate < payload.startDate
        ) {
            setError("endDate", "종료일은 시작일보다 빠를 수 없습니다.");
            valid = false;
        }

        return valid;
    }

    function validateCommon(payload) {
        let valid = true;

        if (!payload.title) {
            setError("title", "정산명을 입력해 주세요.");
            valid = false;
        }

        if (!payload.settlementCategory) {
            setError("settlementCategory", "정산 성격을 선택해 주세요.");
            valid = false;
        }

        if (
            payload.totalAmount === null ||
            !Number.isInteger(payload.totalAmount) ||
            payload.totalAmount <= 0
        ) {
            setError("totalAmount", "총 금액을 1원 이상 입력해 주세요.");
            valid = false;
        }

        if (
            !Array.isArray(payload.participants) ||
            payload.participants.length === 0
        ) {
            setError("participants", "참여자를 한 명 이상 추가해 주세요.");
            valid = false;
        }

        if (!payload.linkedAccountId) {
            setError(
                "linkedAccountId",
                "정산 수취 계좌를 선택해 주세요."
            );
            valid = false;
        }

        return valid;
    }

    function extractAmount(value) {
        return String(value || "")
            .replace(/[^\d]/g, "")
            .replace(/^0+(?=\d)/, "");
    }

    function formatAmountInput(rawAmount) {
        return rawAmount
            ? Number(rawAmount).toLocaleString("ko-KR")
            : "";
    }

    function setParticipantLookupLoading(loading) {
        lookupParticipantButton.disabled = loading;
        participantTokenInput.disabled = loading;
        lookupParticipantButton.textContent =
            loading ? "조회 중..." : "조회 후 추가";
    }

    function setError(fieldName, message) {
        const element =
            document.querySelector(
                `[data-error-for="${fieldName}"]`
            );

        if (element) {
            element.textContent = message;
        }
    }

    function clearError(fieldName) {
        setError(fieldName, "");
    }

    function clearErrors() {
        document.querySelectorAll(".field-error").forEach(element => {
            element.textContent = "";
        });
    }

    function setSubmitting(value) {
        submitting = value;
        submitButton.disabled = value;
        updateSubmitButton();
    }

    function updateSubmitButton() {
        submitButton.textContent = submitting
            ? "생성 중..."
            : settlementType === "RECURRING"
                ? "정기정산 생성"
                : "공동정산 생성";
    }

    function getValidationMessage(body) {
        if (!body) return null;

        if (
            Array.isArray(body.errors) &&
            body.errors.length > 0
        ) {
            return (
                body.errors[0].message ||
                body.errors[0].defaultMessage
            );
        }

        return null;
    }

    async function readJsonSafely(response) {
        const text = await response.text();

        if (!text) return null;

        try {
            return JSON.parse(text);
        } catch {
            return { message: text };
        }
    }

    function formatDate(value) {
        if (!value) return "";

        const [year, month, day] = value.split("-");
        return `${year}.${month}.${day}`;
    }

    function showToast(message, isError = false) {
        toast.textContent = message;
        toast.classList.toggle("error", isError);
        toast.classList.add("visible");

        window.clearTimeout(showToast.timer);

        showToast.timer = window.setTimeout(() => {
            toast.classList.remove("visible");
        }, 3000);
    }
});
