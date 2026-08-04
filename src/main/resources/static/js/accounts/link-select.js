document.addEventListener("DOMContentLoaded", async () => {
    const API = {
        available: "/api/mock-bank/accounts/available",
        link: "/api/linked-accounts"
    };

    const listEl = document.getElementById("account-select-list");
    const errorEl = document.getElementById("account-select-error");
    const agreeCheckbox = document.getElementById("link-select-agree-checkbox");
    const confirmButton = document.getElementById("link-select-confirm");
    const cancelButton = document.getElementById("link-select-cancel");

    const selectedIds = new Set();
    let accounts = [];

    function getToken() {
        return sessionStorage.getItem("accessToken");
    }

    function redirectToLogin() {
        window.location.replace("/login?required=true");
    }

    function showError(message) {
        errorEl.textContent = message;
        errorEl.hidden = false;
    }

    function maskAccountNumber(raw) {
        if (!raw) return "";
        const digits = String(raw).replace(/\D/g, "");
        if (digits.length < 4) return raw;
        const visibleTail = digits.slice(-4);
        return `${digits.slice(0, 3)}-***-${visibleTail}`;
    }

    function formatBalance(balance) {
        if (balance == null) return "0";
        return Number(balance).toLocaleString("ko-KR");
    }

    function updateConfirmState() {
        confirmButton.disabled =
            !(agreeCheckbox.checked && selectedIds.size > 0);
    }

    function toggleSelect(accountId, cardEl) {
        if (selectedIds.has(accountId)) {
            selectedIds.delete(accountId);
            cardEl.classList.remove("selected");
        } else {
            selectedIds.add(accountId);
            cardEl.classList.add("selected");
        }
        updateConfirmState();
    }

    function renderAccounts(items) {
        listEl.innerHTML = "";

        if (!Array.isArray(items) || items.length === 0) {
            showError("연결 가능한 계좌가 없습니다.");
            return;
        }

        items.forEach(account => {
            const connectable = account.connectable !== false;

            const card = document.createElement("div");
            card.className = `account-select-item${connectable ? "" : " disabled"}`;
            card.dataset.accountId = account.accountId;

            card.innerHTML = `
                <div class="account-select-left">
                    <div class="account-select-icon">
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="m3 10 9-6 9 6"></path>
                            <path d="M5 10v8"></path>
                            <path d="M9 10v8"></path>
                            <path d="M15 10v8"></path>
                            <path d="M19 10v8"></path>
                            <path d="M3 18h18"></path>
                            <path d="M2 21h20"></path>
                        </svg>
                    </div>

                    <div class="account-select-info">
                        <div class="account-select-bank-row">
                            <span class="account-select-bank-name">
                                ${escapeHtml(account.bankName ?? "테스트은행")}
                            </span>
                            ${connectable ? "" : `<span class="account-select-badge">비활성</span>`}
                        </div>

                        <div class="account-select-alias">
                            ${connectable
                ? escapeHtml(account.accountName ?? "")
                : "연결 불가 (사용 중지 계좌)"}
                        </div>

                        <div class="account-select-numbers">
                            ${maskAccountNumber(account.accountNumber)} · 예금주: ${escapeHtml(account.accountHolderName ?? "")}
                        </div>
                    </div>
                </div>

                <div class="account-select-right">
                    <span class="account-select-balance-label">잔액</span>
                    <span class="account-select-balance">
                        ${connectable ? formatBalance(account.balance) : "0"}
                    </span>
                    <span class="account-select-check">
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M20 6 9 17l-5-5"></path>
                        </svg>
                    </span>
                </div>
            `;

            if (connectable) {
                card.addEventListener("click", () => {
                    toggleSelect(account.accountId, card);
                });
            }

            listEl.appendChild(card);
        });
    }

    function escapeHtml(value) {
        if (value == null) return "";
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    async function loadAccounts() {
        const token = getToken();

        if (!token) {
            redirectToLogin();
            return;
        }

        try {
            const response = await fetch(API.available, {
                method: "GET",
                headers: {
                    "Accept": "application/json",
                    "Authorization": `Bearer ${token}`
                }
            });

            if (response.status === 401 || response.status === 403) {
                redirectToLogin();
                return;
            }

            if (!response.ok) {
                throw new Error("계좌 목록을 불러오지 못했습니다.");
            }

            const data = await response.json();
            accounts = data?.data ?? data ?? [];
            renderAccounts(accounts);
        } catch (error) {
            console.error(error);
            showError(error.message || "계좌 목록을 불러오지 못했습니다.");
        }
    }

    agreeCheckbox?.addEventListener("change", updateConfirmState);

    cancelButton?.addEventListener("click", () => {
        window.location.href = "/mypage";
    });

    confirmButton?.addEventListener("click", async () => {
        const token = getToken();
        if (!token) {
            redirectToLogin();
            return;
        }

        const selectedAccounts = accounts
            .filter(account => selectedIds.has(account.accountId))
            .map(account => ({
                accountId: account.accountId,
                bankCode: account.bankCode ?? "",
                accountNumber: account.accountNumber,
                accountName: account.accountName,
                accountHolderName: account.accountHolderName,
                accountAlias: account.accountName,
                balance: account.balance
            }));

        confirmButton.disabled = true;
        const originalText = confirmButton.textContent;
        confirmButton.textContent = "연결 중...";

        try {
            const response = await fetch(API.link, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}`
                },
                body: JSON.stringify({ selectedAccounts })
            });

            if (!response.ok) {
                throw new Error("계좌 연결에 실패했습니다.");
            }

            window.location.href = "/mypage";
        } catch (error) {
            console.error(error);
            window.alert(error.message || "계좌 연결 중 오류가 발생했습니다.");
            confirmButton.disabled = false;
            confirmButton.textContent = originalText;
        }
    });

    await loadAccounts();
});