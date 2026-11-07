const cardsHost = document.querySelector("[data-projects]");
const statsHost = document.querySelector("[data-stats]");

const cardTemplate = (project) => `
    <article class="card" style="--accent:${project.accent}" data-reveal>
        <div class="card__media">
            <img src="${project.visual || project.gif}" alt="Анимация проекта ${project.number}: ${project.title}" loading="lazy" width="480" height="270">
            <span class="card__num">0${project.number}</span>
        </div>
        <div class="card__body">
            <h3>${project.title}</h3>
            <p>${project.subtitle}</p>
            <div class="tags">${project.skills.map((skill) => `<span class="tag">${skill}</span>`).join("")}</div>
            <a class="card__link" href="${project.page}">Открыть проект</a>
        </div>
    </article>
`;

const observeReveal = () => {
    const targets = document.querySelectorAll("[data-reveal]");
    if (!("IntersectionObserver" in window)) {
        targets.forEach((node) => node.classList.add("is-visible"));
        return;
    }
    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (entry.isIntersecting) {
                const delay = Number(entry.target.dataset.delay || 0);
                window.setTimeout(() => entry.target.classList.add("is-visible"), delay);
                observer.unobserve(entry.target);
            }
        });
    }, { threshold: 0.16, rootMargin: "0px 0px -40px 0px" });
    targets.forEach((node) => observer.observe(node));
};

const renderProjects = (projects) => {
    if (!cardsHost) {
        return;
    }
    cardsHost.innerHTML = projects.map(cardTemplate).join("");
    document.querySelectorAll(".card").forEach((card, index) => {
        card.dataset.delay = String(index * 70);
    });
    observeReveal();
};

const statBlock = (value, label) => `<div class="stat"><b>${value}</b><span>${label}</span></div>`;

const loadStats = async () => {
    if (!statsHost) {
        return;
    }
    let workItems = "—";
    let leaders = "—";
    try {
        const page = await fetch("/api/v1/work-items?page=0&size=1").then((r) => r.json());
        workItems = String(page.totalItems ?? page.totalElements ?? "—");
    } catch (error) {
        workItems = "offline";
    }
    try {
        const scores = await fetch("/api/game/leaderboard?limit=50").then((r) => r.json());
        leaders = String(Array.isArray(scores) ? scores.length : "—");
    } catch (error) {
        leaders = "offline";
    }
    statsHost.innerHTML = [
        statBlock("8", "проектов в витрине"),
        statBlock(workItems, "задач в PostgreSQL/H2"),
        statBlock("8", "уровней в игре"),
        statBlock(leaders, "результатов в таблице лидеров"),
    ].join("");
};

const fallbackProjects = [
    { key: "p1", number: 1, title: "REST API по контракту", subtitle: "DTO, валидация, пагинация, идемпотентность", page: "/projects/p1.html", gif: "/gifs/p1.gif", accent: "#38bdf8", skills: ["Spring MVC", "Jakarta Validation", "Idempotency-Key"], endpoints: [] },
    { key: "p2", number: 2, title: "JPA без N+1", subtitle: "fetch join, EntityGraph, DTO-проекция, @Version", page: "/projects/p2.html", gif: "/gifs/p2.gif", accent: "#a78bfa", skills: ["Hibernate", "SELECT FOR UPDATE"], endpoints: [] },
    { key: "p3", number: 3, title: "Границы транзакций", subtitle: "REQUIRED, REQUIRES_NEW, откат, self-invocation", page: "/projects/p3.html", gif: "/gifs/p3.gif", accent: "#f472b6", skills: ["@Transactional", "AOP-прокси"], endpoints: [] },
    { key: "p4", number: 4, title: "Многопоточность", subtitle: "CompletableFuture, тайм-ауты, гонки, дедлок, пул", page: "/projects/p4.html", gif: "/gifs/p4.gif", accent: "#fbbf24", skills: ["ExecutorService", "orTimeout"], endpoints: [] },
    { key: "p5", number: 5, title: "Брокер и Outbox", subtitle: "партиции, группы, повторы, DLQ, дедупликация", page: "/projects/p5.html", gif: "/gifs/p5.gif", accent: "#34d399", skills: ["Kafka-семантика", "Outbox"], endpoints: [] },
    { key: "p6", number: 6, title: "XML и XSD", subtitle: "JAXB, include схем, валидация, защита от XXE", page: "/projects/p6.html", gif: "/gifs/p6.gif", accent: "#22d3ee", skills: ["JAXB", "SchemaFactory"], endpoints: [] },
    { key: "p7", number: 7, title: "Подпись и сертификаты", subtitle: "JCA/JCE, X.509, KeyStore, проверка цепочки", page: "/projects/p7.html", gif: "/gifs/p7.gif", accent: "#c084fc", skills: ["Signature", "PKCS12"], endpoints: [] },
    { key: "p8", number: 8, title: "WebSocket и состояния", subtitle: "жизненный цикл соединений, машина состояний, лимиты", page: "/projects/p8.html", gif: "/gifs/p8.gif", accent: "#fb7185", skills: ["Spring WebSocket", "Машина состояний"], endpoints: [] },
];

const boot = async () => {
    let projects = fallbackProjects;
    try {
        const response = await fetch("/api/showcase/projects");
        if (response.ok) {
            const data = await response.json();
            if (Array.isArray(data) && data.length === 8) {
                projects = data;
            }
        }
    } catch (error) {
        projects = fallbackProjects;
    }
    renderProjects(projects);
    loadStats();
    observeReveal();
};

document.addEventListener("DOMContentLoaded", boot);
