(function () {
    "use strict";

    const PAGE_SIZE = 20;
    const LOADING_MESSAGE_DELAY_MS = 300;
    const state = {
        options: null,
        page: 0,
        totalCount: 0,
        transactions: [],
        selectedCandidateIds: new Map(),
        resultStates: new Map(),
        pendingRejectTransactionId: null,
        lastFocusedElement: null,
        hasServerChanges: false,
        hasLoaded: false,
        loading: false
    };

    let elements;
    let loadingTimer = null;

    function initialize() {
        if (elements) {
            return true;
        }

        const overlay = document.getElementById("matching-review-overlay");
        if (!overlay) {
            return false;
        }

        elements = {
            overlay,
            title: document.getElementById("matching-review-title"),
            progress: document.getElementById("matching-review-progress"),
            message: document.getElementById("matching-review-message"),
            list: document.getElementById("matching-review-list"),
            footer: document.getElementById("matching-review-footer"),
            moreButton: document.getElementById("matching-review-more"),
            closeButton: document.getElementById("matching-review-close"),
            confirmOverlay: document.getElementById("matching-review-confirm-overlay"),
            confirmCancel: document.getElementById("matching-review-confirm-cancel"),
            confirmSubmit: document.getElementById("matching-review-confirm-submit")
        };

        elements.closeButton.addEventListener("click", close);
        elements.moreButton.addEventListener("click", () => loadPage(true));
        elements.confirmCancel.addEventListener("click", closeRejectConfirm);
        elements.confirmSubmit.addEventListener("click", rejectPendingTransaction);
        elements.list.addEventListener("change", handleCandidateSelection);
        elements.list.addEventListener("click", handleCardAction);
        document.addEventListener("keydown", handleEscape);
        return true;
    }

    async function open(options) {
        if (!initialize()) {
            throw new Error("매칭 검토 모달을 찾을 수 없습니다.");
        }

        state.options = {
            reviewChannel: options.reviewChannel,
            targetType: options.targetType || null,
            aggregateId: options.aggregateId || null,
            transaction: null
        };
        resetState();
        await loadPage(false);
        showModal();
    }

    async function openTransaction(options) {
        if (!initialize()) {
            throw new Error("매칭 검토 모달을 찾을 수 없습니다.");
        }

        state.options = {
            reviewChannel: "NOTIFICATION",
            targetType: null,
            aggregateId: null,
            transaction: {
                linkedAccountId: options.linkedAccountId,
                bankTransactionId: options.bankTransactionId
            }
        };
        resetState();
        await loadTransaction();
        showModal();
    }

    function resetState() {
        clearLoadingTimer();
        state.page = 0;
        state.totalCount = 0;
        state.transactions = [];
        state.selectedCandidateIds.clear();
        state.resultStates.clear();
        state.pendingRejectTransactionId = null;
        state.hasServerChanges = false;
        state.hasLoaded = false;
        hideMessage();
    }

    function showModal() {
        state.lastFocusedElement = document.activeElement;
        elements.overlay.hidden = false;
        document.body.classList.add("matching-review-scroll-locked");
        elements.closeButton.focus();
    }

    function close() {
        if (!elements) {
            return;
        }
        clearLoadingTimer();
        elements.overlay.hidden = true;
        elements.confirmOverlay.hidden = true;
        document.body.classList.remove("matching-review-scroll-locked");
        state.lastFocusedElement?.focus?.();
        if (state.hasServerChanges) {
            state.hasServerChanges = false;
            document.dispatchEvent(new CustomEvent("matching-review:closed"));
        }
    }

    async function loadPage(append) {
        if (state.loading) {
            return;
        }

        state.loading = true;
        elements.moreButton.disabled = true;
        if (!append) {
            scheduleLoadingMessage();
        }

        try {
            const query = new URLSearchParams({
                reviewChannel: state.options.reviewChannel,
                page: String(state.page),
                size: String(PAGE_SIZE)
            });
            if (state.options.targetType) {
                query.set("targetType", state.options.targetType);
            }
            if (state.options.aggregateId) {
                query.set("aggregateId", String(state.options.aggregateId));
            }

            const response = await authFetch(`/api/matching-reviews?${query}`);
            const body = await readJsonSafely(response);
            if (!response.ok) {
                throw new Error(body?.message || "확인 필요 거래를 불러오지 못했습니다.");
            }

            state.transactions = append
                ? state.transactions.concat(body.content || [])
                : body.content || [];
            state.totalCount = Number(body.totalCount || 0);
            state.page = Number(body.page || 0) + 1;
            state.hasLoaded = true;
            render();
        } catch (error) {
            console.error("매칭 검토 목록 조회 실패", error);
            renderError(error.message);
        } finally {
            state.loading = false;
            elements.moreButton.disabled = false;
        }
    }

    async function loadTransaction() {
        const { linkedAccountId, bankTransactionId } = state.options.transaction;
        scheduleLoadingMessage();
        try {
            const response = await authFetch(
                `/api/linked-accounts/${linkedAccountId}/transactions/${bankTransactionId}/match-candidates`
            );
            const body = await readJsonSafely(response);
            if (!response.ok) {
                throw new Error(body?.message || "매칭 후보를 불러오지 못했습니다.");
            }
            state.transactions = [body];
            state.totalCount = 1;
            state.hasLoaded = true;
            render();
        } catch (error) {
            console.error("매칭 검토 조회 실패", error);
            renderError(error.message);
        }
    }

    function renderLoading() {
        clearLoadingTimer();
        elements.list.innerHTML = '<div class="matching-review-loading">확인 필요 거래를 불러오는 중입니다.</div>';
        elements.footer.hidden = true;
    }

    function scheduleLoadingMessage() {
        clearLoadingTimer();
        loadingTimer = window.setTimeout(() => {
            loadingTimer = null;
            if (state.loading && !state.hasLoaded) {
                renderLoading();
            }
        }, LOADING_MESSAGE_DELAY_MS);
    }

    function clearLoadingTimer() {
        if (loadingTimer !== null) {
            window.clearTimeout(loadingTimer);
            loadingTimer = null;
        }
    }

    function renderError(message) {
        clearLoadingTimer();
        elements.list.innerHTML = `<div class="matching-review-empty">${escapeHtml(message)}</div>`;
        elements.footer.hidden = true;
    }

    function render() {
        clearLoadingTimer();
        if (!state.hasLoaded) {
            return;
        }

        const deferredCount = Array.from(state.resultStates.values())
                .filter(result => result.type === "DEFERRED")
                .length;
        const completedCount = state.resultStates.size - deferredCount;
        elements.title.textContent = state.options.reviewChannel === "NOTIFICATION"
            ? "정산·차용증 매칭 선택"
            : `확인이 필요한 거래 ${state.totalCount}건`;
        elements.progress.textContent = `${completedCount}건 처리 · ${Math.max(state.totalCount - completedCount, 0)}건 남음`;

        if (state.transactions.length === 0) {
            elements.list.innerHTML = '<div class="matching-review-empty">현재 화면에서 확인할 거래가 없습니다.</div>';
            elements.footer.hidden = true;
            return;
        }

        elements.list.innerHTML = state.transactions
                .map(renderTransaction)
                .join("");
        elements.footer.hidden = state.transactions.length >= state.totalCount;
    }

    function renderTransaction(review) {
        const transaction = review.transaction;
        const result = state.resultStates.get(transaction.bankTransactionId);
        if (result) {
            return renderResult(transaction, result);
        }

        const selectedId = state.selectedCandidateIds.get(transaction.bankTransactionId);
        const multipleCandidates = review.candidates.length > 1;
        const candidateHtml = review.candidates
                .map(candidate => renderCandidate(transaction, candidate, selectedId))
                .join("");

        return `
            <article class="matching-review-card" data-transaction-id="${transaction.bankTransactionId}" data-linked-account-id="${transaction.linkedAccountId}">
                <header class="matching-review-card__header">
                    <div class="matching-review-card__amount">${formatMoney(transaction.amount)} 입금</div>
                    <div class="matching-review-card__meta">${escapeHtml(transaction.counterpartyName || "입금자 미상")} · ${formatDateTime(transaction.transactionAt)}${transaction.memo ? ` · ${escapeHtml(transaction.memo)}` : ""}</div>
                    <div class="matching-review-card__badges">
                        <span class="matching-review-badge matching-review-badge--warning">확인 필요</span>
                        ${multipleCandidates ? '<span class="matching-review-badge">복수 후보</span>' : ""}
                    </div>
                </header>
                <div class="matching-review-card__content">
                    <p class="matching-review-card__instruction">다음 중 반영할 대상을 하나 선택해 주세요.</p>
                    <div class="matching-review-candidates">${candidateHtml}</div>
                    <footer class="matching-review-card__actions">
                        <button type="button" class="matching-review-button matching-review-button--ghost" data-action="later">나중에 하기</button>
                        <button type="button" class="matching-review-button matching-review-button--secondary" data-action="reject">어느 후보도 아님</button>
                        <button type="button" class="matching-review-button matching-review-button--primary" data-action="apply" ${selectedId ? "" : "disabled"}>선택하여 반영</button>
                    </footer>
                </div>
            </article>`;
    }

    function renderCandidate(transaction, candidate, selectedId) {
        const selected = Number(selectedId) === Number(candidate.matchCandidateId);
        const difference = Number(transaction.amount) - Number(candidate.expectedRemainingAmount);
        const differenceClass = difference < 0 ? " is-short" : "";
        const domainClass = candidate.targetType === "LOAN" ? "loan" : "settlement";
        const domainLabel = candidate.targetType === "LOAN" ? "차용증" : "정산";

        return `
            <label class="matching-review-candidate${selected ? " is-selected" : ""}">
                <input type="radio" name="matching-candidate-${transaction.bankTransactionId}" value="${candidate.matchCandidateId}" ${selected ? "checked" : ""}>
                <span class="matching-review-candidate__main">
                    <strong>${escapeHtml(candidate.targetName || `${domainLabel} #${candidate.aggregateId}`)}</strong>
                    <small><span class="matching-review-badge matching-review-badge--${domainClass}">${domainLabel}</span> 참여자 ${escapeHtml(candidate.participantName || "-")}</small>
                </span>
                <span class="matching-review-candidate__money">
                    <small>예정 잔여금액</small>
                    <strong>${formatMoney(candidate.expectedRemainingAmount)}</strong>
                </span>
                <span class="matching-review-candidate__difference${differenceClass}">
                    <small>${amountTypeLabel(candidate.amountMatchType)}</small>
                    <span>${differenceText(difference)}</span>
                </span>
            </label>`;
    }

    function renderResult(transaction, result) {
        const unmatched = result.type === "UNMATCHED";
        return `
            <article class="matching-review-card matching-review-card--result${unmatched ? " is-unmatched" : ""}">
                <div class="matching-review-result-icon">${unmatched ? "–" : "✓"}</div>
                <div>
                    <h3>${unmatched ? "미매칭 처리 완료" : result.type === "DEFERRED" ? "나중에 확인" : "처리 완료"}</h3>
                    <p>${escapeHtml(result.message)}</p>
                </div>
            </article>`;
    }

    function handleCandidateSelection(event) {
        const input = event.target.closest('input[type="radio"]');
        if (!input) {
            return;
        }
        const card = input.closest(".matching-review-card");
        state.selectedCandidateIds.set(
            Number(card.dataset.transactionId),
            Number(input.value)
        );
        render();
    }

    function handleCardAction(event) {
        const button = event.target.closest("[data-action]");
        if (!button) {
            return;
        }
        const card = button.closest(".matching-review-card");
        const transactionId = Number(card.dataset.transactionId);
        const linkedAccountId = Number(card.dataset.linkedAccountId);

        if (button.dataset.action === "apply") {
            applyCandidate(transactionId, linkedAccountId, button);
        } else if (button.dataset.action === "reject") {
            state.pendingRejectTransactionId = transactionId;
            elements.confirmOverlay.hidden = false;
            elements.confirmCancel.focus();
        } else if (button.dataset.action === "later") {
            state.resultStates.set(transactionId, {
                type: "DEFERRED",
                message: "거래는 확인 필요 상태로 유지되며 나중에 다시 표시됩니다."
            });
            render();
        }
    }

    async function applyCandidate(transactionId, linkedAccountId, button) {
        const matchCandidateId = state.selectedCandidateIds.get(transactionId);
        if (!matchCandidateId) {
            return;
        }
        button.disabled = true;

        try {
            const response = await authFetch(
                `/api/linked-accounts/${linkedAccountId}/transactions/${transactionId}/matching-review/apply`,
                {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ matchCandidateId })
                }
            );
            const body = await readJsonSafely(response);
            if (!response.ok) {
                throw new Error(body?.message || "선택한 후보를 반영하지 못했습니다.");
            }

            if (body.reviewResult === "CANDIDATE_INVALIDATED") {
                showMessage("선택한 후보가 더 이상 유효하지 않습니다. 최신 후보를 다시 불러왔습니다.", true);
                await reloadCurrentView();
                return;
            }

            if (state.options.transaction) {
                state.transactions = [];
                state.totalCount = 0;
                state.hasLoaded = true;
                render();
            } else {
                await reloadCurrentView();
            }
            notifyProcessed(transactionId, body);
        } catch (error) {
            console.error("매칭 후보 반영 실패", error);
            showMessage(error.message, true);
            button.disabled = false;
        }
    }

    async function rejectPendingTransaction() {
        const transactionId = state.pendingRejectTransactionId;
        const review = state.transactions.find(item => item.transaction.bankTransactionId === transactionId);
        if (!review) {
            closeRejectConfirm();
            return;
        }

        elements.confirmSubmit.disabled = true;
        try {
            const response = await authFetch(
                `/api/linked-accounts/${review.transaction.linkedAccountId}/transactions/${transactionId}/matching-review/reject`,
                { method: "POST" }
            );
            const body = await readJsonSafely(response);
            if (!response.ok) {
                throw new Error(body?.message || "미매칭 처리하지 못했습니다.");
            }
            state.resultStates.set(transactionId, {
                type: "UNMATCHED",
                message: "이 거래는 어느 후보에도 반영되지 않았습니다."
            });
            closeRejectConfirm();
            render();
            notifyProcessed(transactionId, body);
        } catch (error) {
            console.error("미매칭 처리 실패", error);
            showMessage(error.message, true);
        } finally {
            elements.confirmSubmit.disabled = false;
        }
    }

    async function reloadCurrentView() {
        state.selectedCandidateIds.clear();
        state.resultStates.clear();
        if (state.options.transaction) {
            await loadTransaction();
        } else {
            state.page = 0;
            await loadPage(false);
        }
    }

    function closeRejectConfirm() {
        state.pendingRejectTransactionId = null;
        elements.confirmOverlay.hidden = true;
    }

    function notifyProcessed(transactionId, response) {
        state.hasServerChanges = true;
        document.dispatchEvent(new CustomEvent("matching-review:processed", {
            detail: { bankTransactionId: transactionId, response }
        }));
    }

    function showMessage(message, error) {
        elements.message.textContent = message;
        elements.message.classList.toggle("is-error", Boolean(error));
        elements.message.hidden = false;
    }

    function hideMessage() {
        if (!elements) {
            return;
        }
        elements.message.hidden = true;
        elements.message.classList.remove("is-error");
        elements.message.textContent = "";
    }

    function handleEscape(event) {
        if (event.key !== "Escape" || !elements) {
            return;
        }
        if (!elements.confirmOverlay.hidden) {
            closeRejectConfirm();
        } else if (!elements.overlay.hidden) {
            close();
        }
    }

    async function readJsonSafely(response) {
        const text = await response.text();
        if (!text) {
            return null;
        }
        try {
            return JSON.parse(text);
        } catch {
            return null;
        }
    }

    function formatMoney(value) {
        return `${new Intl.NumberFormat("ko-KR").format(Number(value || 0))}원`;
    }

    function formatDateTime(value) {
        if (!value) {
            return "거래일시 미상";
        }
        return new Intl.DateTimeFormat("ko-KR", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit"
        }).format(new Date(value));
    }

    function amountTypeLabel(type) {
        return ({ EXACT: "정확 일치", PARTIAL: "부분입금", EXCESS: "초과입금" })[type] || "금액 확인";
    }

    function differenceText(difference) {
        if (difference === 0) {
            return "정확히 일치";
        }
        return difference > 0
            ? `${formatMoney(difference)} 초과`
            : `${formatMoney(Math.abs(difference))} 부족`;
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    window.MatchingReviewModal = { open, openTransaction, close };
})();
