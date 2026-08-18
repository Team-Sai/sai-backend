function authHeaders(extra) {
    const token = sessionStorage.getItem("accessToken");
    return Object.assign(
        token ? { Authorization: `Bearer ${token}` } : {},
        extra || {}
    );
}

document.addEventListener("DOMContentLoaded", () => {

    const btnFamily = document.getElementById("btnFamily");
    const btnAcquaintance = document.getElementById("btnAcquaintance");
    const confirmBtn = document.getElementById("relationConfirmBtn");
    const closeBtn = document.getElementById("relationCloseBtn");

    let selectedRelation = null;

    function selectOption(targetBtn, relationType) {

        btnFamily.setAttribute("aria-checked", "false");
        btnAcquaintance.setAttribute("aria-checked", "false");

        targetBtn.setAttribute("aria-checked", "true");
        selectedRelation = relationType;

        if (confirmBtn) {
            confirmBtn.disabled = false;
        }
    }

    btnFamily?.addEventListener("click", () => {
        selectOption(btnFamily, "FAMILY");
    });

    btnAcquaintance?.addEventListener("click", () => {
        selectOption(btnAcquaintance, "ACQUAINTANCE");
    });

    confirmBtn?.addEventListener("click", () => {
        if (!selectedRelation) return;

        if (selectedRelation === "FAMILY") {
            window.location.href = "/contracts/new?relation=FAMILY";
        } else if (selectedRelation === "ACQUAINTANCE") {
            window.location.href = "/contracts/new";
        }
    });

    closeBtn?.addEventListener("click", () => {
        const overlay = document.getElementById("relationModalOverlay");
        if (overlay) {
            overlay.style.display = "none";
        }
    });
});