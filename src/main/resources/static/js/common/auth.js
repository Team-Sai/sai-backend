let reissuePromise = null;
function getAccessToken() {
    return sessionStorage.getItem("accessToken");
}
function setAccessToken(accessToken) {
    sessionStorage.setItem("accessToken", accessToken);
}
function clearStoredAuth() {
    sessionStorage.removeItem("accessToken");
}
async function reissueAccessToken() {
    if (reissuePromise) {
        return reissuePromise;
    }
    reissuePromise = (async () => {
        try {
            const response = await fetch("/api/auth/reissue", {
                method: "POST",
                credentials: "include"
            });
            if (!response.ok) {
                clearStoredAuth();
                return null;
            }
            const body = await response.json();
            setAccessToken(body.accessToken);
            return body.accessToken;
        } finally {
            reissuePromise = null;
        }
    })();
    return reissuePromise;
}

async function authFetch(url, options = {}) {
    let accessToken = getAccessToken();

    if (!accessToken) {
        accessToken = await reissueAccessToken();

        if (!accessToken) {
            redirectToLogin();
            throw new Error("로그인이 필요합니다.");
        }
    }

    const headers = new Headers(
        options.headers || {}
    );

    headers.set(
        "Authorization",
        `Bearer ${accessToken}`
    );

    let response = await fetch(url, {
        ...options,
        headers,
        credentials: "include"
    });

    if (response.status !== 401) {
        return response;
    }

    const newAccessToken =
        await reissueAccessToken();

    if (!newAccessToken) {
        redirectToLogin();
        throw new Error(
            "로그인이 만료되었습니다."
        );
    }

    headers.set(
        "Authorization",
        `Bearer ${newAccessToken}`
    );

    const retryResponse = await fetch(url, {
        ...options,
        headers,
        credentials: "include"
    });

    if (retryResponse.status === 401) {
        redirectToLogin();
        throw new Error(
            "로그인이 만료되었습니다."
        );
    }

    return retryResponse;
}

function redirectToLogin() {
    clearStoredAuth();

    window.location.href =
        "/login?required=true";
}