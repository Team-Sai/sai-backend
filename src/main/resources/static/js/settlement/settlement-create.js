document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("shared-settlement-form");
    const submitButton = document.getElementById("submit-button");
    const toast = document.getElementById("toast");

    const titleInput = document.getElementById("title");
    const categorySelect = document.getElementById("settlement-category");
    const dueDateInput = document.getElementById("due-date");
    const totalAmountInput = document.getElementById("total-amount");
    const settlementAccountSelect = document.getElementById("settlement-account");
    const summaryAccount = document.getElementById("summary-account");
    const participantTokenInput = document.getElementById("participant-token");
    const lookupParticipantButton = document.getElementById("lookup-participant-button");
    const participantChips = document.getElementById("participant-chips");
    const participantEmptyMessage = document.getElementById("participant-empty-message");

    const summaryTitle = document.getElementById("summary-title");
    const summaryCategory = document.getElementById("summary-category");
    const summaryDueDate = document.getElementById("summary-due-date");
    const summarySplitType = document.getElementById("summary-split-type");
    const summaryTotalAmount = document.getElementById("summary-total-amount");
    const summaryParticipantCount = document.getElementById("summary-participant-count");
    const summaryPerPersonAmount = document.getElementById("summary-per-person-amount");
    const ownerChip = document.getElementById("owner-chip");
    const selectedParticipants = new Map();

    setMinimumDueDate();
    bindSummary();
    bindChoiceCards();
    bindTotalAmount();
    bindParticipantLookup();
    bindSettlementAccount();
    updateParticipantView();
    loadCurrentUser();
    loadLinkedAccounts();

    form.addEventListener("submit", submitSharedSettlement);

    function setMinimumDueDate() {
        const today = new Date();
        const year = today.getFullYear();
        const month = String(today.getMonth() + 1).padStart(2, "0");
        const day = String(today.getDate()).padStart(2, "0");

        dueDateInput.min = `${year}-${month}-${day}`;
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
            summaryDueDate.textContent = formatDate(dueDateInput.value) || "선택 전";
        });

        document.querySelectorAll('input[name="splitType"]').forEach((radio) => {
            radio.addEventListener("change", () => {
                summarySplitType.textContent =
                    radio.value === "CUSTOM" ? "직접 설정" : "균등 분배";
            });
        });
    }

    function bindChoiceCards() {
        document.querySelectorAll(".choice-card").forEach((card) => {
            card.addEventListener("click", () => {
                const radio = card.querySelector('input[type="radio"]');

                if (!radio || radio.disabled) {
                    return;
                }

                document.querySelectorAll(".choice-card").forEach((item) => {
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

        participantTokenInput.addEventListener("keydown", (event) => {
            if (event.key !== "Enter") {
                return;
            }

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

            const normalizedParticipant = normalizeLookupUser(
                responseBody,
                userToken
            );

            if (selectedParticipants.has(normalizedParticipant.userToken)) {
                setError("participantLookup", "이미 추가한 참여자입니다.");
                return;
            }

            selectedParticipants.set(
                normalizedParticipant.userToken,
                normalizedParticipant
            );

            participantTokenInput.value = "";
            updateParticipantView();
            showToast(`${normalizedParticipant.name} 님을 참여자로 추가했습니다.`);
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
            const response = await authFetch(
                "/api/linked-accounts",
                {
                    method: "GET",
                    headers: {
                        "Accept": "application/json"
                    }
                }
            );

            if (!response.ok) {
                throw new Error(
                    "연동 계좌를 불러오지 못했습니다."
                );
            }

            const responseBody = await readJsonSafely(response);

            const accounts =
                responseBody?.data ??
                responseBody ??
                [];

            settlementAccountSelect.innerHTML =
                '<option value="">계좌를 선택해 주세요</option>';

            accounts.forEach((account) => {
                const option =
                    document.createElement("option");

                option.value =
                    account.linkedAccountId;

                option.textContent = [
                    account.bankName,
                    account.accountAlias,
                    account.maskedAccountNumber
                ]
                    .filter(Boolean)
                    .join(" ");

                settlementAccountSelect.appendChild(option);
            });

        } catch (error) {
            console.error(
                "연동 계좌 조회 실패",
                error
            );
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
            .forEach((chip) => chip.remove());

        selectedParticipants.forEach((participant) => {
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

        summaryTotalAmount.textContent = totalAmount > 0
            ? `${totalAmount.toLocaleString("ko-KR")}원`
            : "입력 전";

        const participantCount = selectedParticipants.size + 1;
        const perPersonAmount = totalAmount > 0
            ? Math.floor(totalAmount / participantCount)
            : 0;

        summaryPerPersonAmount.textContent = perPersonAmount > 0
            ? `${perPersonAmount.toLocaleString("ko-KR")}원`
            : "계산 전";
    }

    async function loadCurrentUser() {
        try {
            const response = await authFetch(
                "/api/users/me"
            );

            if (!response.ok) {
                return;
            }

            const user = await response.json();
            const name = user.name || user.userName;

            if (name) {
                ownerChip.textContent = `${name} (생성자)`;
            }
        } catch (error) {
            console.warn("사용자 정보를 불러오지 못했습니다.", error);
        }
    }

    async function submitSharedSettlement(event) {
        event.preventDefault();
        clearErrors();

        const rawTotalAmount = extractAmount(totalAmountInput.value);

        const payload = {
            settlementCategory: categorySelect.value,
            title: titleInput.value.trim(),
            dueDate: dueDateInput.value,
            totalAmount: rawTotalAmount
                ? Number(rawTotalAmount)
                : null,

            linkedAccountId: settlementAccountSelect.value
                ? Number(settlementAccountSelect.value)
                : null,

            participants:
                Array.from(selectedParticipants.values())
                    .map((participant) => ({
                        userToken: participant.userToken
                    }))
        };

        if (!validate(payload)) {
            showToast("필수 입력값을 확인해 주세요.", true);
            return;
        }

        setSubmitting(true);

        try {
            const response = await authFetch(
                "/api/settlements/shared",
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(payload)
                }
            );

            const responseBody =
                await readJsonSafely(response);

            if (!response.ok) {
                throw new Error(
                    getValidationMessage(responseBody) ||
                    responseBody?.message ||
                    "공동정산 생성에 실패했습니다."
                );
            }

            const recentSettlement = {
                ...responseBody,
                settlementCategory:
                    payload.settlementCategory,
                splitType: "EQUAL",
                dueDate: payload.dueDate,
                totalAmount: payload.totalAmount,
                participantCount:
                    payload.participants.length + 1,
                linkedAccountId:payload.linkedAccountId
            };

            sessionStorage.setItem(
                "recentCreatedSettlement",
                JSON.stringify(recentSettlement)
            );

            window.location.href =
                `/settlements?created=${encodeURIComponent(
                    responseBody.settlementId
                )}`;
        } catch (error) {
            showToast(error.message || "요청 처리 중 오류가 발생했습니다.", true);
        } finally {
            setSubmitting(false);
        }
    }

    function validate(payload) {
        let valid = true;

        if (!payload.title) {
            setError("title", "정산명을 입력해 주세요.");
            valid = false;
        }

        if (!payload.settlementCategory) {
            setError("settlementCategory", "정산 성격을 선택해 주세요.");
            valid = false;
        }

        if (!payload.dueDate) {
            setError("dueDate", "정산 마감일을 입력해 주세요.");
            valid = false;
        } else if (payload.dueDate < dueDateInput.min) {
            setError("dueDate", "정산 마감일은 오늘 이후여야 합니다.");
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

        if (!Array.isArray(payload.participants) || payload.participants.length === 0) {
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
        if (!rawAmount) {
            return "";
        }

        return Number(rawAmount).toLocaleString("ko-KR");
    }

    function setParticipantLookupLoading(isLoading) {
        lookupParticipantButton.disabled = isLoading;
        participantTokenInput.disabled = isLoading;
        lookupParticipantButton.textContent = isLoading
            ? "조회 중..."
            : "조회 후 추가";
    }

    function setError(fieldName, message) {
        const errorElement =
            document.querySelector(`[data-error-for="${fieldName}"]`);

        if (errorElement) {
            errorElement.textContent = message;
        }
    }

    function clearError(fieldName) {
        setError(fieldName, "");
    }

    function clearErrors() {
        document.querySelectorAll(".field-error").forEach((element) => {
            element.textContent = "";
        });
    }

    function setSubmitting(isSubmitting) {
        submitButton.disabled = isSubmitting;
        submitButton.textContent = isSubmitting
            ? "생성 중..."
            : "공동정산 생성";
    }

    function getValidationMessage(body) {
        if (!body) {
            return null;
        }

        if (Array.isArray(body.errors) && body.errors.length > 0) {
            return body.errors[0].message || body.errors[0].defaultMessage;
        }

        return null;
    }

    async function readJsonSafely(response) {
        const text = await response.text();

        if (!text) {
            return null;
        }

        try {
            return JSON.parse(text);
        } catch {
            return { message: text };
        }
    }



    function formatDate(value) {
        if (!value) {
            return "";
        }

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

    function bindSettlementAccount() {
        settlementAccountSelect.addEventListener(
            "change",
            () => {
                const selectedOption =
                    settlementAccountSelect.options[
                        settlementAccountSelect.selectedIndex
                    ];

                summaryAccount.textContent =
                    settlementAccountSelect.value
                        ? selectedOption.text
                        : "아직 설정되지 않음";
            }
        );
    }
});
