// Reusable layout/data components for ControlLocal.
// These mirror the Blazor component vocabulary: Sidebar, Topbar, MetricCard,
// DataTable, StatusBadge, FilterBar, FormSection, ActionButtons, Modal, Tabs,
// Timeline.

/* =================== NAVIGATION CONTEXT =================== */
// NavCtx is used by the navigable prototype. In the static design canvas
// (index.html) the context is null and `navigate(...)` is a no-op so screens
// behave as standalone previews.
const NavCtx = React.createContext(null);
const useNav = () => React.useContext(NavCtx) || { navigate: () => {}, role: null, route: null };

/* =================== BRAND MARK ===================
   Reusable logo glyph (a storefront-shaped square with a 'C').
   Used in: Login, Sidebar, Recover/reset notice, Reports header,
   PDF/Excel export headers. Variants: 'dark' (on light bg) /
   'light' (on dark bg) / 'tone' (small navy on grey).            */
const BrandMark = ({ size = 36, variant = "primary", radius }) => {
  const palette = {
    primary: { bg: "#0E5BFF",  ring: "rgba(255,255,255,0.45)", fg: "#fff" },
    light:   { bg: "#fff",     ring: "rgba(14,91,255,0.35)",   fg: "#0E5BFF" },
    dark:    { bg: "#0B1F33",  ring: "rgba(255,255,255,0.30)", fg: "#fff" },
    ghost:   { bg: "transparent", ring: "rgba(14,91,255,0.4)",  fg: "#0E5BFF" },
  }[variant] || { bg: "#0E5BFF", ring: "rgba(255,255,255,0.45)", fg: "#fff" };
  const r = radius != null ? radius : Math.max(6, Math.round(size * 0.25));
  const inset = Math.max(4, Math.round(size * 0.16));
  return (
    <div style={{
      width: size, height: size, borderRadius: r, background: palette.bg,
      color: palette.fg, display: 'grid', placeItems: 'center',
      fontWeight: 700, fontSize: Math.round(size * 0.42),
      letterSpacing: '-0.02em', position: 'relative',
      border: variant === 'light' ? '1px solid #DDE5E8' : 0,
      flex: `0 0 ${size}px`,
    }}>
      <div style={{ position: 'absolute', inset, border: `1.5px solid ${palette.ring}`, borderRadius: Math.max(2, r - inset + 2) }}></div>
      <svg width={Math.round(size * 0.5)} height={Math.round(size * 0.5)} viewBox="0 0 24 24" style={{ position: 'relative', zIndex: 1 }}>
        <path d="M6 11 L12 6 L18 11 L18 18 L13 18 L13 14 L11 14 L11 18 L6 18 Z" fill="none" stroke={palette.fg} strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round" />
      </svg>
    </div>
  );
};

// Role-scoped sidebar navigation. Each role gets a distinct menu — no
// hidden items, no rule-bending. This is the source of truth that mirrors
// the permission matrix.
const SIDE_NAV_BY_ROLE = {
  "Broker administrador": [
    { section: "Principal", items: [
      { icon: "home",    label: "Dashboard global",         route: "dashboard" },
      { icon: "chart",   label: "Reportes globales",        route: "reportes" },
    ]},
    { section: "Administración", items: [
      { icon: "briefcase", label: "Brokers",                 route: "brokers" },
      { icon: "users",     label: "Reasignar agentes",       route: "reasignar" },
      { icon: "folder",    label: "Catálogos del sistema",   route: "catalogs" },
    ]},
    { section: "Supervisión", items: [
      { icon: "history",   label: "Auditoría global",        route: "actividad" },
    ]},
  ],
  "Broker": [
    { section: "Principal", items: [
      { icon: "home",    label: "Dashboard de equipo",       route: "dashboard" },
      { icon: "chart",   label: "Reportes de equipo",        route: "reportes" },
    ]},
    { section: "Mi equipo", items: [
      { icon: "users",   label: "Mis agentes",                route: "agents" },
    ]},
    { section: "Bandejas de revisión", items: [
      { icon: "pin",       label: "Captaciones por revisar",  pill: "9", route: "bandeja-captaciones" },
      { icon: "fileText",  label: "Solicitudes por evaluar",  pill: "6", route: "solicitudes" },
      { icon: "target",    label: "Operaciones del equipo",   route: "oportunidades" },
    ]},
  ],
  "Agente inmobiliario": [
    { section: "Principal", items: [
      { icon: "home",    label: "Dashboard",                  route: "dashboard" },
    ]},
    { section: "Captación", items: [
      { icon: "user",    label: "Propietarios",               route: "owners" },
      { icon: "store",   label: "Locales comerciales",        route: "locales" },
      { icon: "pin",     label: "Captaciones",                pill: "14", route: "captaciones" },
    ]},
    { section: "Comercial", items: [
      { icon: "users",    label: "Clientes interesados",      route: "clientes" },
      { icon: "target",   label: "Oportunidades",             pill: "8", route: "oportunidades" },
      { icon: "calendar", label: "Visitas",                   route: "visitas" },
      { icon: "fileText", label: "Solicitudes de alquiler",   route: "solicitudes" },
    ]},
  ],
};
// Backwards-compat shim (kept so any external import keeps working but is no
// longer consulted by the Sidebar).
const ROLE_HIDDEN = { "Broker administrador": new Set(), "Broker": new Set(), "Agente inmobiliario": new Set() };

