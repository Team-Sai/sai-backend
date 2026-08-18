(function () {
    function escapeHtml(value) {
        return String(value ?? "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    const monthTitle = document.getElementById('monthTitle');
    const monthPickerPopup = document.getElementById('monthPickerPopup');
    const yearSelect = document.getElementById('yearSelect');
    const monthSelect = document.getElementById('monthSelect');
    const monthPickerGoBtn = document.getElementById('monthPickerGoBtn');
    const daysGrid = document.getElementById('daysGrid');
    const prevMonthBtn = document.getElementById('prevMonthBtn');
    const nextMonthBtn = document.getElementById('nextMonthBtn');
    const detailDate = document.getElementById('detailDate');
    const detailList = document.getElementById('detailList');
    const typeTabs = document.getElementById('typeTabs');

    const params = new URLSearchParams(location.search);
    const initialDate = params.get('date');

    let viewYear;
    let viewMonth;
    let selectedDate = initialDate || null;
    let currentItems = [];
    let currentFilter = 'ALL';

    if (initialDate) {
        const [y, m] = initialDate.split('-');
        viewYear = Number(y);
        viewMonth = Number(m) - 1;
    } else {
        const today = new Date();
        viewYear = today.getFullYear();
        viewMonth = today.getMonth();
    }

    function pad(n) {
        return String(n).padStart(2, '0');
    }

    function toYearMonth(year, month) {
        return `${year}-${pad(month + 1)}`;
    }

    function toDateString(year, month, day) {
        return `${year}-${pad(month + 1)}-${pad(day)}`;
    }

    function updateUrl(dateStr) {
        const url = new URL(location.href);
        url.searchParams.set('date', dateStr);
        history.pushState({ date: dateStr }, '', url);
    }

    function clearSelection() {
        selectedDate = null;
        detailDate.textContent = '날짜를 선택하세요';
        currentItems = [];
        detailList.innerHTML = '<p class="empty-state">달력에서 날짜를 선택하면 해당 날짜의 일정이 보여요.</p>';
    }

    async function loadMonth() {
        monthTitle.textContent = `${viewYear}년 ${viewMonth + 1}월`;
        daysGrid.innerHTML = '<p class="empty-state">달력을 불러오는 중이에요...</p>';

        const yearMonth = toYearMonth(viewYear, viewMonth);

        let res;
        try {
            res = await authFetch(`/api/integration/dashboard?yearMonth=${yearMonth}`, {
                method: "GET"
            });
        } catch (e) {
            daysGrid.innerHTML = '<p class="empty-state">달력을 불러오지 못했어요.</p>';
            return;
        }

        if (!res.ok) {
            daysGrid.innerHTML = '<p class="empty-state">달력을 불러오지 못했어요.</p>';
            return;
        }

        const data = await res.json();
        const markerMap = {};
        (data.calendarDays || []).forEach(day => {
            markerMap[day.date] = day;
        });

        renderGrid(markerMap);
    }

    function renderGrid(markerMap) {
        daysGrid.innerHTML = '';

        const firstDayOfMonth = new Date(viewYear, viewMonth, 1);
        const startWeekday = firstDayOfMonth.getDay();
        const totalDays = new Date(viewYear, viewMonth + 1, 0).getDate();

        for (let i = 0; i < startWeekday; i++) {
            const empty = document.createElement('div');
            empty.className = 'day-cell empty';
            daysGrid.appendChild(empty);
        }

        for (let day = 1; day <= totalDays; day++) {
            const dateStr = toDateString(viewYear, viewMonth, day);
            const marker = markerMap[dateStr];

            const cell = document.createElement('button');
            cell.type = 'button';
            cell.className = 'day-cell';
            cell.dataset.date = dateStr;
            cell.setAttribute('aria-label', `${viewMonth + 1}월 ${day}일`);
            if (dateStr === selectedDate) {
                cell.classList.add('selected');
            }

            const num = document.createElement('span');
            num.className = 'day-num';
            num.textContent = String(day);
            cell.appendChild(num);

            if (marker) {
                const dots = document.createElement('div');
                dots.className = 'day-dots';
                if (marker.hasInbound) {
                    const dot = document.createElement('span');
                    dot.className = 'dot dot-inbound';
                    dots.appendChild(dot);
                }
                if (marker.hasOutbound) {
                    const dot = document.createElement('span');
                    dot.className = 'dot dot-outbound';
                    dots.appendChild(dot);
                }
                cell.appendChild(dots);
            }

            cell.addEventListener('click', () => onDateClick(dateStr));
            daysGrid.appendChild(cell);
        }
    }

    async function onDateClick(dateStr) {
        selectedDate = dateStr;
        updateUrl(dateStr);
        await selectDate(dateStr);
    }

    async function selectDate(dateStr) {
        daysGrid.querySelectorAll('.day-cell.selected').forEach(el => el.classList.remove('selected'));
        const target = daysGrid.querySelector(`[data-date="${dateStr}"]`);
        if (target) {
            target.classList.add('selected');
        }
        await loadDayDetail(dateStr);
    }

    window.addEventListener('popstate', () => {
        const params = new URLSearchParams(location.search);
        const dateStr = params.get('date');

        if (!dateStr) {
            clearSelection();
            return;
        }

        const [y, m] = dateStr.split('-');
        const targetYear = Number(y);
        const targetMonth = Number(m) - 1;

        if (targetYear !== viewYear || targetMonth !== viewMonth) {
            viewYear = targetYear;
            viewMonth = targetMonth;
            selectedDate = dateStr;
            loadMonth().then(() => selectDate(dateStr));
        } else {
            selectedDate = dateStr;
            selectDate(dateStr);
        }
    });

    async function loadDayDetail(dateStr) {
        detailDate.textContent = dateStr.replaceAll('-', '. ') + '.';
        detailList.innerHTML = '<p class="empty-state">일정을 불러오는 중이에요...</p>';

        let res;
        try {
            res = await authFetch(`/api/dashboard/calendar/${dateStr}`, {
                method: "GET"
            });
        } catch (e) {
            currentItems = [];
            detailList.innerHTML = '<p class="empty-state">일정을 불러오지 못했어요. 다시 로그인해보세요.</p>';
            return;
        }

        if (!res.ok) {
            currentItems = [];
            detailList.innerHTML = '<p class="empty-state">일정을 불러오지 못했어요. 다시 로그인해보세요.</p>';
            return;
        }

        currentItems = await res.json();
        currentFilter = 'ALL';
        document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
        document.querySelector('.tab-btn[data-type="ALL"]').classList.add('active');

        renderDetailList();
    }

    function formatDate(dateStr) {
        return dateStr ? dateStr.replaceAll('-', '.') : '';
    }

    function renderDetailList() {
        const items = currentFilter === 'ALL'
            ? currentItems
            : currentItems.filter(item => item.type === currentFilter);

        if (items.length === 0) {
            detailList.innerHTML = '<p class="empty-state">이 날짜엔 일정이 없어요.</p>';
            return;
        }

        detailList.innerHTML = items.map((item, index) => {
            const badgeClass = item.type === 'LOAN' ? 'badge-loan' : 'badge-settlement';
            const badgeLabel = item.type === 'LOAN' ? '대여' : '정산';
            const amount = Number(item.amount).toLocaleString(undefined, { maximumFractionDigits: 0 });
            const isReceivable = item.subLabel === '수취예정' || item.subLabel === '받을 돈';
            const directionClass = isReceivable ? 'is-receivable' : 'is-payable';

            const metaParts = [];

            if (item.counterpartyName) metaParts.push(escapeHtml(item.counterpartyName));
            if (item.installmentInfo) metaParts.push(escapeHtml(item.installmentInfo));
            if (item.type === 'LOAN' && item.maturityDate) {
                metaParts.push(`만기 ${formatDate(item.maturityDate)}`);
            }
            if (item.type === 'SETTLEMENT' && item.categoryLabel) {
                metaParts.push(escapeHtml(item.categoryLabel));
            }
            const metaText = metaParts.join(' · ');

            const expandRows = [];
            if (item.type === 'LOAN') {
                if (item.principalAmount != null) {
                    expandRows.push(['원금', `${Number(item.principalAmount).toLocaleString()}원`]);
                }
                if (item.interestRate != null) {
                    expandRows.push(['이자율', `${item.interestRate}%`]);
                }
            } else {
                if (item.settlementTypeLabel) expandRows.push(['유형', escapeHtml(item.settlementTypeLabel)]);
                if (item.splitTypeLabel) expandRows.push(['정산방식', escapeHtml(item.splitTypeLabel)]);
                if (item.periodStartDate && item.periodEndDate) {
                    expandRows.push(['기간', `${formatDate(item.periodStartDate)} ~ ${formatDate(item.periodEndDate)}`]);
                }
            }

            const expandHtml = expandRows.length > 0
                ? expandRows.map(([label, value]) =>
                    `<div class="detail-expand-row"><dt>${label}</dt><dd>${value}</dd></div>`
                ).join('')
                : '<p class="empty-state">추가 정보가 없어요.</p>';

            return `
                <div class="detail-item ${directionClass}${item.overdue ? ' is-overdue' : ''}" data-index="${index}">
                    <button type="button" class="detail-item-header" aria-expanded="false">
                        <div class="detail-item-main">
                             <div class="detail-item-top">
                                 <span class="badge ${badgeClass}">${badgeLabel}</span>
                                 <span class="detail-title">${escapeHtml(item.title)}</span>
                                 ${item.overdue ? '<span class="badge badge-overdue">연체</span>' : ''}
                             </div>
                         <div class="detail-item-meta">
                            <span class="direction-label">${escapeHtml(item.subLabel)}</span>${metaText ? ' · ' + metaText : ''}
                         </div>
                        </div>
                        <span class="detail-amount">${amount}원</span>
                    </button>
                <dl class="detail-item-expand" hidden>
                    ${expandHtml}
                    <a class="detail-view-link" href="${escapeHtml(item.detailUrl)}">상세보기 →</a>
                </dl>
            </div>
        `;
        }).join('');
    }

    detailList.addEventListener('click', (e) => {
        const header = e.target.closest('.detail-item-header');
        if (!header) return;

        const item = header.closest('.detail-item');
        const expand = item.querySelector('.detail-item-expand');
        const isOpen = header.getAttribute('aria-expanded') === 'true';

        header.setAttribute('aria-expanded', String(!isOpen));
        expand.hidden = isOpen;
    });

    prevMonthBtn.addEventListener('click', () => {
        viewMonth -= 1;
        if (viewMonth < 0) {
            viewMonth = 11;
            viewYear -= 1;
        }
        clearSelection();
        loadMonth();
    });

    nextMonthBtn.addEventListener('click', () => {
        viewMonth += 1;
        if (viewMonth > 11) {
            viewMonth = 0;
            viewYear += 1;
        }
        clearSelection();
        loadMonth();
    });

    function openMonthPicker() {
        yearSelect.innerHTML = '';
        const rangeStart = viewYear - 5;
        const rangeEnd = viewYear + 5;
        for (let y = rangeStart; y <= rangeEnd; y++) {
            const opt = document.createElement('option');
            opt.value = String(y);
            opt.textContent = `${y}년`;
            if (y === viewYear) opt.selected = true;
            yearSelect.appendChild(opt);
        }
        monthSelect.value = String(viewMonth);
        monthPickerPopup.hidden = false;
    }

    function closeMonthPicker() {
        monthPickerPopup.hidden = true;
    }

    monthTitle.addEventListener('click', (e) => {
        e.stopPropagation();
        if (monthPickerPopup.hidden) {
            openMonthPicker();
        } else {
            closeMonthPicker();
        }
    });

    monthPickerPopup.addEventListener('click', (e) => e.stopPropagation());

    document.addEventListener('click', () => {
        if (!monthPickerPopup.hidden) closeMonthPicker();
    });

    monthPickerGoBtn.addEventListener('click', () => {
        viewYear = Number(yearSelect.value);
        viewMonth = Number(monthSelect.value);
        closeMonthPicker();
        clearSelection();
        loadMonth();
    });

    typeTabs.addEventListener('click', (e) => {
        const btn = e.target.closest('.tab-btn');
        if (!btn) return;
        currentFilter = btn.dataset.type;
        document.querySelectorAll('.tab-btn').forEach(el => el.classList.remove('active'));
        btn.classList.add('active');
        renderDetailList();
    });

    loadMonth().then(() => {
        if (initialDate) {
            selectDate(initialDate);
        }
    });
})();