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


    function authHeaders(extra = {}) {
        const token =
            sessionStorage.getItem("accessToken");

        return {
            ...(token
                ? {
                    Authorization:
                        `Bearer ${token}`
                }
                : {}),
            ...extra
        };
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
                    iconClass: "icon-blue",
                    accentClass: "accent-blue",
                    ctaLabel: "서명하러 가기",
                    ctaUrl:
                        `/contracts/${notification.referenceId}/approve`
                };


            case "SETTLEMENT_PARTICIPANT_ADDED":
                return {
                    category: "SETTLEMENT",
                    iconClass: "icon-green",
                    accentClass: "accent-green",
                    ctaLabel: "정산 보기",
                    ctaUrl:
                        `/settlements/${notification.referenceId}`
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

        return {
            id:
                notification.notificationId,

            notificationType:
                notification.notificationType,

            category:
                view.category,

            title:
                escapeHtml(
                    notification.title
                ),

            description:
                escapeHtml(
                    notification.content
                ),

            timeLabel:
                formatTimeLabel(
                    notification.createdAt
                ),

            ctaLabel:
                view.ctaLabel,

            ctaUrl:
                view.ctaUrl,

            iconClass:
                view.iconClass,

            accentClass:
                view.accentClass
        };
    }


    async function fetchNotifications() {

        const token =
            sessionStorage.getItem(
                "accessToken"
            );

        if (!token) {
            window.location.href =
                "/login?required=true";
            return;
        }

        try {

            const response =
                await fetch(
                    "/api/notifications",
                    {
                        method: "GET",

                        headers:
                            authHeaders({
                                Accept:
                                    "application/json"
                            }),

                        credentials:
                            "include"
                    }
                );


            if (response.status === 401) {

                sessionStorage.removeItem(
                    "accessToken"
                );

                window.location.href =
                    "/login?required=true";

                return;
            }


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

                                <div
                                    class="
                                        notif-icon
                                        ${notification.iconClass}
                                    "
                                >
                                    <svg
                                        width="18"
                                        height="18"
                                        viewBox="0 0 24 24"
                                        fill="none"
                                        aria-hidden="true"
                                    >
                                        <path
                                            d="M4 8h16M4 8l3-3M4 8l3 3"
                                            stroke="currentColor"
                                            stroke-width="1.6"
                                            stroke-linecap="round"
                                            stroke-linejoin="round"
                                        />
                                    </svg>
                                </div>


                                <div class="notif-body">

                                    <div class="notif-row">

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

                                    </div>


                                    <p
                                        class="notif-desc"
                                    >
                                        ${notification.description}
                                    </p>


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


            if (
                notification?.ctaUrl
            ) {
                window.location.href =
                    notification.ctaUrl;
            }
        }
    );


    fetchNotifications();
}