/* =================== STATUS BADGE =================== */
const STATUS_MAP = {
  // captacion
  "Pendiente": "amber", "Activa": "green", "Observada": "amber",
  "Rechazada": "red", "Cerrada": "gray",
  // oportunidad
  "Abierta": "blue", "En seguimiento": "blue", "Solicitud creada": "info",
  "Cerrada exitosa": "green", "Cerrada no continúa": "gray", "Cerrada no favorable": "red",
  // solicitud
  "Registrada": "gray", "En revisión": "amber", "Aprobada": "green",
  // visita
  "Programada": "blue", "Reprogramada": "amber", "Realizada": "green", "Cancelada": "red",
  // documentos
  "Aprobado": "green", "Observado": "amber",
  // usuarios
  "Activo": "green", "Inactivo": "gray", "Suspendido": "red",
};
const StatusBadge = ({ label, tone, plain }) => {
  const t = tone || STATUS_MAP[label] || "gray";
  return <span className={`cl-badge ${t}${plain ? ' plain' : ''}`}>{label}</span>;
};

/* =================== SIDEBAR =================== */
// Per-role profile of the footer user shown in the sidebar. We use a
// representative name per role so the prototype reflects the right context.
const ROLE_USER = {
  "Broker administrador": { ini: "AT", name: "Alejandro Téllez",  short: "Admin general" },
  "Broker":                { ini: "RS", name: "Ricardo Salas",     short: "Broker — Lima Centro" },
  "Agente inmobiliario":   { ini: "VM", name: "Valentina Mora",    short: "Agente — Lima Centro" },
};

// Map ancillary routes back to their "parent" sidebar route so the right item
// stays highlighted on detail/edit screens. Keeps the menu in sync without
// every screen having to pass an explicit active label.
const ROUTE_TO_NAV = {
  'profile':           'dashboard',
  'captacion-form':    'captaciones',
  'captacion-review':  'bandeja-captaciones',
  'oportunidad-nueva': 'oportunidades',
  'detail-360':        'oportunidades',
  'interacciones':     'oportunidades',
  'cierre':            'oportunidades',
  'documentos':        'solicitudes',
  'evaluacion':        'solicitudes',
};

