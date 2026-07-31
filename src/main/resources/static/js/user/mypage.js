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
            me: "/api/users/me"
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

            if (
                !Array.isArray(accounts) ||
                accounts.length === 0
            ) {
                return;
            }

            accounts.forEach(account => {
                const item =
                    document.createElement("div");

                item.className =
                    "account-item";

                item.innerHTML = `
                    ${createBankIcon()}

                    <div class="account-content">
                        <div class="account-top">
                            <div>
                                <div class="bank-name">
                                    ${escapeHtml(
                    account.bankName
                )}
                                </div>

                                <div class="account-name">
                                    ${escapeHtml(
                    account.accountName
                )}
                                </div>
                            </div>

                            <strong class="account-balance">
                                ${escapeHtml(
                    account.balance
                )}
                            </strong>
                        </div>

                        <div class="account-number">
                            ${escapeHtml(
                    account.maskedAccountNumber
                )}
                        </div>
                    </div>
                `;

                accountList.appendChild(item);
            });
        }

        function renderMyPage(rawData) {
            const data =
                rawData?.data ??
                rawData ??
                {};

            setText(
                "header-user-name",
                data.name
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

            // 현재 API에서 반환하지 않는 정보는 빈칸 처리
            setText(
                "verification-status",
                data.verificationStatus ?? ""
            );

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

            const token =
                sessionStorage.getItem(
                    "accessToken"
                );

            if (!token) {
                redirectToLogin(true);
                return;
            }

            const response = await fetch(
                API.me,
                {
                    method: "GET",
                    headers: {
                        "Accept":
                            "application/json",

                        "Authorization":
                            `Bearer ${token}`
                    }
                }
            );

            if (
                response.status === 401 ||
                response.status === 403
            ) {
                sessionStorage.removeItem(
                    "accessToken"
                );

                redirectToLogin(true);
                return;
            }

            const responseData =
                await readJson(response);

            if (!response.ok) {
                throw new Error(
                    responseData.message ||
                    "내 정보 조회에 실패했습니다."
                );
            }

            renderMyPage(responseData);
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

        function logout() {
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

            const token =
                sessionStorage.getItem(
                    "accessToken"
                );

            if (!token) {
                redirectToLogin(true);
                return;
            }

            const response = await fetch(
                API.me,
                {
                    method: "DELETE",
                    headers: {
                        "Authorization":
                            `Bearer ${token}`
                    }
                }
            );

            if (
                response.status === 401 ||
                response.status === 403
            ) {
                sessionStorage.removeItem(
                    "accessToken"
                );

                redirectToLogin(true);
                return;
            }

            if (!response.ok) {
                const responseData =
                    await readJson(response);

                throw new Error(
                    responseData.message ||
                    "회원 탈퇴에 실패했습니다."
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