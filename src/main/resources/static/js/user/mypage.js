document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const FILE_PREVIEW =
            window.location.protocol === "file:";

        const ROUTES = {
            main: FILE_PREVIEW
                ? "../login/login.html"
                : "/",

            login: FILE_PREVIEW
                ? "../login/login.html"
                : "/login"
        };

        const API = {
            me: "/api/users/me",
            linkedAccounts: "/api/linked-accounts"
        };

        document
            .querySelectorAll("[data-route]")
            .forEach(link => {
                const routeName =
                    link.dataset.route;

                if (ROUTES[routeName]) {
                    link.href =
                        ROUTES[routeName];
                }
            });

        const errorBox =
            document.getElementById(
                "mypage-error"
            );

        const logoutButton =
            document.getElementById(
                "logout-button"
            );

        const withdrawButton =
            document.getElementById(
                "withdraw-button"
            );

        function redirectToLogin(required = false) {
            const query = required
                ? "?required=true"
                : "";

            window.location.replace(
                `${ROUTES.login}${query}`
            );
        }

        function setText(id, value) {
            const element =
                document.getElementById(id);

            if (element) {
                element.textContent =
                    value ?? "";
            }
        }

        function setProfileImage(url) {
            const targets = [
                {
                    image:
                        document.getElementById(
                            "profile-image"
                        ),

                    placeholder:
                        document.getElementById(
                            "profile-placeholder"
                        )
                },
                {
                    image:
                        document.getElementById(
                            "header-profile-image"
                        ),

                    placeholder:
                        document.getElementById(
                            "header-profile-placeholder"
                        )
                }
            ];

            targets.forEach(
                ({ image, placeholder }) => {
                    if (!image || !placeholder) {
                        return;
                    }

                    if (url) {
                        image.src = url;
                        image.hidden = false;
                        placeholder.hidden = true;
                    } else {
                        image.removeAttribute("src");
                        image.hidden = true;
                        placeholder.hidden = false;
                    }
                }
            );
        }

        function createBankIcon() {
            return `
                <div class="bank-icon">
                    <svg viewBox="0 0 24 24"
                         aria-hidden="true">
                        <path d="m3 10 9-6 9 6"></path>
                        <path d="M5 10v8"></path>
                        <path d="M9 10v8"></path>
                        <path d="M15 10v8"></path>
                        <path d="M19 10v8"></path>
                        <path d="M3 18h18"></path>
                        <path d="M2 21h20"></path>
                    </svg>
                </div>
            `;
        }

        function renderAccounts(accounts) {
            const accountList =
                document.getElementById(
                    "account-list"
                );

            if (!accountList) {
                return;
            }

            accountList.innerHTML = "";

            if (!Array.isArray(accounts) || accounts.length === 0) {
                accountList.appendChild(createEmptyAccountState());
                return;
            }

            accounts.forEach(account => {
                const item = document.createElement("div");
                item.className = "account-item";

                item.innerHTML = `
            <div class="account-content">
                <div class="account-top">
                    <div class="account-identity">
                        <span class="bank-name">${escapeHtml(account.bankName)}</span>
                        <span class="account-divider">|</span>
                        <span class="account-number">${escapeHtml(account.maskedAccountNumber)}</span>
                    </div>

                    <strong class="account-balance">
                        ${formatBalance(account.balance)}원
                    </strong>
                </div>

            </div>
        `;

                accountList.appendChild(item);
            });
        }

        function formatBalance(balance) {
            if (balance == null) {
                return "0";
            }
            return Number(balance).toLocaleString("ko-KR");
        }

        function createEmptyAccountState() {
            const wrapper =
                document.createElement("div");

            wrapper.className =
                "account-empty";

            wrapper.innerHTML = `
        <strong class="account-empty-title">
            연결된 계좌가 없습니다
        </strong>

        <p class="account-empty-desc">
            계좌를 연결하면 정산 및 차용금 관리가<br>
            자동화되어 더욱 편리해집니다.
        </p>

        <button type="button"
                class="account-empty-button"
                id="connect-account-button">
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="12" cy="12" r="9"></circle>
                <path d="M12 8v8"></path>
                <path d="M8 12h8"></path>
            </svg>
            지금 바로 연결하기
        </button>
    `;

            wrapper
                .querySelector("#connect-account-button")
                ?.addEventListener(
                    "click",
                    handleConnectAccountClick
                );

            return wrapper;
        }

        async function handleConnectAccountClick() {
            const bankWindow = window.open(
                "about:blank",
                "sai-bank-link",
                "width=480,height=720"
            );

            try {
                const response = await authFetch(
                    "/api/accounts/link/start",
                    {
                        method: "POST"
                    }
                );

                if (!response.ok) {
                    bankWindow?.close();

                    const errorData =
                        await readJson(response);

                    window.alert(
                        errorData.message ||
                        "계좌 연동을 시작할 수 없습니다."
                    );

                    return;
                }

                const { redirectUrl } =
                    await readJson(response);

                if (bankWindow) {
                    bankWindow.location.href =
                        redirectUrl;
                }

            } catch (error) {
                bankWindow?.close();

                console.error(error);

                window.alert(
                    "계좌 연동을 시작할 수 없습니다."
                );
            }
        }window.addEventListener("message", (event) => {
            if (event.origin !== "http://localhost:8081") {
                return;
            }

            if (event.data?.type === "SAI_BANK_LINK_COMPLETE") {
                if (event.data.success) {
                    loadMyPage();
                } else {
                    window.alert("계좌 연동에 실패했습니다.");
                }
            }
        });

        function renderMyPage(rawData) {
            const data =
                rawData?.data ??
                rawData ??
                {};

            setText(
                "header-user-name",
                data.name ? `${data.name} 님` : ""
            );

            setText(
                "member-name",
                data.name
            );

            setText(
                "member-email",
                data.email
            );

            setText(
                "member-email-detail",
                data.email
            );

            setText(
                "member-birth-date",
                data.birthDate
            );

            setText(
                "member-phone",
                formatPhone(data.phone)
            );

            setText(
                "member-key",
                data.userKey
            );

            function setVerificationStatus(status) {
                const badge = document.getElementById("verification-status");

                if (!badge) {
                    return;
                }

                if (!status) {
                    badge.hidden = true;
                    badge.textContent = "";
                    return;
                }

                badge.hidden = false;
                badge.textContent = status;
            }
            setVerificationStatus(data.verificationStatus);

            setText(
                "joined-at",
                formatDateTime(
                    data.createdAt ??
                    data.joinedAt
                )
            );

            setText(
                "marketing-consent",
                data.marketingConsent ?? ""
            );

            setText(
                "user-token",
                data.userToken ?? ""
            );

            setText(
                "last-login-at",
                formatDateTime(data.lastLoginAt ?? data.lastLogin)
            );

            setProfileImage(
                data.profileImageUrl
            );

            renderAccounts(
                data.accounts ?? []
            );
        }

        async function loadMyPage() {
            if (FILE_PREVIEW) {
                loadPreviewMyPage();
                return;
            }

            const [
                meResponse,
                accountsResponse
            ] = await Promise.all([
                authFetch(
                    API.me,
                    {
                        method: "GET",
                        headers: {
                            "Accept":
                                "application/json"
                        }
                    }
                ),

                authFetch(
                    API.linkedAccounts,
                    {
                        method: "GET",
                        headers: {
                            "Accept":
                                "application/json"
                        }
                    }
                )
            ]);

            const meData = await readJson(meResponse);

            if (!meResponse.ok) {
                throw new Error(meData.message || "내 정보 조회에 실패했습니다.");
            }

            let accounts = [];
            if (accountsResponse.ok) {
                const accountsData = await readJson(accountsResponse);
                accounts = accountsData?.data ?? accountsData ?? [];
            } else {
                console.error("연결된 계좌 조회 실패");
            }

            renderMyPage({ ...(meData?.data ?? meData ?? {}), accounts });
        }

        function loadPreviewMyPage() {
            const auth =
                localStorage.getItem(
                    "saiwonjangPreviewAuth"
                );

            const member = JSON.parse(
                localStorage.getItem(
                    "saiwonjangCurrentUser"
                ) || "null"
            );

            if (!auth || !member) {
                redirectToLogin(true);
                return;
            }

            renderMyPage(member);
        }

        async function logout() {

            if (!FILE_PREVIEW) {
                try {
                    await fetch(
                        "/api/auth/logout",
                        {
                            method: "POST",
                            credentials: "include"
                        }
                    );
                } catch (error) {
                    console.error(
                        "로그아웃 API 호출 실패",
                        error
                    );
                }
            }

            sessionStorage.removeItem(
                "accessToken"
            );

            localStorage.removeItem(
                "saiwonjangPreviewAuth"
            );

            localStorage.removeItem(
                "saiwonjangCurrentUser"
            );

            redirectToLogin(false);
        }

        async function withdraw() {
            const confirmed = window.confirm(
                "정말 회원 탈퇴하시겠습니까?"
            );

            if (!confirmed) {
                return;
            }

            if (FILE_PREVIEW) {
                localStorage.removeItem(
                    "saiwonjangDemoMember"
                );

                logout();
                return;
            }

            const response = await authFetch(
                API.me,
                {
                    method: "DELETE"
                }
            );

            if (!response.ok) {
                const responseData =
                    await readJson(response);

                throw new Error(
                    responseData.message ||
                    "회원 탈퇴에 실패했습니다."
                );
            }

            try {
                await fetch(
                    "/api/auth/logout",
                    {
                        method: "POST",
                        credentials: "include"
                    }
                );
            } catch (error) {
                console.warn(
                    "탈퇴 후 토큰 정리 실패",
                    error
                );
            }

            sessionStorage.removeItem(
                "accessToken"
            );

            window.alert(
                "회원 탈퇴가 완료되었습니다."
            );

            redirectToLogin(false);
        }

        logoutButton?.addEventListener(
            "click",
            logout
        );

        withdrawButton?.addEventListener(
            "click",
            async () => {
                try {
                    await withdraw();
                } catch (error) {
                    console.error(error);

                    window.alert(
                        error.message ||
                        "회원 탈퇴 처리 중 오류가 발생했습니다."
                    );
                }
            }
        );

        document
            .querySelector(".add-account-button")
            ?.addEventListener("click", handleConnectAccountClick);

        try {
            await loadMyPage();
        } catch (error) {
            console.error(error);

            errorBox.textContent =
                error.message ||
                "내 정보를 불러오지 못했습니다.";

            errorBox.hidden = false;
        }

        async function readJson(response) {
            const text = await response.text();

            if (!text) {
                return {};
            }

            try {
                return JSON.parse(text);
            } catch {
                return {};
            }
        }

        function formatPhone(phone) {
            if (!phone) {
                return "";
            }

            const numbers =
                String(phone).replace(
                    /\D/g,
                    ""
                );

            if (numbers.length === 11) {
                return numbers.replace(
                    /(\d{3})(\d{4})(\d{4})/,
                    "$1-$2-$3"
                );
            }

            return phone;
        }

        function formatDateTime(value) {
            if (!value) {
                return "";
            }

            const date = new Date(value);

            if (
                Number.isNaN(date.getTime())
            ) {
                return value;
            }

            return new Intl.DateTimeFormat(
                "ko-KR",
                {
                    year: "numeric",
                    month: "2-digit",
                    day: "2-digit"
                }
            ).format(date);
        }

        function escapeHtml(value) {
            if (value == null) {
                return "";
            }

            return String(value)
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll('"', "&quot;")
                .replaceAll("'", "&#039;");
        }
    }
);
