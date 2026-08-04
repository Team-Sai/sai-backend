document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("shared-settlement-form");
    const submitButton = document.getElementById("submit-button");
    const toast = document.getElementById("toast");

    const titleInput = document.getElementById("title");
    const categorySelect = document.getElementById("settlement-category");
    const dueDateInput = document.getElementById("due-date");

    const summaryTitle = document.getElementById("summary-title");
    const summaryCategory = document.getElementById("summary-category");
    const summaryDueDate = document.getElementById("summary-due-date");
    const summarySplitType = document.getElementById("summary-split-type");
    const ownerChip = document.getElementById("owner-chip");

    setMinimumDueDate();
    bindSummary();
    bindChoiceCards();
    loadCurrentUser();

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
                document.querySelectorAll(".choice-card").forEach((item) => {
                    item.classList.remove("selected");
                });

                card.classList.add("selected");
                const radio = card.querySelector('input[type="radio"]');
                radio.checked = true;
                radio.dispatchEvent(new Event("change"));
            });
        });
    }

    async function loadCurrentUser() {
        const token = getAccessToken();
        if (!token) {
            return;
        }

        try {
            const response = await fetch("/api/users/me", {
                headers: {
                    Authorization: `Bearer ${token}`
                },
                credentials: "include"
            });

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

        const payload = {
            settlementCategory: categorySelect.value,
            title: titleInput.value.trim(),
            splitType:
                document.querySelector('input[name="splitType"]:checked')?.value,
            dueDate: dueDateInput.value
        };

        if (!validate(payload)) {
            showToast("필수 입력값을 확인해 주세요.", true);
            return;
        }

        const token = getAccessToken();

        if (!token) {
            window.location.href = "/login?required=true";
            return;
        }

        setSubmitting(true);

        try {
            const response = await fetch("/api/settlements/shared", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`
                },
                credentials: "include",
                body: JSON.stringify(payload)
            });

            if (response.status === 401) {
                clearStoredAuth();
                window.location.href = "/login?required=true";
                return;
            }

            const responseBody = await readJsonSafely(response);

            if (!response.ok) {
                throw new Error(
                    responseBody?.message ||
                    getValidationMessage(responseBody) ||
                    "공동정산 생성에 실패했습니다."
                );
            }

            const recentSettlement = {
                ...responseBody,
                settlementCategory: payload.settlementCategory,
                splitType: payload.splitType,
                dueDate: payload.dueDate
            };

            sessionStorage.setItem(
                "recentCreatedSettlement",
                JSON.stringify(recentSettlement)
            );

            window.location.href =
                `/settlements?created=${encodeURIComponent(responseBody.settlementId)}`;
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

        if (!payload.splitType) {
            valid = false;
        }

        return valid;
    }

    function setError(fieldName, message) {
        const errorElement =
            document.querySelector(`[data-error-for="${fieldName}"]`);

        if (errorElement) {
            errorElement.textContent = message;
        }
    }

    function clearErrors() {
        document.querySelectorAll(".field-error").forEach((element) => {
            element.textContent = "";
        });
    }

    function setSubmitting(isSubmitting) {
        submitButton.disabled = isSubmitting;
        submitButton.textContent =
            isSubmitting ? "생성 중..." : "공동정산 생성";
    }

    function getAccessToken() {
        return sessionStorage.getItem("accessToken");
    }
    function clearStoredAuth() {
        sessionStorage.removeItem("accessToken");
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
});
