document.addEventListener("DOMContentLoaded", () => {
    initNotificationCenter();
});

function initNotificationCenter() {

    const state = {
        notifications: [],
        activeCategory: "ALL"
    };

    const notifListEl =
        document.getElementById("notifList");



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


    function formatTimeLabel(dateStr) {

        if (!dateStr) {
            return "";
        }

        const date = new Date(dateStr);

        if (Number.isNaN(date.getTime())) {
            return "";
        }

        const diffMs =
            Math.max(
                Date.now() - date.getTime(),
                0
            );

        const minutes =
            Math.floor(
                diffMs / (60 * 1000)
            );

        const hours =
            Math.floor(
                diffMs / (60 * 60 * 1000)
            );

        const days =
            Math.floor(
                diffMs /
                (24 * 60 * 60 * 1000)
            );

        if (days > 0) {
            return `${days}일 전`;
        }

        if (hours > 0) {
            return `${hours}시간 전`;
        }

        if (minutes > 0) {
            return `${minutes}분 전`;
        }

        return "방금 전";
    }


    function resolveNotificationView(
        notification
    ) {

        switch (
            notification.notificationType
            ) {

            case "CONTRACT_REQUESTED":
                return {
                    category: "SIGN",
                    iconClass: "icon-orange",
                    accentClass: "accent-orange",
                    ctaLabel: "서명하러 가기",
                    ctaUrl:
                        `/contracts/${notification.referenceId}/approve`
                };

            case "CONTRACT_CHANGE":
                return notification.secondaryReferenceId
                    ? {
                        category: "SIGN",
                        iconClass: "icon-indigo",
                        accentClass: "accent-indigo",
                        ctaLabel: "변경 요청 확인하기",
                        ctaUrl:
                            `/contracts/${notification.referenceId}/change-requests/${notification.secondaryReferenceId}`
                    }
                    : {
                        category: "SIGN",
                        iconClass: "icon-indigo",
                        accentClass: "accent-indigo",
                        ctaLabel: "계약서 보기",
                        ctaUrl:
                            `/contracts/${notification.referenceId}/contract-detail`
                    };

            case "SETTLEMENT_DUE_REMINDER_D3":
            case "SETTLEMENT_DUE_REMINDER_D1":
            case "SETTLEMENT_DUE_REMINDER_DDAY":
            case "SETTLEMENT_PARTICIPANT_ADDED":
                return {
                    category: "SETTLEMENT",
                    iconClass: "icon-green",
                    accentClass: "accent-green",
                    ctaLabel: "정산 보기",
                    ctaUrl: null,
                    ctaLabel: null
                };

            case "BANK_TRANSACTION_MATCHING_REVIEW":
                return {
                    category: "SYSTEM",
                    iconClass: notification.resolved
                        ? "icon-gray"
                        : "icon-orange",
                    accentClass: notification.resolved
                        ? ""
                        : "accent-orange",
                    ctaLabel: notification.resolved
                        ? "처리 완료"
                        : "매칭 확인하기",
                    ctaUrl: null,
                    ctaAction: notification.resolved
                        ? null
                        : "MATCHING_REVIEW"
                };

            default:
                return {
                    category: "SYSTEM",
                    iconClass: "icon-gray",
                    accentClass: "",
                    ctaLabel: null,
                    ctaUrl: null
                };
        }
    }


    function normalizeNotification(
        notification
    ) {

        const view =
            resolveNotificationView(
                notification
            );

        const referenceTitle = notification.referenceTitle || "";
        const settlementTypeLabel =
            notification.settlementType === "RECURRING"
                ? "정기정산"
                : notification.settlementType === "SHARED"
                    ? "공동정산"
                    : null;
        const displayTitle =
            notification.notificationType === "SETTLEMENT_PARTICIPANT_ADDED"
                ? `${referenceTitle || "새로운 정산"}에 참여자로 등록되었습니다.`
                : notification.title;
        const finalTitle = referenceTitle || displayTitle;
        const finalDescription =
            notification.notificationType === "SETTLEMENT_PARTICIPANT_ADDED"
                ? "참여자로 등록되었습니다."
                : notification.content;
        const normalizedCtaLabel = view.category === "SIGN" ? null : view.ctaLabel;

        return {
            id:
                notification.notificationId,

            notificationType:
                notification.notificationType,

            category:
                view.category,

            title: escapeHtml(finalTitle),

            categoryLabel:
                view.category === "SIGN"
                    ? "\uCC28\uC6A9\uC99D"
                    : view.category === "SYSTEM"
                        ? "\uACF5\uC9C0\uC0AC\uD56D"
                        : "\uC815\uC0B0",

            description:
                escapeHtml(
                    finalDescription
                ),

            timeLabel:
                formatTimeLabel(
                    notification.createdAt
                ),

            ctaLabel:
                normalizedCtaLabel,

            ctaUrl:
                view.ctaUrl,

            ctaAction:
                view.ctaAction || null,

            referenceId:
                notification.referenceId,

            secondaryReferenceId:
                notification.secondaryReferenceId,

            referenceTitle: escapeHtml(referenceTitle),
            referenceType: notification.referenceType || null,
            settlementType: notification.settlementType || null,
            settlementTypeLabel,

            resolved:
                Boolean(notification.resolved),

            iconClass:
                view.iconClass,

            accentClass:
                view.accentClass
        };
    }


    async function fetchNotifications() {

        try {
            const response =
                await authFetch(
                    "/api/notifications",
                    {
                        method: "GET",
                        headers: {
                            Accept:
                                "application/json"
                        }
                    }
                );

            if (!response.ok) {
                throw new Error(
                    "알림을 불러오지 못했습니다."
                );
            }


            const responseBody =
                await response.json();


            state.notifications =
                (
                    Array.isArray(
                        responseBody
                    )
                        ? responseBody
                        : []
                )
                .map(
                    normalizeNotification
                );


            render();

        } catch (error) {

            console.error(
                "알림 조회 실패",
                error
            );

            notifListEl.innerHTML = `
                <li class="empty-notification">
                    알림을 불러올 수 없습니다.
                </li>
            `;
        }
    }


    function render() {

        const filteredList =
            state.notifications.filter(
                (notification) => {

                    if (
                        state.activeCategory
                        === "ALL"
                    ) {
                        return true;
                    }

                    return (
                        notification.category
                        ===
                        state.activeCategory
                    );
                }
            );


        if (
            filteredList.length === 0
        ) {

            notifListEl.innerHTML = `
                <li class="empty-notification">
                    해당 알림이 없습니다.
                </li>
            `;

            return;
        }


        notifListEl.innerHTML =
            filteredList
                .map(
                    (notification) => {

                        const ctaHtml =
                            notification.ctaLabel
                                ? `
                                    <button
                                        type="button"
                                        class="notif-cta"
                                        ${notification.resolved ? "disabled" : ""}
                                    >
                                        ${notification.ctaLabel}
                                    </button>
                                `
                                : "";


                        return `
                            <li
                                class="
                                    notif-card
                                    ${notification.accentClass}
                                "
                                data-id="${notification.id}"
                            >

                                <div class="notif-body">

                                    <div class="notif-row">

                                        <span class="notification-badge notification-badge-${notification.category.toLowerCase()}">
                                            ${notification.category === "SETTLEMENT" ? (notification.settlementTypeLabel || "정산") : notification.categoryLabel}
                                        </span>

                                        <span
                                            class="notif-title"
                                        >
                                            ${notification.title}
                                        </span>

                                        <span
                                            class="notif-time"
                                        >
                                            ${notification.timeLabel}
                                        </span>

                                        <span class="notif-desc">
                                            ${notification.description}
                                        </span>

                                    </div>


                                    ${ctaHtml}

                                </div>

                            </li>
                        `;
                    }
                )
                .join("");
    }


    document
        .querySelectorAll(
            ".filter-tab"
        )
        .forEach(
            (tab) => {

                tab.addEventListener(
                    "click",
                    (event) => {

                        document
                            .querySelectorAll(
                                ".filter-tab"
                            )
                            .forEach(
                                (item) => {
                                    item.classList.remove(
                                        "active"
                                    );
                                }
                            );


                        event
                            .currentTarget
                            .classList
                            .add("active");


                        state.activeCategory =
                            event
                                .currentTarget
                                .dataset
                                .category;


                        render();
                    }
                );
            }
        );


    notifListEl.addEventListener(
        "click",
        (event) => {

            const card =
                event.target.closest(
                    ".notif-card"
                );

            if (!card) {
                return;
            }


            const id =
                Number(
                    card.dataset.id
                );


            const notification =
                state.notifications.find(
                    (item) =>
                        item.id === id
                );

            if (notification?.category === "SETTLEMENT") {
                const settlementId =
                    notification.notificationType.startsWith("SETTLEMENT_DUE")
                        ? notification.secondaryReferenceId
                        : notification.referenceType === "SETTLEMENT"
                        ? notification.referenceId
                        : notification.secondaryReferenceId;

                if (settlementId) {
                    window.location.href = `/settlements/${settlementId}`;
                    return;
                }
            }


            if (
                notification?.ctaUrl
            ) {
                window.location.href =
                    notification.ctaUrl;
                return;
            }

            if (
                notification?.ctaAction
                === "MATCHING_REVIEW"
            ) {
                MatchingReviewModal.openTransaction({
                    bankTransactionId:
                        notification.referenceId,
                    linkedAccountId:
                        notification.secondaryReferenceId
                }).catch((error) => {
                    console.error(
                        "매칭 검토 모달 조회 실패",
                        error
                    );
                });
            }
        }
    );

    document.addEventListener(
        "matching-review:processed",
        fetchNotifications
    );


    fetchNotifications();
}