const Sidebar = ({ active, role = "Agente inmobiliario" }) => {
  const { navigate, route } = useNav();
  const sections = SIDE_NAV_BY_ROLE[role] || SIDE_NAV_BY_ROLE["Agente inmobiliario"];
  const user = ROLE_USER[role] || ROLE_USER["Agente inmobiliario"];
  const effectiveRoute = route ? (ROUTE_TO_NAV[route] || route) : null;
  return (
    <aside className="cl-sidebar">
      <div className="cl-brand" style={{ cursor: 'pointer' }} onClick={() => navigate('dashboard')}>
        <BrandMark size={36} variant="primary" />
        <div>
          <div className="cl-brand-name">ControlLocal</div>
          <div className="cl-brand-sub">Corretaje comercial</div>
        </div>
      </div>
      <nav className="cl-nav">
        {sections.map((sec, i) => (
          <div className="cl-nav-section" key={i}>
            <div className="cl-nav-label">{sec.section}</div>
            {sec.items.map((it, j) => {
              // Active if: (a) caller passed an explicit active label that matches,
              // or (b) the current navigable route maps to this nav item.
              const isActive = active === it.label || (effectiveRoute && effectiveRoute === it.route);
              return (
                <div key={j}
                  onClick={() => it.route && navigate(it.route)}
                  className={`cl-nav-item${isActive ? ' active' : ''}`}
                  style={{ cursor: it.route ? 'pointer' : 'default' }}>
                  <Icon name={it.icon} size={15} className="cl-ico" />
                  <span>{it.label}</span>
                  {it.pill && <span className="cl-pill">{it.pill}</span>}
                </div>
              );
            })}
          </div>
        ))}
      </nav>
      <div className="cl-sidebar-foot">
        <div className="cl-avatar" style={{ cursor: 'pointer' }} onClick={() => navigate('profile')}>{user.ini}</div>
        <div style={{ flex: 1, minWidth: 0, cursor: 'pointer' }} onClick={() => navigate('profile')}>
          <div className="cl-foot-name">{user.name}</div>
          <div className="cl-foot-role">{user.short}</div>
        </div>
        <span title="Cerrar sesión" style={{ cursor: 'pointer', display: 'inline-flex' }} onClick={() => navigate('login')}>
          <Icon name="logout" size={15} color="#6E7E94" />
        </span>
      </div>
    </aside>
  );
};

/* =================== TOPBAR =================== */
const TOPBAR_SEARCH = {
  "Broker administrador": "Buscar broker, agente, captación, auditoría…",
  "Broker":               "Buscar agente, captación, solicitud, oportunidad…",
  "Agente inmobiliario":  "Buscar oportunidad, local, cliente, captación…",
};
const Topbar = ({ crumbs = [], notifs = 3, role = "Agente inmobiliario" }) => {
  const user = ROLE_USER[role] || ROLE_USER["Agente inmobiliario"];
  return (
  <header className="cl-topbar">
    <div className="cl-search">
      <Icon name="search" size={14} color="#8392A7" />
      <input placeholder={TOPBAR_SEARCH[role] || TOPBAR_SEARCH["Agente inmobiliario"]} />
      <span style={{ fontSize: 11, color: '#8392A7', background: '#fff', border: '1px solid #DDE5E8', padding: '1px 5px', borderRadius: 4 }}>⌘K</span>
    </div>
    <div className="cl-topbar-right">
      <span className="cl-date">Lun · 24 May 2026</span>
      <div className="cl-icon-btn">
        <Icon name="bell" size={17} />
        {notifs > 0 && <span className="cl-dot"></span>}
      </div>
      <div className="cl-profile">
        <div className="cl-avatar" style={{ width: 28, height: 28 }}>{user.ini}</div>
        <div>
          <div className="cl-profile-name">{user.name}</div>
          <div className="cl-profile-role">{role}</div>
        </div>
        <Icon name="chevronDown" size={14} color="#8392A7" />
      </div>
    </div>
  </header>
  );
};

/* =================== BREADCRUMBS =================== */
const Breadcrumbs = ({ items = [] }) => (
  <div className="cl-breadcrumbs">
    {items.map((it, i) => (
      <React.Fragment key={i}>
        {i > 0 && <Icon name="chevronRight" size={11} color="#B5BFCB" />}
        <span className={i === items.length - 1 ? "here" : ""}>{it}</span>
      </React.Fragment>
    ))}
  </div>
);

