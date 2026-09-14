const events = {
  3: { label: "월세 납부일", type: "red" },
  9: { label: "가족 정산", type: "blue" },
  17: { label: "보증금 상환일", type: "red" },
  19: { label: "부모님 간병비 정산", type: "orange" },
  25: { label: "여행 회비 정산", type: "blue" }
};

window.addEventListener("DOMContentLoaded", () => {
  requestAnimationFrame(() => {
    document.querySelector(".hero")?.classList.add("is-open");
  });

  setupScrollTyping();
});

function setupScrollTyping() {
  const targets = document.querySelectorAll(
      ".split-section .section-copy > .eyebrow, " +
      ".split-section .section-copy > h2, " +
      ".split-section .section-copy > .description, " +
      ".calendar-section > .eyebrow, .calendar-section > h2, " +
      ".cta-panel > .eyebrow, .cta-panel > h2, .cta-panel > p:not(.eyebrow)"
  );

  targets.forEach((target) => {
    wrapTextNodes(target);
  });

  const observer = new IntersectionObserver(
    (entries, currentObserver) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;

        entry.target.classList.add("is-typing-in");
        currentObserver.unobserve(entry.target);
      });
    },
    { threshold: 0.25 }
  );

  targets.forEach((target) => observer.observe(target));
}

function wrapTextNodes(element) {
  let characterIndex = 0;
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
  const textNodes = [];

  while (walker.nextNode()) textNodes.push(walker.currentNode);

  textNodes.forEach((node) => {
    if (!node.textContent.trim()) return;

    const fragment = document.createDocumentFragment();
    let whitespace = "";

    [...node.textContent].forEach((character) => {
      if (/\s/.test(character)) {
        whitespace += character;
        return;
      }

      if (whitespace) {
        fragment.appendChild(document.createTextNode(whitespace));
        whitespace = "";
      }

      const span = document.createElement("span");
      span.className = "typing-character";
      span.textContent = character;
      span.style.transitionDelay = `${characterIndex++ * 0.018}s`;
      fragment.appendChild(span);
    });

    if (whitespace) {
      fragment.appendChild(document.createTextNode(whitespace));
    }

    node.parentNode.replaceChild(fragment, node);
  });
}

function renderCalendar(year, month) {
  const grid = document.getElementById("calendarGrid");
  const monthLabel = document.getElementById("monthLabel");
  grid.innerHTML = "";
  monthLabel.textContent = `${year}년 ${month}월`;
  const first = new Date(year, month - 1, 1);
  const last = new Date(year, month, 0);
  const prevLast = new Date(year, month - 1, 0).getDate();
  const start = first.getDay();
  const total = Math.ceil((start + last.getDate()) / 7) * 7;

  for (let i = 0; i < total; i++) {
    const cell = document.createElement("div");
    cell.className = "day";
    let day = i - start + 1;
    if (day < 1) {
      day = prevLast + day;
      cell.classList.add("muted");
    } else if (day > last.getDate()) {
      day = day - last.getDate();
      cell.classList.add("muted");
    }
    cell.textContent = day;

    if (!cell.classList.contains("muted") && events[day]) {
      const e = document.createElement("div");
      e.className = `event ${events[day].type}`;
      e.textContent = events[day].label;
      cell.appendChild(e);
    }
    grid.appendChild(cell);
  }
}

let currentYear = 2026;
let currentMonth = 8;
renderCalendar(currentYear, currentMonth);

document.getElementById("prevBtn").addEventListener("click", () => {
  currentMonth--;
  if (currentMonth === 0) { currentMonth = 12; currentYear--; }
  renderCalendar(currentYear, currentMonth);
});
document.getElementById("nextBtn").addEventListener("click", () => {
  currentMonth++;
  if (currentMonth === 13) { currentMonth = 1; currentYear++; }
  renderCalendar(currentYear, currentMonth);
});
document.getElementById("todayBtn").addEventListener("click", () => {
  currentYear = 2026;
  currentMonth = 8;
  renderCalendar(currentYear, currentMonth);
});
