document.addEventListener("DOMContentLoaded", () => {
    const FILE_PREVIEW = window.location.protocol === "file:";

    const ROUTES = {
        main: FILE_PREVIEW ? "../login/login.html" : "/",
        signup: FILE_PREVIEW ? "../signup/signup.html" : "/signup",
        dashboard: FILE_PREVIEW ? "../integration/dashboard.html" : "/integration/dashboard"
    };

    const API = {
        login: "/api/auth/login"
    };

    document.querySelectorAll("[data-route]").forEach(link => {
        const routeName = link.dataset.route;

        if (ROUTES[routeName]) {
            link.href = ROUTES[routeName];
        }
    });

    const form = document.getElementById("login-form");
    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const rememberEmail = document.getElementById("remember-email");
    const togglePassword = document.getElementById("toggle-password");
    const eyeIcon = document.getElementById("password-eye-icon");
    const emailError = document.getElementById("email-error");
    const passwordError = document.getElementById("password-error");
    const loginError = document.getElementById("login-error");
    const loginButton = document.getElementById("login-button");
    const pageMessage = document.getElementById("page-message");

    if (!form) {
        return;
    }

    const params = new URLSearchParams(window.location.search);

    if (params.get("signup") === "success") {
        pageMessage.textContent =
            "회원가입이 완료되었습니다. 로그인해 주세요.";
        pageMessage.hidden = false;
    } else if (params.get("required") === "true") {
        pageMessage.textContent =
            "로그인이 필요한 페이지입니다.";
        pageMessage.hidden = false;
    }

    const savedEmail =
        localStorage.getItem("saiwonjangSavedEmail");

    if (savedEmail) {
        emailInput.value = savedEmail;
        rememberEmail.checked = true;
    }

    togglePassword?.addEventListener("click", () => {
        const shouldShow =
            passwordInput.type === "password";

        passwordInput.type =
            shouldShow ? "text" : "password";

        togglePassword.setAttribute(
            "aria-label",
            shouldShow
                ? "비밀번호 숨기기"
                : "비밀번호 보기"
        );

        eyeIcon.innerHTML = shouldShow
            ? '<path d="m2 2 20 20"/><path d="M6.71 6.71C4.93 7.9 3.57 9.62 2.81 11.65a1 1 0 0 0 0 .7C4.32 16.12 7.89 18.5 12 18.5c1.18 0 2.29-.2 3.31-.56"/><path d="M10.73 10.73a2 2 0 0 0 2.54 2.54"/><path d="M14.12 5.68A9.95 9.95 0 0 0 12 5.5c-4.11 0-7.68 2.38-9.19 6.15"/><path d="M16.61 7.39c2.05 1.15 3.63 3 4.58 5.26a1 1 0 0 1 0 .7 10.1 10.1 0 0 1-1.46 2.4"/>'
            : '<path d="M2.062 12.348a1 1 0 0 1 0-.696C3.574 7.884 7.269 5.5 12 5.5s8.426 2.384 9.938 6.152a1 1 0 0 1 0 .696C20.426 16.116 16.731 18.5 12 18.5S3.574 16.116 2.062 12.348Z"/><circle cx="12" cy="12" r="3"/>';
    });

    emailInput.addEventListener("input", () => {
        emailInput.classList.remove("invalid");
        emailError.hidden = true;
        loginError.hidden = true;
    });

    passwordInput.addEventListener("input", () => {
        passwordInput.classList.remove("invalid");
        passwordError.hidden = true;
        loginError.hidden = true;
    });

    form.addEventListener("submit", async event => {
        event.preventDefault();

        const emailValid = emailInput.validity.valid;
        const passwordValid =
            passwordInput.value.trim().length > 0;

        emailInput.classList.toggle(
            "invalid",
            !emailValid
        );

        passwordInput.classList.toggle(
            "invalid",
            !passwordValid
        );

        emailError.hidden = emailValid;
        passwordError.hidden = passwordValid;
        loginError.hidden = true;

        if (!emailValid || !passwordValid) {
            return;
        }

        if (rememberEmail.checked) {
            localStorage.setItem(
                "saiwonjangSavedEmail",
                emailInput.value.trim()
            );
        } else {
            localStorage.removeItem(
                "saiwonjangSavedEmail"
            );
        }

        const originalText =
            loginButton.textContent;

        loginButton.disabled = true;
        loginButton.textContent = "로그인 중";

        try {
            if (FILE_PREVIEW) {
                loginWithPreviewData();
            } else {
                await loginWithApi();
            }

            window.location.replace(ROUTES.dashboard);

        } catch (error) {
            console.error(error);

            loginError.textContent =
                error.message ||
                "로그인 중 오류가 발생했습니다.";

            loginError.hidden = false;

        } finally {
            loginButton.disabled = false;
            loginButton.textContent = originalText;
        }
    });

    function loginWithPreviewData() {
        const member = JSON.parse(
            localStorage.getItem(
                "saiwonjangDemoMember"
            ) || "null"
        );

        if (
            !member ||
            member.email !== emailInput.value.trim() ||
            member.password !== passwordInput.value
        ) {
            throw new Error(
                "가입한 이메일 또는 비밀번호를 확인해 주세요."
            );
        }

        localStorage.setItem(
            "saiwonjangCurrentUser",
            JSON.stringify(member)
        );

        localStorage.setItem(
            "saiwonjangPreviewAuth",
            "authenticated"
        );
    }

    async function loginWithApi() {
        const response = await fetch(API.login, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json"
            },
            body: JSON.stringify({
                email: emailInput.value.trim(),
                password: passwordInput.value
            })
        });

        const responseData =
            await readJson(response);

        if (!response.ok) {
            throw new Error(
                responseData.message ||
                "이메일 또는 비밀번호를 확인해 주세요."
            );
        }

        const token =
            responseData.accessToken ??
            responseData.data?.accessToken;

        if (!token) {
            console.error(
                "로그인 API 응답:",
                responseData
            );

            throw new Error(
                "로그인 토큰을 전달받지 못했습니다."
            );
        }

        const normalizedToken =
            token.startsWith("Bearer ")
                ? token.substring(7)
                : token;

        sessionStorage.setItem(
            "accessToken",
            normalizedToken
        );
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
});