/* =================== METRIC CARD =================== */
const MetricCard = ({ icon, label, value, delta, deltaDir = "up", tone = "blue", footer }) => {
  const tones = {
    blue: { bg: "#E8F0FF", color: "#0E5BFF" },
    green: { bg: "#E6F6EC", color: "#16A34A" },
    amber: { bg: "#FEF3DC", color: "#B97506" },
    red: { bg: "#FCE7E7", color: "#DC2626" },
    info: { bg: "#E0F1FA", color: "#0284C7" },
    navy: { bg: "#E2E8F0", color: "#0B1F33" },
  };
  const t = tones[tone] || tones.blue;
  return (
    <div className="cl-metric">
      <div className="cl-metric-row">
        {icon && <div className="cl-metric-icon" style={{ background: t.bg, color: t.color }}>
          <Icon name={icon} size={16} />
        </div>}
        <div className="cl-metric-label">{label}</div>
      </div>
      <div className="cl-metric-value">{value}</div>
      {delta != null && (
        <div className={`cl-metric-delta ${deltaDir}`}>
          <Icon name={deltaDir === "up" ? "arrowUpRight" : deltaDir === "down" ? "arrowDownRight" : "arrowRight"} size={11} />
          {delta}
        </div>
      )}
      {footer && <div style={{ fontSize: 11, color: '#8392A7', marginTop: 6 }}>{footer}</div>}
    </div>
  );
};

/* =================== FILTER BAR =================== */
const FilterBar = ({ children, search = "Buscar…" }) => (
  <div className="cl-filterbar">
    <input className="cl-input search" placeholder={search} style={{ width: 280 }} />
    {children}
    <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
      <button className="cl-btn sm"><Icon name="filter" size={13} /> Más filtros</button>
      <button className="cl-btn sm"><Icon name="download" size={13} /> Exportar</button>
    </div>
  </div>
);

