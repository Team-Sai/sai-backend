document.addEventListener("DOMContentLoaded", () => {
    const resultBox =
        document.getElementById("result");

    const purposeSelect =
        document.getElementById("purpose");

    const verificationButton =
        document.getElementById("verification-button");

    verificationButton.addEventListener(
        "click",
        startVerification
    );

    async function startVerification() {
        const accessToken = getAccessToken();

        if (!accessToken) {
            printResult(
                "로그인 정보가 없습니다. 먼저 로그인해 주세요."
            );
            return;
        }

        setLoading(true);

        try {
            printResult(
                "본인인증 요청을 준비하고 있습니다."
            );

            const prepare =
                await prepareIdentityVerification(
                    accessToken,
                    purposeSelect.value
                );

            printResult(
                "본인인증 창을 여는 중입니다."
            );

            await requestPortOneVerification(prepare);

            printResult(
                "인증 결과를 확인하고 있습니다."
            );

            const completeResult =
                await completeIdentityVerification(
                    accessToken,
                    prepare.identityVerificationId
                );

            if (completeResult.status !== "VERIFIED") {
                throw new Error(
                    "본인인증 완료 상태를 확인할 수 없습니다."
                );
            }

            printResult(
                "본인인증이 완료되었습니다."
            );
            setTimeout(() => {
                window.location.href = `/api/contracts?identityVerificationId=${prepare.identityVerificationId}`;
            }, 1000);

        } catch (error) {
            console.error(error);

            printResult({
                step: "FAILED",
                message:
                    error.message
                    ?? "본인인증 처리 중 오류가 발생했습니다."
            });

        } finally {
            setLoading(false);
        }
    }

    async function prepareIdentityVerification(
        accessToken,
        purpose
    ) {
        const response = await fetch(
            "/api/identity-verifications",
            {
                method: "POST",

                headers: {
                    "Authorization":
                        `Bearer ${accessToken}`,

                    "Content-Type":
                        "application/json"
                },

                body: JSON.stringify({
                    purpose
                })
            }
        );

        const result = await readResponse(response);

        if (!response.ok) {
            throw new Error(
                result.message
                ?? `준비 요청 실패: ${response.status}`
            );
        }

        validatePrepareResponse(result);

        return result;
    }

    async function requestPortOneVerification(prepare) {
        if (
            typeof PortOne === "undefined"
            || typeof PortOne.requestIdentityVerification
            !== "function"
        ) {
            throw new Error(
                "포트원 SDK를 불러오지 못했습니다."
            );
        }

        const response =
            await PortOne.requestIdentityVerification({
                storeId:
                prepare.storeId,

                channelKey:
                prepare.channelKey,

                identityVerificationId:
                prepare.identityVerificationId
            });

        if (response.code != null) {
            throw new Error(
                response.message
                ?? "포트원 본인인증에 실패했습니다."
            );
        }

        if (
            response.identityVerificationId
            && response.identityVerificationId
            !== prepare.identityVerificationId
        ) {
            throw new Error(
                "본인인증 요청 식별값이 일치하지 않습니다."
            );
        }
    }

    async function completeIdentityVerification(
        accessToken,
        identityVerificationId
    ) {
        const encodedId =
            encodeURIComponent(identityVerificationId);

        const response = await fetch(
            `/api/identity-verifications/${encodedId}/complete`,
            {
                method: "POST",

                headers: {
                    "Authorization":
                        `Bearer ${accessToken}`
                }
            }
        );

        const result = await readResponse(response);

        if (!response.ok) {
            throw new Error(
                result.message
                ?? `완료 요청 실패: ${response.status}`
            );
        }

        return result;
    }

    function validatePrepareResponse(prepare) {
        if (
            !prepare.identityVerificationId
            || !prepare.storeId
            || !prepare.channelKey
        ) {
            throw new Error(
                "본인인증 준비 응답이 올바르지 않습니다."
            );
        }
    }

    function getAccessToken() {
        const directToken =
            sessionStorage.getItem("accessToken")
            ?? sessionStorage.getItem(
                "saiwonjangAccessToken"
            );

        if (directToken) {
            return removeBearerPrefix(directToken);
        }

        const authJson =
            sessionStorage.getItem("saiwonjangAuth");

        if (!authJson) {
            return null;
        }

        try {
            const auth = JSON.parse(authJson);

            const token =
                auth.accessToken
                ?? auth.token
                ?? null;

            return token
                ? removeBearerPrefix(token)
                : null;

        } catch (error) {
            console.error(
                "로그인 정보 파싱 실패",
                error
            );

            return null;
        }
    }

    function removeBearerPrefix(token) {
        return token
            .trim()
            .replace(/^Bearer\s+/i, "");
    }

    async function readResponse(response) {
        const text = await response.text();

        if (!text) {
            return {};
        }

        try {
            return JSON.parse(text);

        } catch {
            return {
                raw: text
            };
        }
    }

    function setLoading(loading) {
        verificationButton.disabled = loading;

        verificationButton.textContent =
            loading
                ? "본인인증 처리 중..."
                : "본인인증 시작";
    }

    function printResult(value) {
        resultBox.textContent =
            typeof value === "string"
                ? value
                : JSON.stringify(
                    value,
                    null,
                    2
                );
    }
});