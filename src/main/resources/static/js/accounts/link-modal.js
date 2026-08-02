document.addEventListener("DOMContentLoaded", () => {
    const backdrop = document.getElementById("link-modal-backdrop");
    const closeButton = document.getElementById("link-modal-close");
    const cancelButton = document.getElementById("link-modal-cancel");
    const confirmButton = document.getElementById("link-modal-confirm");
    const agreeCheckbox = document.getElementById("link-agree-checkbox");
    const agreeDetailButton = document.getElementById("link-agree-detail");

    document.addEventListener("DOMContentLoaded", () => {
        const token = sessionStorage.getItem("accessToken");

        if (!token) {
            window.location.replace("/login?required=true");
            return;
        }
    });

    function openModal() {
        backdrop.hidden = false;
        document.body.style.overflow = "hidden";
    }

    function goBack() {
        window.location.href = "/mypage";
    }

    openModal();

    closeButton?.addEventListener("click", goBack);
    cancelButton?.addEventListener("click", goBack);

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !backdrop.hidden) {
            goBack();
        }
    });

    agreeCheckbox?.addEventListener("change", () => {
        confirmButton.disabled = !agreeCheckbox.checked;
    });

    agreeDetailButton?.addEventListener("click", () => {
        window.open("/terms/account-link", "_blank");
    });

    confirmButton?.addEventListener("click", async () => {
        confirmButton.disabled = true;
        const originalText = confirmButton.textContent;
        confirmButton.textContent = "연동 중...";

        try {
            const token = sessionStorage.getItem("accessToken");

            const response = await fetch("/api/mock-bank/link", {
                method: "POST",
                headers: {
                    "Authorization": `Bearer ${token}`
                }
            });

            if (!response.ok) {
                throw new Error("계좌 연동 준비에 실패했습니다.");
            }

            window.location.href = "/accounts/link/select";
        } catch (error) {
            console.error(error);
            window.alert(error.message || "계좌 연동 준비 중 오류가 발생했습니다.");
            confirmButton.disabled = false;
            confirmButton.textContent = originalText;
        }
    });
});