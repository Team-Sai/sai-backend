document.addEventListener("DOMContentLoaded", () => {
    const settlementId = getSettlementIdFromPath();

    const toast = document.getElementById("toast");
    const closeButton = document.getElementById("close-button");
    const syncButton = document.getElementById("sync-button");
    const refreshButton = document.getElementById("refresh-button");
    const changeAccountButton =
        document.getElementById("change-account-button");

    if (!settlementId) {
        showToast(
            "정산 ID를 확인할 수 없습니다.",
            true
        );
        return;
    }

    loadPage();

    if (refreshButton) {
        refreshButton.addEventListener(
            "click",
            loadPage
        );
    }

    if (closeButton) {
        closeButton.addEventListener(
            "click",
            closeSettlement
        );
    }

    if (syncButton) {
        syncButton.addEventListener(
            "click",
            syncTransactions
        );
    }

    async function syncTransactions() {
        try {
            syncButton.disabled = true;

            const result = await requestJson(
                "/api/transactions/sync",
                {
                    method: "POST"
                }
            );

            showToast(
                `동기화 완료: 자동반영 ${result.appliedCount}건, 확인필요
              ${result.needsCheckCount}건, 미매칭 ${result.unmatchedCount}건`
            );

            await loadPage();

        } catch (error) {
            console.error("거래내역 동기화 실패:", error);

            showToast(
                error.message || "거래내역 동기화에 실패했습니다.",
                true
            );

        } finally {
            syncButton.disabled = false;
        }
    }

    async function loadPage() {
        try {
            setLoading(true);

            const [
                detail,
                paymentStatus
            ] = await Promise.all([
                requestJson(
                    `/api/settlements/${settlementId}`
                ),
                requestJson(
                    `/api/settlements/${settlementId}/payment-status`
                )
            ]);

            renderDetail(detail);
            renderPaymentStatus(paymentStatus);

            await loadSettlementAccount();

            const syncTime =
                document.getElementById(
                    "sync-time"
                );

            if (syncTime) {
                syncTime.textContent =
                    formatDateTime(new Date());
            }

        } catch (error) {
            console.error(
                "정산 상세 조회 실패:",
                error
            );

            showToast(
                error.message ||
                "정산 상세 정보를 불러오지 못했습니다.",
                true
            );

        } finally {
            setLoading(false);
        }
    }
    async function loadSettlementAccount() {
        try {
            const account =
                await requestSettlementAccount();

            renderSettlementAccount(
                account
            );

        } catch (error) {
            console.error(
                "수취 계좌 조회 실패:",
                error
            );

            renderSettlementAccount(null);
        }
    }

    async function requestSettlementAccount() {
        const response =
            await authFetch(
                `/api/settlements/${settlementId}/account`,
                {
                    method: "GET"
                }
            );

        if (response.status === 403) {
            throw new Error(
                "수취 계좌 조회 권한이 없습니다."
            );
        }

        if (response.status === 404) {
            return null;
        }

        if (!response.ok) {
            throw new Error(
                "수취 계좌 조회에 실패했습니다."
            );
        }

        return response.json();
    }
    async function requestJson(
        url,
        options = {}
    ) {
        const response =
            await authFetch(
                url,
                options
            );

        if (response.status === 403) {
            throw new Error(
                "해당 정산을 조회할 권한이 없습니다."
            );
        }

        if (response.status === 404) {
            throw new Error(
                "정산을 찾을 수 없습니다."
            );
        }

        if (!response.ok) {
            let message =
                "요청 처리에 실패했습니다.";

            try {
                const body =
                    await response.json();

                message =
                    body.message ||
                    message;

            } catch (error) {
            }

            throw new Error(message);
        }

        if (response.status === 204) {
            return null;
        }

        return response.json();
    }

    function renderDetail(detail) {
        setText(
            "settlement-title",
            detail.title ||
            "이름 없는 정산"
        );

        setText(
            "settlement-category",
            detail.settlementCategory ||
            "-"
        );

        setText(
            "due-date",
            formatDate(
                detail.dueDate
            )
        );

        setText(
            "created-at",
            formatDate(
                detail.createdAt
            )
        );

        setText(
            "split-type",
            getSplitTypeText(
                detail.splitType
            )
        );

        setText(
            "settlement-status-badge",
            getSettlementStatusText(
                detail.settlementStatus
            )
        );

        setText(
            "settlement-role-badge",
            getRoleText(
                detail.role
            )
        );

        if (closeButton) {
            closeButton.hidden =
                detail.role !== "OWNER";

            closeButton.disabled =
                detail.settlementStatus ===
                "CLOSED";
        }
    }
    function renderPaymentStatus(status) {
        setText(
            "total-expected-amount",
            formatMoney(status.totalExpectedAmount)
        );

        setText(
            "total-paid-amount",
            formatMoney(status.totalPaidAmount)
        );

        setText(
            "total-remaining-amount",
            formatMoney(status.totalRemainingAmount)
        );

        const progressRate =
            normalizeRate(status.progressRate);

        setText(
            "amount-progress-rate",
            `${progressRate}%`
        );

        const progressBar =
            document.getElementById("progress-bar");

        if (progressBar) {
            progressBar.style.width =
                `${progressRate}%`;
        }

        const obligations =
            Array.isArray(status.obligations)
                ? status.obligations
                : [];

        const totalCount =
            obligations.length;

        const paidCount =
            obligations.filter(
                obligation =>
                    obligation.paymentStatus === "PAID"
            ).length;

        setText(
            "completed-target-count",
            paidCount
        );

        setText(
            "total-target-count",
            totalCount
        );

        setText(
            "participant-count",
            totalCount
        );

        const participantProgressRate =
            totalCount === 0
                ? 0
                : Math.round(
                    (paidCount / totalCount) * 100
                );

        setText(
            "target-completion-rate",
            `${participantProgressRate}%`
        );


        setText(
            "attention-count",
            "0명"
        );

        renderParticipants(
            obligations
        );

        renderParticipantAvatars(
            obligations
        );

        if (closeButton) {
            closeButton.disabled =
                !status.closable;
        }
    }

    function renderSettlementAccount(account) {
        if (!account) {
            setText(
                "bank-name",
                "수취 계좌"
            );

            setText(
                "masked-account-number",
                "계좌 정보 없음"
            );

            setText(
                "account-holder-name",
                "-"
            );

            return;
        }

        setText(
            "bank-name",
            account.bankName || "수취 계좌"
        );

        setText(
            "masked-account-number",
            account.maskedAccountNumber ||
            "계좌 정보 없음"
        );

        setText(
            "account-holder-name",
            account.accountHolderName || "-"
        );
    }
    function renderParticipants(obligations) {
        const tableBody =
            document.getElementById(
                "payment-table-body"
            );

        const emptyState =
            document.getElementById(
                "empty-participants"
            );

        if (!tableBody) {
            return;
        }

        tableBody.innerHTML = "";

        if (obligations.length === 0) {
            if (emptyState) {
                emptyState.hidden = false;
            }

            return;
        }

        if (emptyState) {
            emptyState.hidden = true;
        }

        obligations.forEach(
            obligation => {

                const row =
                    document.createElement("tr");

                row.innerHTML = `
                <td class="name-cell">
                    ${escapeHtml(
                    obligation.participantName ||
                    `참여자 #${obligation.participantId}`
                )}
                </td>

                <td>
                    ${formatMoney(
                    obligation.expectedAmount
                )}원
                </td>

                <td>
                    ${formatMoney(
                    obligation.paidAmount
                )}원
                </td>

                <td>
                    ${formatMoney(
                    obligation.remainingAmount
                )}원
                </td>

                <td>
                    -
                </td>

                <td>
                    ${createPaymentStatusChip(
                    obligation.paymentStatus
                )}
                </td>

                <td>
                    <button
                        class="manage-button"
                        type="button"
                        disabled
                    >
                        -
                    </button>
                </td>
            `;

                tableBody.appendChild(row);
            }
        );
    }

    function renderParticipantAvatars(
        obligations
    ) {
        const container =
            document.getElementById(
                "participant-avatar-list"
            );

        if (!container) {
            return;
        }

        container.innerHTML = "";

        if (obligations.length === 0) {
            container.innerHTML = `
            <div class="participant-avatar">
                <small>
                    참여자가 없습니다.
                </small>
            </div>
        `;

            return;
        }

        obligations
            .slice(0, 6)
            .forEach(
                obligation => {

                    const item =
                        document.createElement(
                            "div"
                        );

                    item.className =
                        "participant-avatar";

                    item.innerHTML = `
                    <div class="avatar-circle">
                        ◎
                    </div>

                    <strong>
                        ${escapeHtml(
                            obligation.participantName ||
                            `참여자 #${obligation.participantId}`
                    )}
                    </strong>

                    <small>
                        ${escapeHtml(
                        getPaymentStatusText(
                            obligation.paymentStatus
                        )
                    )}
                    </small>
                `;

                    container.appendChild(
                        item
                    );
                }
            );
    }


    async function closeSettlement() {
        if (
            !window.confirm(
                "이 정산을 마감하시겠습니까?"
            )
        ) {
            return;
        }

        try {
            if (closeButton) {
                closeButton.disabled =
                    true;
            }

            await requestJson(
                `/api/settlements/${settlementId}/close`,
                {
                    method: "POST"
                }
            );

            showToast(
                "정산이 마감되었습니다."
            );

            await loadPage();

        } catch (error) {
            console.error(
                "정산 마감 실패:",
                error
            );

            showToast(
                error.message ||
                "정산 마감에 실패했습니다.",
                true
            );

        } finally {
            if (closeButton) {
                closeButton.disabled =
                    false;
            }
        }
    }

    function createPaymentStatusChip(
        status
    ) {
        if (status === "PAID") {
            return `
                <span
                    class="status-chip status-paid"
                >
                    ● 입금 완료
                </span>
            `;
        }

        if (
            status ===
            "PARTIALLY_PAID"
        ) {
            return `
                <span
                    class="status-chip status-partial"
                >
                    ● 일부 입금
                </span>
            `;
        }

        return `
            <span
                class="status-chip status-unpaid"
            >
                ● 미입금
            </span>
        `;
    }

    function getPaymentStatusText(
        status
    ) {
        switch (status) {
            case "PAID":
                return "입금 완료";

            case "PARTIALLY_PAID":
                return "일부 입금";

            case "UNPAID":
                return "미입금";

            default:
                return "-";
        }
    }

    function getSettlementStatusText(
        status
    ) {
        switch (status) {
            case "IN_PROGRESS":
                return "진행중";

            case "CLOSED":
                return "완료";

            case "CANCELLED":
                return "취소";

            default:
                return "-";
        }
    }

    function getSplitTypeText(
        splitType
    ) {
        switch (splitType) {
            case "EQUAL":
                return "균등";

            case "CUSTOM":
                return "직접 설정";

            default:
                return "-";
        }
    }

    function getRoleText(role) {
        switch (role) {
            case "OWNER":
                return "정산자";

            case "MEMBER":
                return "참여자";

            default:
                return "-";
        }
    }

    function getSettlementIdFromPath() {
        const match =
            window.location.pathname.match(
                /^\/settlements\/(\d+)\/?$/
            );

        return match
            ? match[1]
            : null;
    }

    function normalizeRate(value) {
        const number =
            Number(value || 0);

        if (!Number.isFinite(number)) {
            return 0;
        }

        return Math.max(
            0,
            Math.min(
                100,
                Math.round(
                    number * 100
                ) / 100
            )
        );
    }

    function formatMoney(value) {
        const number =
            Number(value || 0);

        if (!Number.isFinite(number)) {
            return "0";
        }

        return new Intl.NumberFormat(
            "ko-KR"
        ).format(number);
    }

    function formatDate(value) {
        if (!value) {
            return "-";
        }

        const parts =
            String(value)
                .split("T")[0]
                .split("-");

        if (parts.length !== 3) {
            return String(value);
        }

        return `${parts[0]}.${parts[1]}.${parts[2]}`;
    }

    function formatDateTime(value) {
        if (!value) {
            return "-";
        }

        const date =
            value instanceof Date
                ? value
                : new Date(value);

        if (
            Number.isNaN(
                date.getTime()
            )
        ) {
            return String(value);
        }

        return new Intl.DateTimeFormat(
            "ko-KR",
            {
                year: "numeric",
                month: "2-digit",
                day: "2-digit",
                hour: "2-digit",
                minute: "2-digit"
            }
        ).format(date);
    }

    function setText(
        id,
        value
    ) {
        const element =
            document.getElementById(id);

        if (element) {
            element.textContent =
                value;
        }
    }

    function escapeHtml(value) {
        return String(value)
            .replaceAll(
                "&",
                "&amp;"
            )
            .replaceAll(
                "<",
                "&lt;"
            )
            .replaceAll(
                ">",
                "&gt;"
            )
            .replaceAll(
                '"',
                "&quot;"
            )
            .replaceAll(
                "'",
                "&#039;"
            );
    }

    function setLoading(loading) {
        if (!refreshButton) {
            return;
        }

        refreshButton.disabled =
            loading;

        refreshButton.textContent =
            loading
                ? "불러오는 중..."
                : "↻ 새로고침";
    }

    function showToast(
        message,
        isError = false
    ) {
        if (!toast) {
            return;
        }

        toast.textContent =
            message;

        toast.classList.toggle(
            "error",
            isError
        );

        toast.classList.add(
            "visible"
        );

        window.clearTimeout(
            showToast.timer
        );

        showToast.timer =
            window.setTimeout(
                () => {
                    toast.classList.remove(
                        "visible"
                    );
                },
                3000
            );
    }
});