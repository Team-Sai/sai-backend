document.addEventListener("DOMContentLoaded", () => {
    const FILE_PREVIEW = window.location.protocol === "file:";

    const ROUTES = {
        main: FILE_PREVIEW ? "../login/login.html" : "/",
        login: FILE_PREVIEW ? "../login/login.html" : "/login"
    };

    const API = {
        signup: "/api/auth/signup"
    };

    document.querySelectorAll("[data-route]").forEach(link => {
        const routeName = link.dataset.route;
        if (ROUTES[routeName]) {
            link.href = ROUTES[routeName];
        }
    });

    const form = document.getElementById("signup-form");
    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const nameInput = document.getElementById("full-name");
    const dobInput = document.getElementById("dob");
    const togglePassword = document.getElementById("toggle-password");
    const eyeIcon = document.getElementById("password-eye-icon");
    const signupError = document.getElementById("signup-error");
    const signupButton = document.getElementById("signup-button");

    const errors = {
        email: document.getElementById("email-error"),
        password: document.getElementById("password-error"),
        name: document.getElementById("name-error"),
        dob: document.getElementById("dob-error")
    };

    togglePassword.addEventListener("click", () => {
        const shouldShow = passwordInput.type === "password";

        passwordInput.type = shouldShow ? "text" : "password";
        togglePassword.setAttribute(
            "aria-label",
            shouldShow ? "비밀번호 숨기기" : "비밀번호 보기"
        );

        eyeIcon.innerHTML = shouldShow
            ? '<path d="m2 2 20 20"/><path d="M6.71 6.71C4.93 7.9 3.57 9.62 2.81 11.65a1 1 0 0 0 0 .7C4.32 16.12 7.89 18.5 12 18.5c1.18 0 2.29-.2 3.31-.56"/><path d="M10.73 10.73a2 2 0 0 0 2.54 2.54"/><path d="M14.12 5.68A9.95 9.95 0 0 0 12 5.5c-4.11 0-7.68 2.38-9.19 6.15"/><path d="M16.61 7.39c2.05 1.15 3.63 3 4.58 5.26a1 1 0 0 1 0 .7 10.1 10.1 0 0 1-1.46 2.4"/>'
            : '<path d="M2.062 12.348a1 1 0 0 1 0-.696C3.574 7.884 7.269 5.5 12 5.5s8.426 2.384 9.938 6.152a1 1 0 0 1 0 .696C20.426 16.116 16.731 18.5 12 18.5S3.574 16.116 2.062 12.348Z"/><circle cx="12" cy="12" r="3"/>';
    });

    dobInput.addEventListener("input", () => {
        dobInput.value = dobInput.value.replace(/\D/g, "").slice(0, 8);
    });

    [emailInput, passwordInput, nameInput, dobInput].forEach(input => {
        input.addEventListener("input", () => {
            input.classList.remove("invalid");
            signupError.hidden = true;

            const key =
                input === nameInput
                    ? "name"
                    : input === dobInput
                        ? "dob"
                        : input.id;

            errors[key].hidden = true;
        });
    });

    form.addEventListener("submit", async event => {
        event.preventDefault();

        const passwordPattern =
            /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,}$/;

        const valid = {
            email: emailInput.validity.valid,
            password: passwordPattern.test(passwordInput.value),
            name: nameInput.value.trim().length > 0,
            dob: /^\d{8}$/.test(dobInput.value)
        };

        const inputs = {
            email: emailInput,
            password: passwordInput,
            name: nameInput,
            dob: dobInput
        };

        Object.keys(valid).forEach(key => {
            inputs[key].classList.toggle("invalid", !valid[key]);
            errors[key].hidden = valid[key];
        });

        signupError.hidden = true;

        if (Object.values(valid).includes(false)) {
            return;
        }

        const payload = {
            email: emailInput.value.trim(),
            password: passwordInput.value,
            name: nameInput.value.trim(),
            birthDate: dobInput.value
        };

        const originalText = signupButton.textContent;
        signupButton.disabled = true;
        signupButton.textContent = "가입 처리 중";

        try {
            if (FILE_PREVIEW) {
                localStorage.setItem(
                    "saiwonjangDemoMember",
                    JSON.stringify({
                        ...payload,
                        joinedAt: new Date().toISOString().slice(0, 10),
                        verificationStatus: "",
                        marketingConsent: "",
                        profileImageUrl: "",
                        accounts: []
                    })
                );
            } else {
                const response = await fetch(API.signup, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    credentials: "include",
                    body: JSON.stringify(payload)
                });

                const responseData = await response.json().catch(() => ({}));

                if (!response.ok) {
                    throw new Error(
                        responseData.message || "회원가입에 실패했습니다."
                    );
                }
            }

            window.location.href = `${ROUTES.login}?signup=success`;
        } catch (error) {
            signupError.textContent =
                error.message || "회원가입 중 오류가 발생했습니다.";
            signupError.hidden = false;
        } finally {
            signupButton.disabled = false;
            signupButton.textContent = originalText;
        }
    });
});
