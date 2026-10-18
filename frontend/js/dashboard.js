/**
 * dashboard.js
 * ------------------------------------------------------------------
 * Lógica da tela principal (dashboard.html):
 *  - carrega categorias, tarefas e o resumo pela API (js/api.js)
 *  - aplica filtros, busca, ordenação e paginação (feitos NO SERVIDOR)
 *  - criar/editar/excluir tarefas e categorias, com modais e avisos (toasts)
 *
 * Organização do arquivo:
 *   1. Constantes e estado       4. Tarefas (lista, filtros, paginação)
 *   2. Utilitários               5. Modais (tarefa, categoria, confirmação)
 *   3. Categorias (barra lateral) 6. Inicialização
 */
(() => {
  "use strict";

  // ============================================================ 1. CONSTANTES E ESTADO

  const PAGE_SIZE = 10;
  const DEFAULT_COLOR = "#94A3B8"; // cinza usado quando a categoria não tem cor

  // Paleta do seletor de cor (mesmos 8 hex do design)
  const PALETTE = [
    { hex: "#2563EB", name: "Azul Clássico" },
    { hex: "#8B5CF6", name: "Roxo Intenso" },
    { hex: "#10B981", name: "Verde Esmeralda" },
    { hex: "#F59E0B", name: "Âmbar / Alerta" },
    { hex: "#EF4444", name: "Vermelho Coral" },
    { hex: "#EC4899", name: "Rosa Vibrante" },
    { hex: "#06B6D4", name: "Ciano Oceano" },
    { hex: "#6366F1", name: "Índigo Noite" },
  ];

  // As chaves (TODO, HIGH...) são exatamente os valores do enum da API
  const STATUS = {
    TODO: { label: "A fazer", select: "bg-surface-container-high text-on-surface-variant" },
    IN_PROGRESS: { label: "Em andamento", select: "bg-primary-fixed text-primary" },
    DONE: { label: "Concluída", select: "bg-tertiary-fixed/40 text-tertiary" },
  };
  const PRIORITY = {
    HIGH: { label: "Alta", badge: "bg-error-container text-on-error-container", dot: "bg-error" },
    MEDIUM: { label: "Média", badge: "bg-amber-100 text-amber-800", dot: "bg-amber-500" },
    LOW: { label: "Baixa", badge: "bg-emerald-100 text-emerald-800", dot: "bg-emerald-500" },
  };

  /** Estado da tela. Qualquer mudança nos filtros altera isto e chama loadTasks(). */
  const state = {
    categories: [],
    tasks: [], // tarefas da página atual
    filters: { status: "", priority: "", categoryId: "", search: "", sortBy: "createdAt", direction: "desc", page: 0 },
    editingTaskId: null,
    editingCategoryId: null,
    selectedColor: PALETTE[0].hex,
    loadSeq: 0, // evita que uma resposta antiga sobrescreva uma mais nova
  };

  // ============================================================ 2. UTILITÁRIOS

  const $ = (id) => document.getElementById(id);

  /**
   * Escapa texto do usuário antes de colocá-lo em innerHTML.
   * É a defesa contra XSS: sem isto, um título como <img onerror=...> executaria código.
   */
  function esc(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  /** Aceita apenas #RRGGBB (valor vindo da API vai em atributo style). */
  const safeColor = (c) => (/^#[0-9A-Fa-f]{6}$/.test(c || "") ? c : DEFAULT_COLOR);

  /** "2026-12-31" -> "31/12/2026" (sem usar Date, para não sofrer com fuso horário). */
  function formatDate(iso) {
    if (!iso) return "";
    const [y, m, d] = iso.split("-");
    return `${d}/${m}/${y}`;
  }

  /** Data de hoje no formato yyyy-MM-dd (hora local), para comparar com o prazo. */
  function todayISO() {
    const n = new Date();
    const pad = (x) => String(x).padStart(2, "0");
    return `${n.getFullYear()}-${pad(n.getMonth() + 1)}-${pad(n.getDate())}`;
  }

  function initials(name) {
    const parts = String(name || "").trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return "?";
    const first = parts[0][0];
    const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
    return (first + last).toUpperCase();
  }

  /** Mensagem amigável a partir de um erro da API (prioriza o erro do primeiro campo inválido). */
  function errorMessage(err) {
    if (err && err.fieldErrors) {
      const first = Object.values(err.fieldErrors)[0];
      if (first) return first;
    }
    return (err && err.message) || "Erro inesperado. Tente novamente.";
  }

  function debounce(fn, ms) {
    let t;
    return (...args) => {
      clearTimeout(t);
      t = setTimeout(() => fn(...args), ms);
    };
  }

  // ---------------------------------------------------------------- avisos (toasts)

  function toast(type, message) {
    const ok = type === "success";
    const el = document.createElement("div");
    el.className =
      "pointer-events-auto flex items-start gap-3 p-4 rounded-xl bg-surface-container-lowest shadow-xl transition-all duration-300";
    el.innerHTML = `
      <div class="w-9 h-9 rounded-lg ${ok ? "bg-tertiary-fixed/30 text-tertiary" : "bg-error-container text-error"} flex items-center justify-center flex-shrink-0">
        <span class="material-symbols-outlined text-xl" style="font-variation-settings: 'FILL' 1;">${ok ? "check_circle" : "error"}</span>
      </div>
      <div class="flex-1 min-w-0 pt-0.5">
        <p class="font-label-lg text-label-lg text-on-surface">${ok ? "Sucesso" : "Erro"}</p>
        <p class="toast-msg font-body-sm text-body-sm text-on-surface-variant mt-0.5 leading-snug"></p>
      </div>
      <button type="button" aria-label="Fechar aviso" class="p-1 rounded-lg text-outline hover:text-on-surface hover:bg-surface-container-low transition-colors">
        <span class="material-symbols-outlined text-base">close</span>
      </button>`;
    el.querySelector(".toast-msg").textContent = message; // textContent: nunca interpreta HTML
    const remove = () => {
      el.style.opacity = "0";
      setTimeout(() => el.remove(), 300);
    };
    el.querySelector("button").addEventListener("click", remove);
    $("toast-root").appendChild(el);
    setTimeout(remove, 4000);
  }

  // ---------------------------------------------------------------- abrir/fechar modais

  function openModal(id) {
    $(id).classList.remove("hidden");
    document.body.style.overflow = "hidden";
  }
  function closeModal(id) {
    $(id).classList.add("hidden");
    const anyOpen = ["task-modal", "category-modal", "confirm-modal"].some((m) => !$(m).classList.contains("hidden"));
    if (!anyOpen) document.body.style.overflow = "";
  }
  function showInline(boxId, textId, message) {
    $(textId).textContent = message;
    $(boxId).classList.remove("hidden");
  }
  const hideInline = (boxId) => $(boxId).classList.add("hidden");

  /** Coloca o botão em estado "carregando" e devolve uma função que o restaura. */
  function setBusy(button, busyText) {
    const labelEl = button.querySelector("span:last-child");
    const original = labelEl.textContent;
    button.disabled = true;
    labelEl.textContent = busyText;
    return () => {
      button.disabled = false;
      labelEl.textContent = original;
    };
  }

  // ============================================================ 3. CATEGORIAS

  async function loadCategories() {
    state.categories = await TodoList.listCategories();
    renderCategories();
  }

  function renderCategories() {
    const active = state.filters.categoryId;

    // 3.1 Barra lateral
    const activeCls = "bg-surface-container-low text-primary font-semibold";
    const idleCls = "text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface";
    $("nav-all").className =
      "flex items-center justify-between px-3 py-2 rounded-lg font-body-sm text-body-sm transition-colors text-left " +
      (active === "" ? activeCls : idleCls);

    $("category-list").innerHTML = state.categories
      .map(
        (c) => `
      <div class="group flex items-center justify-between px-3 py-2 rounded-lg font-body-sm text-body-sm cursor-pointer transition-colors ${
        String(c.id) === String(active) ? activeCls : idleCls
      }" data-cat-id="${c.id}">
        <div class="flex items-center gap-space-sm min-w-0">
          <span class="w-2.5 h-2.5 rounded-full flex-shrink-0" style="background-color:${safeColor(c.color)}"></span>
          <span class="truncate">${esc(c.name)}</span>
        </div>
        <div class="flex items-center gap-1 opacity-0 group-hover:opacity-90 transition-opacity flex-shrink-0">
          <button type="button" data-action="edit-category" title="Editar" class="p-1 text-outline hover:text-primary"><span class="material-symbols-outlined text-sm">edit</span></button>
          <button type="button" data-action="delete-category" title="Excluir" class="p-1 text-outline hover:text-error"><span class="material-symbols-outlined text-sm">delete</span></button>
        </div>
      </div>`
      )
      .join("");

    // 3.2 Select de filtro (o value é o ID da categoria, que é o que a API espera)
    $("filter-category").innerHTML =
      `<option value="">Categoria: Todas</option>` +
      state.categories.map((c) => `<option value="${c.id}">${esc(c.name)}</option>`).join("");
    $("filter-category").value = active;
  }

  function setCategoryFilter(categoryId) {
    state.filters.categoryId = String(categoryId);
    state.filters.page = 0;
    renderCategories();
    loadTasks();
  }

  // ============================================================ 4. TAREFAS

  /** Mostra linhas "esqueleto" enquanto a API responde. */
  function renderSkeleton() {
    const row = `
      <div class="grid grid-cols-12 items-center px-6 py-4 border-b border-surface-container-high/40">
        <div class="col-span-8 lg:col-span-4 flex items-center gap-3">
          <div class="w-5 h-5 rounded-md bg-surface-container-highest animate-pulse"></div>
          <div class="flex flex-col gap-2 flex-1 pr-4">
            <div class="h-4 w-4/5 rounded bg-surface-container-highest animate-pulse"></div>
            <div class="h-3 w-2/5 rounded bg-surface-container-high animate-pulse"></div>
          </div>
        </div>
        <div class="hidden lg:block col-span-2"><div class="h-5 w-24 rounded-full bg-surface-container-highest animate-pulse"></div></div>
        <div class="hidden lg:block col-span-2"><div class="h-4 w-24 rounded bg-surface-container-high animate-pulse"></div></div>
        <div class="hidden lg:block col-span-1"><div class="h-6 w-14 rounded-full bg-surface-container-highest animate-pulse"></div></div>
        <div class="col-span-4 lg:col-span-3 flex justify-end gap-2"><div class="h-8 w-28 rounded-lg bg-surface-container-high animate-pulse"></div></div>
      </div>`;
    $("tasks-container").innerHTML = row.repeat(5);
    $("tasks-container").classList.remove("hidden");
    $("empty-state").classList.add("hidden");
    $("pagination").classList.add("hidden");
  }

  async function loadTasks() {
    const seq = ++state.loadSeq;
    renderSkeleton();
    try {
      const f = state.filters;
      const page = await TodoList.listTasks({
        status: f.status,
        priority: f.priority,
        categoryId: f.categoryId,
        search: f.search,
        sortBy: f.sortBy,
        direction: f.direction,
        page: f.page,
        size: PAGE_SIZE,
      });
      if (seq !== state.loadSeq) return; // chegou uma resposta mais nova, ignora esta

      // Excluiu o último item da última página? Volta uma página.
      if (page.content.length === 0 && f.page > 0) {
        f.page = Math.max(page.totalPages - 1, 0);
        return loadTasks();
      }
      state.tasks = page.content;
      renderTasks(page);
    } catch (err) {
      if (seq !== state.loadSeq) return;
      $("tasks-container").innerHTML = "";
      toast("error", errorMessage(err));
    }
  }

  async function loadSummary() {
    try {
      const s = await TodoList.taskSummary();
      $("count-todo").textContent = s.todo;
      $("count-progress").textContent = s.inProgress;
      $("count-done").textContent = s.done;
    } catch (err) {
      toast("error", errorMessage(err));
    }
  }

  /** Recarrega lista e cards (usado depois de qualquer alteração). */
  const refresh = () => Promise.all([loadTasks(), loadSummary()]);

  function hasActiveFilters() {
    const f = state.filters;
    return !!(f.status || f.priority || f.categoryId || f.search);
  }

  function renderTasks(page) {
    const total = page.totalElements;

    if (total === 0) {
      $("tasks-container").classList.add("hidden");
      $("pagination").classList.add("hidden");
      $("empty-state").classList.remove("hidden");
      const filtered = hasActiveFilters();
      $("empty-text").textContent = filtered
        ? "Nenhuma tarefa corresponde aos filtros aplicados. Ajuste a busca ou limpe os filtros."
        : "Que tal criar sua primeira tarefa para começar o dia com o pé direito?";
      $("empty-clear").classList.toggle("hidden", !filtered);
      return;
    }

    $("empty-state").classList.add("hidden");
    $("tasks-container").classList.remove("hidden");
    $("tasks-container").innerHTML = page.content.map(taskRowHTML).join("");
    renderPagination(page);
  }

  /** HTML de uma linha da tabela. Colunas (12): título 4 | categoria 2 | data 2 | prioridade 1 | status+ações 3. */
  function taskRowHTML(t) {
    const done = t.status === "DONE";
    const overdue = t.dueDate && !done && t.dueDate < todayISO();
    const prio = PRIORITY[t.priority] || PRIORITY.MEDIUM;
    const st = STATUS[t.status] || STATUS.TODO;

    const category = t.category
      ? `<span class="w-2.5 h-2.5 rounded-full flex-shrink-0" style="background-color:${safeColor(t.category.color)}"></span>
         <span class="font-body-sm text-body-sm text-on-surface truncate">${esc(t.category.name)}</span>`
      : `<span class="font-body-sm text-body-sm text-outline">Sem categoria</span>`;

    const due = t.dueDate
      ? overdue
        ? `<span class="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md bg-error-container/50 text-error font-body-sm text-body-sm font-semibold" title="Prazo vencido">
             <span class="material-symbols-outlined text-base">event_busy</span>${formatDate(t.dueDate)}</span>`
        : `<span class="material-symbols-outlined text-base text-outline">calendar_today</span><span>${formatDate(t.dueDate)}</span>`
      : `<span class="text-outline">—</span>`;

    const options = Object.entries(STATUS)
      .map(([value, s]) => `<option value="${value}" ${value === t.status ? "selected" : ""}>${s.label}</option>`)
      .join("");

    return `
    <div class="group flex flex-col lg:grid lg:grid-cols-12 items-start lg:items-center px-6 py-4 transition-colors hover:bg-surface-container-low/60 gap-3 lg:gap-0 bg-surface-container-lowest border-b border-surface-container-high/40" data-task-id="${t.id}">
      <div class="lg:col-span-4 flex items-start gap-space-md min-w-0 pr-4 w-full">
        <button type="button" data-action="toggle" aria-label="${done ? "Reabrir tarefa" : "Concluir tarefa"}"
          class="mt-1 w-5 h-5 rounded-md flex items-center justify-center flex-shrink-0 transition-all ${
            done ? "bg-tertiary text-on-tertiary" : "bg-surface-container-low text-transparent hover:text-primary"
          }">
          <span class="material-symbols-outlined text-base font-bold">check</span>
        </button>
        <div class="flex flex-col min-w-0">
          <span class="font-headline-sm text-headline-sm truncate font-semibold transition-colors ${
            done ? "line-through text-outline" : "text-on-surface group-hover:text-primary"
          }" title="${esc(t.title)}">${esc(t.title)}</span>
          ${
            t.description
              ? `<span class="font-body-sm text-body-sm truncate ${done ? "text-outline" : "text-on-surface-variant"}" title="${esc(t.description)}">${esc(t.description)}</span>`
              : ""
          }
        </div>
      </div>
      <div class="lg:col-span-2 flex items-center gap-space-xs min-w-0 pr-2">${category}</div>
      <div class="lg:col-span-2 flex items-center gap-1.5 text-on-surface-variant font-body-sm text-body-sm">${due}</div>
      <div class="lg:col-span-1">
        <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full ${prio.badge} font-label-sm text-label-sm font-semibold whitespace-nowrap">
          <span class="w-1.5 h-1.5 rounded-full ${prio.dot}"></span>${prio.label}
        </span>
      </div>
      <div class="lg:col-span-3 flex items-center justify-between lg:justify-end gap-space-sm w-full">
        <select data-action="status" aria-label="Alterar status" class="${st.select} font-label-md text-label-md rounded-lg px-2.5 py-1.5 focus:outline-none cursor-pointer">${options}</select>
        <div class="flex items-center gap-1">
          <button type="button" data-action="edit" title="Editar" class="p-1.5 rounded-lg text-outline hover:text-primary hover:bg-surface-container-high transition-colors"><span class="material-symbols-outlined text-lg">edit</span></button>
          <button type="button" data-action="delete" title="Excluir" class="p-1.5 rounded-lg text-outline hover:text-error hover:bg-error-container/40 transition-colors"><span class="material-symbols-outlined text-lg">delete</span></button>
        </div>
      </div>
    </div>`;
  }

  function renderPagination(p) {
    const from = p.page * p.size + 1;
    const to = Math.min((p.page + 1) * p.size, p.totalElements);
    $("page-info").innerHTML =
      `<span>Mostrando</span><span class="font-semibold text-on-surface">${from}-${to}</span><span>de</span>` +
      `<span class="font-semibold text-on-surface">${p.totalElements} ${p.totalElements === 1 ? "tarefa" : "tarefas"}</span>`;
    $("page-label").textContent = `Página ${p.page + 1} de ${Math.max(p.totalPages, 1)}`;
    $("page-prev").disabled = p.page === 0;
    $("page-next").disabled = p.last;

    // Janela de até 5 números de página ao redor da atual
    const start = Math.max(0, Math.min(p.page - 2, p.totalPages - 5));
    const end = Math.min(p.totalPages, start + 5);
    let buttons = "";
    for (let i = start; i < end; i++) {
      const current = i === p.page;
      buttons += `<button type="button" data-page="${i}" class="w-8 h-8 rounded-lg font-label-md text-label-md font-semibold transition-colors ${
        current
          ? "bg-primary-container text-on-primary"
          : "bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container-high"
      }">${i + 1}</button>`;
    }
    $("page-numbers").innerHTML = buttons;
    $("pagination").classList.remove("hidden");
  }

  function goToPage(n) {
    state.filters.page = n;
    loadTasks();
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  /** Volta todos os filtros ao padrão (mantém a ordenação escolhida). */
  function clearFilters() {
    Object.assign(state.filters, { status: "", priority: "", categoryId: "", search: "", page: 0 });
    $("filter-status").value = "";
    $("filter-priority").value = "";
    $("task-search").value = "";
    renderCategories();
    loadTasks();
  }

  // ---------------------------------------------------------------- ações na linha

  const findTask = (id) => state.tasks.find((t) => String(t.id) === String(id));

  async function changeStatus(taskId, status) {
    try {
      await TodoList.updateTaskStatus(taskId, status);
      toast("success", "Status atualizado.");
    } catch (err) {
      toast("error", errorMessage(err));
    }
    refresh(); // em caso de erro também recarrega, devolvendo o select ao valor real
  }

  async function deleteTask(task) {
    const prio = PRIORITY[task.priority] || PRIORITY.MEDIUM;
    const preview = `
      <div class="flex flex-col min-w-0">
        <span class="font-label-lg text-label-lg text-on-surface truncate">${esc(task.title)}</span>
        <span class="font-code-sm text-code-sm text-on-surface-variant flex items-center gap-1.5">
          ${task.category ? `<span class="w-2 h-2 rounded-full" style="background-color:${safeColor(task.category.color)}"></span>${esc(task.category.name)}` : "Sem categoria"}
          ${task.dueDate ? ` • Vence em ${formatDate(task.dueDate)}` : ""}
        </span>
      </div>
      <span class="px-2 py-1 rounded ${prio.badge} font-label-sm text-label-sm flex-shrink-0">${prio.label}</span>`;

    const ok = await confirmDialog({
      title: "Excluir tarefa?",
      text: "Essa ação não pode ser desfeita. A tarefa será removida permanentemente.",
      previewHTML: preview,
      action: () => TodoList.deleteTask(task.id),
    });
    if (ok) {
      toast("success", "Tarefa excluída.");
      refresh();
    }
  }

  // ============================================================ 5. MODAIS

  // ---------------------------------------------------------------- 5.1 modal de tarefa

  function fillCategorySelect(selectedId) {
    $("task-category").innerHTML =
      `<option value="">Sem categoria</option>` +
      state.categories.map((c) => `<option value="${c.id}">${esc(c.name)}</option>`).join("");
    $("task-category").value = selectedId ? String(selectedId) : "";
  }

  const selectedPriority = () => document.querySelector('input[name="priority"]:checked').value;
  const setPriority = (value) => {
    document.querySelector(`input[name="priority"][value="${value}"]`).checked = true;
  };

  function updateTaskCounters() {
    $("title-counter").textContent = `${$("task-title").value.length} / 150`;
    $("desc-counter").textContent = `${$("task-desc").value.length} / 2000`;
  }

  /** Sem argumento = criar; com uma tarefa = editar. */
  function openTaskModal(task = null) {
    state.editingTaskId = task ? task.id : null;
    hideInline("task-form-error");

    $("task-modal-title").textContent = task ? "Editar tarefa" : "Criar nova tarefa";
    $("task-modal-subtitle").textContent = task
      ? "Atualize os detalhes da sua tarefa no seu fluxo diário."
      : "Preencha os detalhes da sua tarefa para organizá-la no seu fluxo diário.";
    $("task-submit-label").textContent = task ? "Salvar alterações" : "Salvar tarefa";

    $("task-title").value = task ? task.title : "";
    $("task-desc").value = task ? task.description || "" : "";
    setPriority(task ? task.priority : "MEDIUM");
    $("task-status").value = task ? task.status : "TODO";
    $("task-due-date").value = task && task.dueDate ? task.dueDate : ""; // a data é opcional
    // Ao criar, já sugere a categoria que está filtrada no momento
    fillCategorySelect(task ? (task.category ? task.category.id : "") : state.filters.categoryId);
    updateTaskCounters();

    openModal("task-modal");
    $("task-title").focus();
  }

  async function submitTask() {
    const title = $("task-title").value.trim();
    if (!title) {
      showInline("task-form-error", "task-form-error-text", "Informe o título da tarefa.");
      $("task-title").focus();
      return;
    }
    hideInline("task-form-error");

    // Campos vazios viram null: a API entende "sem descrição / sem data / sem categoria"
    const payload = {
      title,
      description: $("task-desc").value.trim() || null,
      status: $("task-status").value,
      priority: selectedPriority(),
      dueDate: $("task-due-date").value || null,
      categoryId: $("task-category").value ? Number($("task-category").value) : null,
    };

    const editing = state.editingTaskId !== null;
    const restore = setBusy($("task-submit"), "Salvando...");
    try {
      if (editing) await TodoList.updateTask(state.editingTaskId, payload);
      else await TodoList.createTask(payload);
      closeModal("task-modal");
      toast("success", editing ? "Tarefa atualizada." : "Tarefa criada com sucesso!");
      if (!editing) state.filters.page = 0; // nova tarefa aparece no topo da lista
      refresh();
    } catch (err) {
      showInline("task-form-error", "task-form-error-text", errorMessage(err));
    } finally {
      restore();
    }
  }

  // ---------------------------------------------------------------- 5.2 modal de categoria

  function buildColorGrid() {
    $("color-grid").innerHTML = PALETTE.map(
      (c) => `
      <button type="button" data-color="${c.hex}" aria-label="Selecionar cor ${esc(c.name)}"
        class="relative w-9 h-9 rounded-full flex items-center justify-center transition-transform hover:scale-110 active:scale-95"
        style="background-color:${c.hex}">
        <span class="material-symbols-outlined text-white text-base hidden">check</span>
      </button>`
    ).join("");
  }

  function selectColor(hex) {
    state.selectedColor = hex;
    document.querySelectorAll("#color-grid button").forEach((btn) => {
      const on = btn.dataset.color === hex;
      btn.classList.toggle("ring-2", on);
      btn.classList.toggle("ring-primary", on);
      btn.classList.toggle("ring-offset-2", on);
      btn.firstElementChild.classList.toggle("hidden", !on);
    });
    const known = PALETTE.find((c) => c.hex === hex);
    $("color-name").textContent = known ? known.name : "Personalizada";
    updateCategoryPreview();
  }

  function updateCategoryPreview() {
    const name = $("category-name").value.trim() || "Nova categoria";
    const color = state.selectedColor;
    $("category-counter").textContent = `${$("category-name").value.length}/60`;
    $("preview-dot").style.backgroundColor = color;
    $("preview-label").textContent = name;
    $("preview-chip").style.backgroundColor = color + "22"; // 22 = ~13% de opacidade
    $("preview-chip").style.color = color;
    $("preview-chip-dot").style.backgroundColor = color;
    $("preview-chip-label").textContent = name;
  }

  function openCategoryModal(category = null) {
    state.editingCategoryId = category ? category.id : null;
    hideInline("category-error");
    $("category-modal-title").textContent = category ? "Editar categoria" : "Nova categoria";
    $("category-modal-subtitle").textContent = category
      ? "Atualize o nome e a cor da sua categoria"
      : "Crie uma categoria para agrupar e colorir suas tarefas diárias";
    $("category-submit-label").textContent = category ? "Salvar alterações" : "Criar categoria";
    $("category-name").value = category ? category.name : "";
    selectColor(category ? safeColor(category.color) : PALETTE[0].hex);
    openModal("category-modal");
    $("category-name").focus();
  }

  async function submitCategory() {
    const name = $("category-name").value.trim();
    if (!name) {
      showInline("category-error", "category-error-text", "Informe o nome da categoria.");
      return;
    }
    hideInline("category-error");

    const payload = { name, color: state.selectedColor };
    const editing = state.editingCategoryId !== null;
    const restore = setBusy($("category-submit"), "Salvando...");
    try {
      if (editing) await TodoList.updateCategory(state.editingCategoryId, payload);
      else await TodoList.createCategory(payload);
      closeModal("category-modal");
      toast("success", editing ? "Categoria atualizada." : "Categoria criada.");
      await loadCategories();
      loadTasks(); // as tarefas exibem nome/cor da categoria
    } catch (err) {
      // Ex.: 409 "Você já possui uma categoria com esse nome"
      showInline("category-error", "category-error-text", errorMessage(err));
    } finally {
      restore();
    }
  }

  async function deleteCategory(category) {
    const ok = await confirmDialog({
      title: "Excluir categoria?",
      text: "As tarefas dessa categoria não serão apagadas, apenas ficarão sem categoria.",
      previewHTML: `
        <div class="flex items-center gap-3 min-w-0">
          <span class="w-3 h-3 rounded-full flex-shrink-0" style="background-color:${safeColor(category.color)}"></span>
          <span class="font-label-lg text-label-lg text-on-surface truncate">${esc(category.name)}</span>
        </div>`,
      action: () => TodoList.deleteCategory(category.id),
    });
    if (!ok) return;
    toast("success", "Categoria excluída.");
    if (String(state.filters.categoryId) === String(category.id)) {
      state.filters.categoryId = ""; // não pode continuar filtrando por algo que não existe mais
      state.filters.page = 0;
    }
    await loadCategories();
    refresh();
  }

  // ---------------------------------------------------------------- 5.3 confirmação de exclusão

  let confirmResolve = null;

  /**
   * Abre o modal de confirmação. Devolve uma Promise<boolean>:
   * true = excluiu com sucesso, false = cancelou ou deu erro (o erro já vira toast).
   */
  function confirmDialog({ title, text, previewHTML, action }) {
    $("confirm-title").textContent = title;
    $("confirm-text").textContent = text;
    $("confirm-preview").innerHTML = previewHTML || "";
    $("confirm-preview").classList.toggle("hidden", !previewHTML);
    $("confirm-ok").onclick = async () => {
      const restore = setBusy($("confirm-ok"), "Excluindo...");
      try {
        await action();
        finishConfirm(true);
      } catch (err) {
        toast("error", errorMessage(err));
        finishConfirm(false);
      } finally {
        restore();
      }
    };
    openModal("confirm-modal");
    return new Promise((resolve) => {
      confirmResolve = resolve;
    });
  }

  function finishConfirm(result) {
    closeModal("confirm-modal");
    if (confirmResolve) confirmResolve(result);
    confirmResolve = null;
  }

  // ============================================================ 6. INICIALIZAÇÃO

  function bindEvents() {
    // --- Fechar modais: botão X, "Cancelar" e clique no fundo escuro (atributo data-close)
    document.addEventListener("click", (e) => {
      const closer = e.target.closest("[data-close]");
      if (!closer) return;
      if (closer.dataset.close === "confirm-modal") finishConfirm(false);
      else closeModal(closer.dataset.close);
    });
    $("confirm-cancel").addEventListener("click", () => finishConfirm(false));
    document.addEventListener("keydown", (e) => {
      if (e.key !== "Escape") return;
      if (!$("confirm-modal").classList.contains("hidden")) finishConfirm(false);
      else if (!$("task-modal").classList.contains("hidden")) closeModal("task-modal");
      else if (!$("category-modal").classList.contains("hidden")) closeModal("category-modal");
    });

    // --- Sessão
    $("btn-logout").addEventListener("click", () => {
      TodoList.logout();
      window.location.href = "login.html";
    });

    // --- Filtros
    $("task-search").addEventListener(
      "input",
      debounce((e) => {
        state.filters.search = e.target.value.trim();
        state.filters.page = 0;
        loadTasks();
      }, 350) // espera o usuário parar de digitar antes de consultar a API
    );
    $("filter-status").addEventListener("change", (e) => {
      state.filters.status = e.target.value;
      state.filters.page = 0;
      loadTasks();
    });
    $("filter-priority").addEventListener("change", (e) => {
      state.filters.priority = e.target.value;
      state.filters.page = 0;
      loadTasks();
    });
    $("filter-category").addEventListener("change", (e) => setCategoryFilter(e.target.value));
    $("filter-sort").addEventListener("change", (e) => {
      state.filters.sortBy = e.target.value;
      state.filters.page = 0;
      loadTasks();
    });
    $("btn-sort-dir").addEventListener("click", () => {
      state.filters.direction = state.filters.direction === "desc" ? "asc" : "desc";
      const desc = state.filters.direction === "desc";
      $("sort-dir-icon").textContent = desc ? "arrow_downward" : "arrow_upward";
      $("btn-sort-dir").title = desc ? "Ordem decrescente" : "Ordem crescente";
      state.filters.page = 0;
      loadTasks();
    });
    $("empty-clear").addEventListener("click", clearFilters);

    // --- Paginação
    $("page-prev").addEventListener("click", () => goToPage(state.filters.page - 1));
    $("page-next").addEventListener("click", () => goToPage(state.filters.page + 1));
    $("page-numbers").addEventListener("click", (e) => {
      const btn = e.target.closest("[data-page]");
      if (btn) goToPage(Number(btn.dataset.page));
    });

    // --- Linhas da tabela (delegação de eventos: um listener só para todas as linhas)
    const container = $("tasks-container");
    container.addEventListener("click", (e) => {
      const btn = e.target.closest("[data-action]");
      const row = e.target.closest("[data-task-id]");
      if (!btn || !row || btn.tagName === "SELECT") return;
      const task = findTask(row.dataset.taskId);
      if (!task) return;
      if (btn.dataset.action === "toggle") changeStatus(task.id, task.status === "DONE" ? "TODO" : "DONE");
      if (btn.dataset.action === "edit") openTaskModal(task);
      if (btn.dataset.action === "delete") deleteTask(task);
    });
    container.addEventListener("change", (e) => {
      if (e.target.dataset.action !== "status") return;
      const row = e.target.closest("[data-task-id]");
      changeStatus(row.dataset.taskId, e.target.value);
    });

    // --- Barra lateral de categorias
    $("nav-all").addEventListener("click", () => setCategoryFilter(""));
    $("btn-new-category").addEventListener("click", () => openCategoryModal());
    $("category-list").addEventListener("click", (e) => {
      const row = e.target.closest("[data-cat-id]");
      if (!row) return;
      const category = state.categories.find((c) => String(c.id) === row.dataset.catId);
      const action = e.target.closest("[data-action]");
      if (action && action.dataset.action === "edit-category") return openCategoryModal(category);
      if (action && action.dataset.action === "delete-category") return deleteCategory(category);
      setCategoryFilter(row.dataset.catId);
    });

    // --- Modal de tarefa
    $("btn-new-task").addEventListener("click", () => openTaskModal());
    $("empty-new-task").addEventListener("click", () => openTaskModal());
    $("task-title").addEventListener("input", updateTaskCounters);
    $("task-desc").addEventListener("input", updateTaskCounters);
    $("task-submit").addEventListener("click", submitTask);
    $("task-form").addEventListener("submit", (e) => {
      e.preventDefault();
      submitTask();
    });
    // Ctrl+Enter salva a tarefa
    $("task-modal").addEventListener("keydown", (e) => {
      if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) submitTask();
    });

    // --- Modal de categoria
    buildColorGrid();
    $("color-grid").addEventListener("click", (e) => {
      const btn = e.target.closest("[data-color]");
      if (btn) selectColor(btn.dataset.color);
    });
    $("category-name").addEventListener("input", updateCategoryPreview);
    $("category-form").addEventListener("submit", (e) => {
      e.preventDefault();
      submitCategory();
    });
  }

  function renderHeader() {
    const user = TodoList.getUser() || { name: "Usuário", email: "" };
    $("user-name").textContent = user.name;
    $("user-email").textContent = user.email;
    $("user-initials").textContent = initials(user.name);
    $("greeting").textContent = `Olá, ${user.name} 👋`;

    const date = new Date().toLocaleDateString("pt-BR", {
      weekday: "long",
      day: "numeric",
      month: "long",
      year: "numeric",
    });
    $("live-date").textContent = date.charAt(0).toUpperCase() + date.slice(1);
  }

  async function init() {
    // Rota protegida: sem token, volta para o login
    if (!TodoList.isLoggedIn()) {
      window.location.replace("login.html");
      return;
    }
    renderHeader();
    bindEvents();
    try {
      // As três consultas rodam em paralelo
      await Promise.all([loadCategories(), loadTasks(), loadSummary()]);
    } catch (err) {
      toast("error", errorMessage(err));
    }
  }

  init();
})();