/* =================== DATA TABLE =================== */
const DataTable = ({ columns, rows, hover = true }) => (
  <div className="cl-table-wrap">
    <table className="cl-table">
      <thead>
        <tr>{columns.map((c, i) => <th key={i} style={c.width ? { width: c.width } : null}>{c.label}</th>)}</tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>
            {columns.map((c, j) => <td key={j}>{c.render ? c.render(r) : r[c.key]}</td>)}
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

/* =================== ACTION BUTTONS (row) =================== */
const RowActions = ({ items = ["view","edit","more"] }) => (
  <div className="cl-row-actions">
    {items.map((it, i) => {
      if (it === "view") return <button key={i} title="Ver detalle"><Icon name="eye" size={14} /></button>;
      if (it === "edit") return <button key={i} title="Editar"><Icon name="edit" size={13} /></button>;
      if (it === "delete") return <button key={i} className="danger" title="Eliminar"><Icon name="trash" size={13} /></button>;
      if (it === "more") return <button key={i} title="Más"><Icon name="more" size={14} /></button>;
      return null;
    })}
  </div>
);

/* =================== FORM SECTION =================== */
const FormSection = ({ num, title, sub, children, foot }) => (
  <section className="cl-form-section">
    <div className="cl-form-section-head">
      <div className="cl-form-section-num">{num}</div>
      <div style={{ flex: 1 }}>
        <div className="cl-form-section-title">{title}</div>
        {sub && <div className="cl-form-section-sub">{sub}</div>}
      </div>
      {foot}
    </div>
    <div className="cl-form-section-body">{children}</div>
  </section>
);

const Field = ({ label, hint, children, required }) => (
  <div className="cl-field">
    <label className="cl-label">{label}{required && <span style={{ color: '#DC2626', marginLeft: 3 }}>*</span>}</label>
    {children}
    {hint && <div className="cl-help">{hint}</div>}
  </div>
);

/* =================== TABS =================== */
const Tabs = ({ items, active }) => (
  <div className="cl-tabs">
    {items.map((it, i) => {
      const label = typeof it === 'string' ? it : it.label;
      const count = typeof it === 'object' ? it.count : null;
      return (
        <div key={i} className={`cl-tab${active === label ? ' active' : ''}`}>
          {label}
          {count != null && <span className="cl-tab-count">{count}</span>}
        </div>
      );
    })}
  </div>
);

/* =================== TIMELINE =================== */
const Timeline = ({ items }) => (
  <div className="cl-timeline">
    {items.map((it, i) => (
      <div className="cl-tl-item" key={i}>
        <div className={`cl-tl-dot ${it.tone || ''}`}></div>
        <div className="cl-tl-time">{it.time}</div>
        <div className="cl-tl-title">{it.title}</div>
        {it.body && <div className="cl-tl-body">{it.body}</div>}
      </div>
    ))}
  </div>
);

/* =================== APP SHELL =================== */
const AppShell = ({ active, role, crumbs, title, subtitle, actions, children, page = true }) => {
  const nav = useNav();
  const effectiveRole = nav.role || role;
  return (
  <div className="cl-app">
    <Sidebar active={active} role={effectiveRole} />
    <div className="cl-main">
      <Topbar role={effectiveRole} />
      {page && (
        <div className="cl-page">
          {crumbs && <Breadcrumbs items={crumbs} />}
          {(title || actions) && (
            <div className="cl-page-head">
              <div>
                {title && <div className="cl-page-title">{title}</div>}
                {subtitle && <div className="cl-page-sub">{subtitle}</div>}
              </div>
              {actions && <div className="cl-page-actions">{actions}</div>}
            </div>
          )}
          {children}
        </div>
      )}
      {!page && children}
    </div>
  </div>
  );
};

/* =================== MINI CHARTS (svg) =================== */
const Sparkline = ({ data = [4, 6, 5, 8, 7, 10, 9, 12, 11, 14, 13, 16], color = "#0E5BFF", height = 40, fill = true }) => {
  const w = 140, h = height, pad = 4;
  const max = Math.max(...data), min = Math.min(...data);
  const stepX = (w - pad * 2) / (data.length - 1);
  const sy = (v) => h - pad - ((v - min) / (max - min || 1)) * (h - pad * 2);
  const pts = data.map((v, i) => `${pad + i * stepX},${sy(v)}`).join(' ');
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      {fill && <polygon points={`${pad},${h - pad} ${pts} ${w - pad},${h - pad}`} fill={color} opacity="0.12" />}
      <polyline points={pts} fill="none" stroke={color} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
};

const BarChart = ({ data = [], color = "#0E5BFF", height = 120, labels = [] }) => {
  const w = 320, h = height, pad = 18;
  const max = Math.max(...data);
  const bw = (w - pad * 2) / data.length;
  return (
    <svg width="100%" height={h} viewBox={`0 0 ${w} ${h}`}>
      {[0, 0.25, 0.5, 0.75, 1].map((t, i) => (
        <line key={i} x1={pad} x2={w - pad} y1={pad + (h - pad * 2) * t} y2={pad + (h - pad * 2) * t} stroke="#EEF2F4" strokeWidth="1" />
      ))}
      {data.map((v, i) => {
        const bh = (v / max) * (h - pad * 2);
        return <rect key={i} x={pad + i * bw + 4} y={h - pad - bh} width={bw - 8} height={bh} fill={color} opacity={i === data.length - 1 ? 1 : 0.7} rx="2" />;
      })}
      {labels.map((l, i) => (
        <text key={i} x={pad + i * bw + bw / 2} y={h - 4} fontSize="9" fill="#8392A7" textAnchor="middle">{l}</text>
      ))}
    </svg>
  );
};

const DonutChart = ({ segments = [], size = 130, thickness = 22, total }) => {
  const r = (size - thickness) / 2, c = size / 2;
  const sum = total ?? segments.reduce((a, b) => a + b.value, 0);
  let offset = 0;
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <circle cx={c} cy={c} r={r} stroke="#EEF2F4" strokeWidth={thickness} fill="none" />
      {segments.map((s, i) => {
        const len = (s.value / sum) * 2 * Math.PI * r;
        const circ = 2 * Math.PI * r;
        const dash = `${len} ${circ - len}`;
        const el = (
          <circle key={i} cx={c} cy={c} r={r} stroke={s.color} strokeWidth={thickness}
            fill="none" strokeDasharray={dash}
            strokeDashoffset={-offset}
            transform={`rotate(-90 ${c} ${c})`} />
        );
        offset += len;
        return el;
      })}
    </svg>
  );
};

Object.assign(window, {
  StatusBadge, Sidebar, Topbar, Breadcrumbs, MetricCard, FilterBar,
  DataTable, RowActions, FormSection, Field, Tabs, Timeline, AppShell,
  Sparkline, BarChart, DonutChart, BrandMark,
  NavCtx, useNav, ROLE_HIDDEN, SIDE_NAV_BY_ROLE, ROLE_USER,
});
