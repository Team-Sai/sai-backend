(function () {
    "use strict";

    const ROLE_LABELS = { CREDITOR: "채권자", DEBTOR: "채무자" };
    const STATUS_LABELS = { ONGOING: "진행중", COMPLETED: "완료" };
    const REPAYMENT_TYPE_LABELS = {
        EQUAL_PRINCIPAL_AND_INTEREST: "원리금균등상환",
        EQUAL_PRINCIPAL: "원금균등상환",
        BULLET_REPAYMENT: "만기일시상환"
    };

    let currentPage = 1;

    function authHeaders(extra) {
        const token = sessionStorage.getItem("accessToken");
        return Object.assign(
            token ? { Authorization: `Bearer ${token}` } : {},
            extra || {}
        );
    }

    function escapeHtml(value) {
        if (value == null) return "";
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;");
    }

    function renderList(contracts) {
        const listEl = document.getElementById("archiveList");

        if (!contracts || contracts.length === 0) {
            listEl.innerHTML = `<div class="empty-state">보관된 차용증이 없습니다.</div>`;
            return;
        }

        listEl.innerHTML = contracts.map((c) => {
            const roleClass = c.role === "CREDITOR" ? "creditor" : "debtor";
            const statusClass = c.contractStatus === "ONGOING" ? "ongoing" : "completed";

            return `
                <div class="archive-card" data-contract-id="${c.contractId}">
                    <div class="archive-card-main">
                        <div class="archive-card-title">
                            <span class="role-badge ${roleClass}">${ROLE_LABELS[c.role] || c.role}</span>
                            ${escapeHtml(c.contractAlias)}
                        </div>
                        <div class="archive-card-sub">
                            원금 ${Number(c.principalAmount).toLocaleString()}원 · 만기 ${c.maturityDate}
                        </div>
                    </div>
                    <div class="card-actions">
                        <span class="status-pill ${statusClass}">${STATUS_LABELS[c.contractStatus] || c.contractStatus}</span>
                        <button type="button" class="btn-pdf-download" data-contract-id="${c.contractId}">PDF 다운로드</button>
                    </div>
                </div>
            `;
        }).join("");

        listEl.querySelectorAll(".archive-card").forEach((card) => {
            card.addEventListener("click", () => {
                const contractId = card.dataset.contractId;
                window.location.href = `/contracts/${contractId}/contract-detail`;
            });
        });

        listEl.querySelectorAll(".btn-pdf-download").forEach((btn) => {
            btn.addEventListener("click", (event) => {
                event.stopPropagation();
                downloadContractPdf(btn.dataset.contractId);
            });
        });
    }

    function buildContractDocument(c) {
        const printArea = document.getElementById("printArea");

        printArea.innerHTML = `
            <div class="page">
            <div class="doc">
                <h1 class="doc__title">금 전 차 용 계 약 서</h1>

                <section class="doc__article">
                    <span class="doc__clause">제1조(당사자)</span>
                    <div class="doc__body">
                        <span class="doc__text">
                            채권자 <strong class="doc__readonly">${escapeHtml(c.creditorName)}</strong>(이하 "갑"이라고 함)는
                        </span>
                        <span class="doc__text doc__text--indent">
                            금 <strong class="doc__readonly">${Number(c.principalAmount).toLocaleString()}</strong>
                            원을 채무자 <strong class="doc__readonly">${escapeHtml(c.debtorName) || "-"}</strong>(이하 "을"이라고 함)에게 대여하고 을은 이를 차용한다.
                        </span>
                    </div>
                </section>

                <section class="doc__article">
                    <span class="doc__clause">제2조(대출기간)</span>
                    <div class="doc__body">
                        <span class="doc__text">
                            대출 시작일은 <strong class="doc__readonly">${c.startDate}</strong>로 한다.
                        </span>
                        <span class="doc__text doc__text--indent">
                            차용금의 변제기한(만기일)은 <strong class="doc__readonly">${c.maturityDate}</strong>로 한다.
                        </span>
                    </div>
                </section>

                <section class="doc__article">
                    <span class="doc__clause">제3조(이자)</span>
                    <span class="doc__text">
                        이자는 연 <strong class="doc__readonly">${c.interestRate}</strong>%의 비율로 하며, 20%를 초과할 수 없다.
                    </span>
                </section>

                <section class="doc__article">
                    <span class="doc__clause">제4조(변제방법)</span>
                    <div class="doc__body">
                        <span class="doc__text">
                            채무의 변제는 갑의 주소 또는 갑이 지정하는 장소에 지참 또는 송금해서 지불하며,
                        </span>
                        <span class="doc__text doc__text--indent">
                            매월 <strong class="doc__readonly">${c.repaymentDay}</strong>일에 지급하기로 한다.
                        </span>
                    </div>
                </section>

                <section class="doc__article">
                    <span class="doc__clause">제5조(상환방식)</span>
                    <span class="doc__text">
                        <strong class="doc__readonly">${REPAYMENT_TYPE_LABELS[c.repaymentType] || c.repaymentType}</strong>
                    </span>
                </section>

                <section class="doc__article">
                    <span class="doc__clause">제6조(계약의 목적)</span>
                    <span class="doc__text">
                        <strong class="doc__readonly">${escapeHtml(c.contractAlias)}</strong>
                    </span>
                </section>

                <section class="doc__article">
                    <span class="doc__clause">제7조(특약사항)</span>
                    <span class="doc__text">
                        <strong class="doc__readonly">${escapeHtml(c.terms) || "-"}</strong>
                    </span>
                </section>

                <p class="doc__closing">
                    갑과 을은 상기 계약을 증명하기 위하여 본 계약서 2통을 작성하고, 각자 서명 날인한 후 1통씩을 보관한다.
                </p>

                <table class="parties">
                    <colgroup>
                        <col style="width: 12%">
                        <col style="width: 12%">
                        <col style="width: 20%">
                        <col style="width: 14%">
                        <col style="width: 20%">
                        <col style="width: 10%">
                        <col style="width: 32%">
                    </colgroup>
                    <tbody>
                    <tr>
                        <th class="parties__role">채권자</th>
                        <td class="parties__label">성 명</td>
                        <td class="parties__value"><strong class="doc__readonly">${escapeHtml(c.creditorName)}</strong></td>
                        <td class="parties__label">생년월일</td>
                        <td class="parties__value"><strong class="doc__readonly">${escapeHtml(c.creditorBirthDate)}</strong></td>
                        <td class="parties__label">주 소</td>
                        <td class="parties__value"><strong class="doc__readonly">${escapeHtml(c.creditorAddress) || "-"}</strong></td>
                    </tr>
                    <tr>
                        <th class="parties__role">채무자</th>
                        <td class="parties__label">성 명</td>
                        <td class="parties__value"><strong class="doc__readonly">${escapeHtml(c.debtorName) || "-"}</strong></td>
                        <td class="parties__label">생년월일</td>
                        <td class="parties__value"><strong class="doc__readonly">${escapeHtml(c.debtorBirthDate) || "-"}</strong></td>
                        <td class="parties__label">주 소</td>
                        <td class="parties__value"><strong class="doc__readonly">${escapeHtml(c.debtorAddress) || "-"}</strong></td>
                    </tr>
                    </tbody>
                </table>
            </div>
            </div>
        `;
    }

    async function downloadContractPdf(contractId) {
        const printArea = document.getElementById("printArea");

        try {

            const response = await fetch(
                `/api/contracts/${contractId}/contract-detail`,
                { headers: authHeaders({ Accept: "application/json" }) }
            );

            if (!response.ok) {
                throw new Error("차용증 정보를 불러오지 못했습니다.");
            }

            const data = await response.json();
            const c = data.contract;


            buildContractDocument(c);

            printArea.style.position = "absolute";
            printArea.style.left = "0";
            printArea.style.top = "0";
            printArea.style.zIndex = "-9999";
            printArea.style.display = "block";

            const pageEl = printArea.querySelector(".page");

            const canvas = await html2canvas(pageEl, {
                scale: 2,
                useCORS: true,
                logging: false,
                width: pageEl.offsetWidth,
                height: pageEl.offsetHeight,
                windowWidth: pageEl.scrollWidth,
                windowHeight: pageEl.scrollHeight
            });

            printArea.style.position = "fixed";
            printArea.style.left = "-10000px";

            const imgData = canvas.toDataURL("image/jpeg", 0.98);
            const { jsPDF } = window.jspdf;
            const doc = new jsPDF("p", "mm", "a4");

            // .page는 210mm x 297mm(A4)로 고정된 요소이므로,
            // 캡처된 이미지를 A4 페이지 규격에 그대로 꽉 채운다.
            const pdfWidth = doc.internal.pageSize.getWidth();   // 210
            const pdfHeight = doc.internal.pageSize.getHeight(); // 297

            doc.addImage(imgData, "JPEG", 0, 0, pdfWidth, pdfHeight);

            const fileName = `${c.contractAlias || "차용증"}_${contractId}.pdf`;
            doc.save(fileName);

        } catch (error) {
            console.error("[Archive] PDF 다운로드 실패", error);

            
            if (printArea) {
                printArea.style.position = "fixed";
                printArea.style.left = "-10000px";
            }
            alert("차용증 PDF 생성 중 오류가 발생했습니다.");
        }
    }

    function renderPager(data) {
        const pager = document.getElementById("pager");
        const pageLabel = document.getElementById("pageLabel");
        const prevBtn = document.getElementById("prevPageBtn");
        const nextBtn = document.getElementById("nextPageBtn");

        if (!data.totalPages || data.totalPages <= 1) {
            pager.style.display = "none";
            return;
        }

        pager.style.display = "flex";
        pageLabel.textContent = `${data.currentPage} / ${data.totalPages}`;
        prevBtn.disabled = data.currentPage <= 1;
        nextBtn.disabled = data.currentPage >= data.totalPages;
    }

    async function loadArchive() {
        const token = sessionStorage.getItem("accessToken");
        if (!token) {
            window.location.href = "/login?required=true";
            return;
        }

        const listEl = document.getElementById("archiveList");

        try {
            const response = await fetch(
                `/api/dashboard?roleFilter=ALL&page=${currentPage}`,
                { headers: authHeaders({ Accept: "application/json" }) }
            );

            if (response.status === 401) {
                sessionStorage.removeItem("accessToken");
                window.location.href = "/login?required=true";
                return;
            }

            if (!response.ok) {
                throw new Error("보관함 조회 실패");
            }

            const data = await response.json();

            renderList(data.contracts);
            renderPager(data);

        } catch (error) {
            console.error("[Archive] 조회 실패", error);
            listEl.innerHTML = `<div class="error-state">보관함을 불러올 수 없습니다.</div>`;
        }
    }

    document.getElementById("prevPageBtn").addEventListener("click", () => {
        currentPage = Math.max(1, currentPage - 1);
        loadArchive();
    });

    document.getElementById("nextPageBtn").addEventListener("click", () => {
        currentPage += 1;
        loadArchive();
    });

    loadArchive();
})();
