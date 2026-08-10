(function () {
  "use strict";

  function authHeaders(extra) {
    const token = sessionStorage.getItem("accessToken");
    return Object.assign(
      token ? { Authorization: `Bearer ${token}` } : {},
      extra || {}
    );
  }

  const NOTIFICATION_SOURCES = [
    {
      key: "contract-signature",
      async fetchCount() {
        const response = await fetch("/api/contracts/incoming", {
          method: "GET",
          headers: authHeaders({ Accept: "application/json" }),
        });
        if (!response.ok) throw new Error("failed to load incoming contracts");
        const contracts = await response.json();
        return contracts.length;
      },
    },
  ];

  function initTopnavBellClick() {
    const bell = document.querySelector(".topnav__bell");
    if (!bell) return;

    bell.addEventListener("click", () => {
      location.href = "/notifications";
    });
  }

  async function initTopnavBellBadge() {
    const badge = document.getElementById("topnavBellBadge");
    if (!badge) return;

    const results = await Promise.allSettled(
      NOTIFICATION_SOURCES.map((source) => source.fetchCount())
    );

    const total = results
      .filter((r) => r.status === "fulfilled")
      .reduce((sum, r) => sum + r.value, 0);

    badge.hidden = total === 0;
  }

  function init() {
    initTopnavBellClick();
    initTopnavBellBadge();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
