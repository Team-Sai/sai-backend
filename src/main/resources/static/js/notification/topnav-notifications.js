(function () {
  "use strict";

  function authHeaders(extra) {
    const token = sessionStorage.getItem("accessToken");
    return Object.assign(
      token ? { Authorization: `Bearer ${token}` } : {},
      extra || {}
    );
  }

  // Each source resolves to a count of pending items for the current user.
  // The bell badge lights up red if any source has a pending count > 0.
  // To make the bell react to a new kind of request (e.g. 차용증 변경 요청),
  // add another entry here once its backend list endpoint exists.
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

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initTopnavBellBadge);
  } else {
    initTopnavBellBadge();
  }
})();
