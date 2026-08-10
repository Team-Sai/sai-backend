document.addEventListener('DOMContentLoaded', () => {
    initNotificationCenter();
});

function initNotificationCenter() {
    let state = {
        notifications: [],
        isDeleteMode: false,
        selectedIds: new Set(),
        activeCategory: 'ALL'
    };

    const btnSettings = document.getElementById('btnSettings');
    const settingsDropdown = document.getElementById('settingsDropdown');
    const btnEnterDeleteMode = document.getElementById('btnEnterDeleteMode');
    const notifListEl = document.getElementById('notifList');
    const normalFooter = document.getElementById('normalFooter');
    const deleteFooter = document.getElementById('deleteFooter');
    const btnSelectAll = document.getElementById('btnSelectAll');
    const btnDeleteSelected = document.getElementById('btnDeleteSelected');
    const selectedCountEl = document.getElementById('selectedCount');

    function authHeaders(extra) {
        const token = sessionStorage.getItem('accessToken');
        return Object.assign(
            token ? { Authorization: `Bearer ${token}` } : {},
            extra || {}
        );
    }

    async function fetchNotifications() {
        try {
            const response = await fetch('/api/contracts/incoming', {
                method: 'GET',
                headers: authHeaders({ Accept: 'application/json' }),
            });
            if (!response.ok) throw new Error('불러오기 실패');

            const contracts = await response.json();


            state.notifications = contracts.map(c => ({
                id: c.contractId,
                category: 'SIGN',
                type: 'contract_sent_to_debtor',
                title: '서명 요청 알림',
                description: `${c.creditorName || '채권자'}님과의 차용증 계약서에 서명이 필요합니다. 지금 확인하고 진행해 주세요.`,
                time_label: '방금 전',
                is_read: false,
                cta_label: '서명하러 가기',
                cta_url: `/contracts/${c.contractId}/approve`
            }));
            render();
        } catch (e) {
            console.error(e);
            notifListEl.innerHTML = `<li style="text-align:center; padding:30px; color:var(--muted);">알림을 불러올 수 없습니다.</li>`;
        }
    }


    function render() {

        const filteredList = state.notifications.filter(n => {
            if (state.activeCategory === 'ALL') return true;
            return n.category === state.activeCategory;
        });

        if (filteredList.length === 0) {
            notifListEl.innerHTML = `<li style="text-align:center; padding:40px; color:var(--muted);">해당 알림이 없습니다.</li>`;
            return;
        }

        notifListEl.innerHTML = filteredList.map(n => {
            const isSelected = state.selectedIds.has(n.id);
            const isRead = n.is_read;
            const unreadClass = isRead ? '' : 'unread';
            const accentClass = isRead ? '' : (n.category === 'SIGN' ? 'accent-blue' : 'accent-green');
            const selectedClass = isSelected ? 'selected' : '';
            const dot = isRead ? '' : '<i class="dot"></i>';

            const radioColHtml = `
        <div class="notif-radio-col ${state.isDeleteMode ? '' : 'hidden'}">
          <div class="custom-radio"></div>
        </div>`;

            const ctaHtml = (n.cta_label && !state.isDeleteMode)
                ? `<button class="notif-cta">${n.cta_label}</button>`
                : '';

            const iconClass = n.category === 'SIGN' ? 'icon-blue' : (n.category === 'PAYMENT' ? 'icon-green' : 'icon-gray');

            return `
        <li class="notif-card ${unreadClass} ${accentClass} ${selectedClass}" data-id="${n.id}">
          ${radioColHtml}
          <div class="notif-icon ${iconClass}">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M4 8h16M4 8l3-3M4 8l3 3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </div>
          <div class="notif-body">
            <div class="notif-row">
              <span class="notif-title">${n.title}</span>
              <span class="notif-time">${n.time_label} ${dot}</span>
            </div>
            <p class="notif-desc">${n.description}</p>
            ${ctaHtml}
          </div>
        </li>`;
        }).join('');


        if (state.isDeleteMode) {
            normalFooter.classList.add('hidden');
            deleteFooter.classList.remove('hidden');
            selectedCountEl.textContent = state.selectedIds.size;
        } else {
            normalFooter.classList.remove('hidden');
            deleteFooter.classList.add('hidden');
        }
    }


    btnSettings.addEventListener('click', (e) => {
        e.stopPropagation();
        settingsDropdown.classList.toggle('hidden');
    });

    document.addEventListener('click', () => {
        settingsDropdown.classList.add('hidden');
    });


    btnEnterDeleteMode.addEventListener('click', () => {
        state.isDeleteMode = true;
        state.selectedIds.clear();
        render();
    });


    document.querySelectorAll('.filter-tab').forEach(tab => {
        tab.addEventListener('click', (e) => {
            document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
            e.target.classList.add('active');
            state.activeCategory = e.target.dataset.category;
            render();
        });
    });


    notifListEl.addEventListener('click', (e) => {
        const card = e.target.closest('.notif-card');
        if (!card) return;

        const id = Number(card.dataset.id);

        if (state.isDeleteMode) {
            if (state.selectedIds.has(id)) {
                state.selectedIds.delete(id);
            } else {
                state.selectedIds.add(id);
            }
            render();
            return;
        }

        const notification = state.notifications.find((n) => n.id === id);
        if (notification?.cta_url) {
            location.href = notification.cta_url;
        }
    });


    btnSelectAll.addEventListener('click', () => {
        const visibleIds = state.notifications
            .filter(n => state.activeCategory === 'ALL' || n.category === state.activeCategory)
            .map(n => n.id);

        if (state.selectedIds.size === visibleIds.length) {
            state.selectedIds.clear();
        } else {
            visibleIds.forEach(id => state.selectedIds.add(id));
        }
        render();
    });


    btnDeleteSelected.addEventListener('click', () => {
        if (state.selectedIds.size === 0) {
            alert('삭제할 알림을 선택해 주세요.');
            return;
        }

        if (confirm(`선택한 ${state.selectedIds.size}개의 알림을 삭제하시겠습니까?`)) {
            state.notifications = state.notifications.filter(n => !state.selectedIds.has(n.id));
            state.selectedIds.clear();
            state.isDeleteMode = false;
            render();
        }
    });

    fetchNotifications();
}