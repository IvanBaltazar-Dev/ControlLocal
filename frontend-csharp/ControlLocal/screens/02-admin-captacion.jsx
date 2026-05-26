// Screens 5-9: Gestión de brokers, agentes, catálogos, propietarios, locales comerciales

const ScreenBrokers = () => (
  <AppShell active="Brokers" role="Broker administrador"
    crumbs={["ControlLocal", "Administración", "Brokers"]}
    title="Gestión de brokers" subtitle="8 brokers · 6 activos · 2 inactivos — administración a cargo del broker administrador"
    actions={<button className="cl-btn primary"><Icon name="plus" size={13} /> Nuevo broker</button>}>
    <FilterBar search="Buscar broker por nombre o correo…">
      <select className="cl-select"><option>Todos los estados</option><option>Activo</option><option>Inactivo</option></select>
      <select className="cl-select"><option>Todas las zonas</option><option>Lima Norte</option><option>Lima Centro</option><option>Lima Sur</option></select>
    </FilterBar>
    <DataTable
      columns={[
        { label: "Broker", render: r => <div className="cl-flex"><span className="cl-avatar" style={{ width: 28, height: 28, fontSize: 11 }}>{r.ini}</span><div><div style={{ fontWeight: 600 }}>{r.name}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.email}</div></div></div> },
        { label: "Documento", render: r => <span className="mono">{r.dni}</span> },
        { label: "Zona", key: "zone" },
        { label: "Agentes a cargo", render: r => <span><b>{r.agents}</b> <span className="cl-muted">activos</span></span> },
        { label: "Captac. del mes", render: r => <span>{r.cap}</span> },
        { label: "Estado", render: r => <StatusBadge label={r.status} /> },
        { label: "", render: () => <RowActions items={["view","edit","more"]} /> },
      ]}
      rows={[
        { ini: "RS", name: "Ricardo Salas", email: "rsalas@controllocal.pe", dni: "08 412 991", zone: "Lima Centro / Sur", agents: 7, cap: 38, status: "Activo" },
        { ini: "MQ", name: "Mariana Quintero", email: "mquintero@controllocal.pe", dni: "10 552 408", zone: "Lima Norte", agents: 6, cap: 31, status: "Activo" },
        { ini: "FA", name: "Felipe Andrade", email: "fandrade@controllocal.pe", dni: "09 718 220", zone: "Lima Este", agents: 5, cap: 27, status: "Activo" },
        { ini: "SR", name: "Sandra Ríos", email: "srios@controllocal.pe", dni: "07 823 145", zone: "Callao / Norte", agents: 4, cap: 22, status: "Activo" },
        { ini: "LV", name: "Luis Velarde", email: "lvelarde@controllocal.pe", dni: "11 042 776", zone: "Lima Sur", agents: 3, cap: 14, status: "Activo" },
        { ini: "ET", name: "Elena Tafur", email: "etafur@controllocal.pe", dni: "08 998 220", zone: "Lima Moderna", agents: 0, cap: 0, status: "Inactivo" },
      ]} />
    <Pagination total={8} />
  </AppShell>
);

const Pagination = ({ total = 24 }) => (
  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 12, fontSize: 12, color: '#4A5A6E' }}>
    <span>Mostrando 1–8 de {total} resultados</span>
    <div style={{ display: 'flex', gap: 4 }}>
      <button className="cl-btn sm" disabled><Icon name="chevronLeft" size={12} /></button>
      <button className="cl-btn sm primary">1</button>
      <button className="cl-btn sm">2</button>
      <button className="cl-btn sm">3</button>
      <button className="cl-btn sm">…</button>
      <button className="cl-btn sm">{Math.ceil(total / 8)}</button>
      <button className="cl-btn sm"><Icon name="chevronRight" size={12} /></button>
    </div>
  </div>
);

// Pantalla del broker (perfil normal): administra ESTRICTAMENTE a los
// agentes que están bajo su supervisión. Puede registrar, actualizar,
// desactivar y consultar agentes — nunca agentes de otro broker.
const ScreenAgents = () => (
  <AppShell role="Broker"
    crumbs={["ControlLocal", "Mi equipo", "Mis agentes"]}
    title="Mis agentes inmobiliarios"
    subtitle="Equipo bajo tu supervisión directa · 7 activos · 1 inactivo"
    actions={<button className="cl-btn primary"><Icon name="plus" size={13} /> Registrar agente</button>}>
    <div className="cl-alert blue" style={{ marginBottom: 14 }}>
      <Icon name="info" size={15} />
      <span>Al registrar un agente se crea automáticamente una <b>relación activa de supervisión</b> entre tú y el agente. Solo el broker administrador puede reasignar agentes a otro broker.</span>
    </div>
    <FilterBar search="Buscar agente por nombre, DNI o correo…">
      <select className="cl-select"><option>Todos los estados</option><option>Activo</option><option>Inactivo</option></select>
      <select className="cl-select"><option>Todas las zonas</option><option>Lima Centro</option><option>Lima Sur</option></select>
    </FilterBar>
    <DataTable
      columns={[
        { label: "Agente", render: r => <div className="cl-flex"><span className="cl-avatar" style={{ width: 28, height: 28, fontSize: 11, background: r.color }}>{r.ini}</span><div><div style={{ fontWeight: 600 }}>{r.name}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.email}</div></div></div> },
        { label: "Documento", render: r => <span className="mono">{r.dni}</span> },
        { label: "Zona", key: "zone" },
        { label: "Supervisión desde", render: r => <span className="cl-muted" style={{ fontSize: 12 }}>{r.since}</span> },
        { label: "Captaciones", render: r => <span><b>{r.cap}</b></span> },
        { label: "Oportunidades", render: r => <span><b>{r.op}</b></span> },
        { label: "Estado", render: r => <StatusBadge label={r.status} /> },
        { label: "", render: () => <RowActions items={["view","edit","more"]} /> },
      ]}
      rows={[
        { ini: "VM", color: "#0E5BFF", name: "Valentina Mora",   email: "vmora@controllocal.pe",   dni: "45 893 211", zone: "Lima Centro",  since: "14 Feb 2024", cap: 14, op: 8, status: "Activo" },
        { ini: "CV", color: "#16A34A", name: "Carolina Vega",     email: "cvega@controllocal.pe",   dni: "46 220 411", zone: "Lima Sur",     since: "22 Mar 2024", cap:  9, op: 5, status: "Activo" },
        { ini: "AT", color: "#DC2626", name: "Andrea Torres",     email: "atorres@controllocal.pe", dni: "47 412 008", zone: "Callao",       since: "06 May 2024", cap:  6, op: 3, status: "Activo" },
        { ini: "PR", color: "#F59E0B", name: "Paola Reyes",       email: "preyes@controllocal.pe",  dni: "45 002 884", zone: "Lima Centro",  since: "18 Jul 2024", cap:  7, op: 4, status: "Activo" },
        { ini: "JM", color: "#0284C7", name: "Jorge Marín",        email: "jmarin@controllocal.pe",  dni: "44 728 101", zone: "Lima Moderna", since: "03 Sep 2024", cap:  5, op: 2, status: "Activo" },
        { ini: "NS", color: "#8392A7", name: "Natalia Solano",    email: "nsolano@controllocal.pe", dni: "46 998 220", zone: "Lima Sur",     since: "19 Oct 2024", cap:  0, op: 0, status: "Inactivo" },
      ]} />
    <Pagination total={8} />
  </AppShell>
);

/* =================== REASIGNACIÓN DE AGENTES (Admin) =================== */
// Solo el broker administrador puede mover agentes entre brokers. Se usa
// puntualmente para intervención (no es flujo normal). El broker normal NO
// tiene acceso a esta pantalla.
const ScreenReasignarAgentes = () => {
  const { navigate } = useNav();
  return (
  <AppShell role="Broker administrador"
    crumbs={["ControlLocal", "Administración", "Reasignar agentes"]}
    title="Reasignación de agentes entre brokers"
    subtitle="Acción de intervención — solo cuando sea necesario. Cada movimiento queda registrado en la auditoría global."
    actions={<button className="cl-btn"><Icon name="history" size={13} /> Ver histórico</button>}>
    <div className="cl-alert amber" style={{ marginBottom: 14 }}>
      <Icon name="alert" size={15} />
      <span><b>Acción excepcional.</b> La gestión normal de agentes la realiza cada broker sobre su propio equipo. El administrador interviene únicamente cuando hay un motivo justificado (cese de broker, redistribución de zona, queja escalada).</span>
    </div>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 14 }}>
      <div className="cl-stack">
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">1 · Selecciona el agente a reasignar</div></div>
          <div className="cl-card-body">
            <FilterBar search="Buscar agente por nombre o DNI…">
              <select className="cl-select"><option>Broker actual</option><option>Ricardo Salas</option><option>Mariana Quintero</option></select>
              <select className="cl-select"><option>Zona</option></select>
            </FilterBar>
            <DataTable
              columns={[
                { label: "", render: r => <input type="radio" name="agent" defaultChecked={r.sel} /> },
                { label: "Agente", render: r => <div className="cl-flex"><span className="cl-avatar" style={{ width:26, height:26, fontSize:10 }}>{r.ini}</span><div><div style={{ fontWeight: 600 }}>{r.name}</div><div className="cl-muted" style={{ fontSize: 11 }}>DNI {r.dni}</div></div></div> },
                { label: "Broker actual", key: "from" },
                { label: "Supervisión desde", render: r => <span className="cl-muted" style={{ fontSize: 12 }}>{r.since}</span> },
                { label: "Operaciones abiertas", render: r => <span><b>{r.ops}</b> op. · <b>{r.cap}</b> cap.</span> },
                { label: "Estado", render: r => <StatusBadge label={r.status} /> },
              ]}
              rows={[
                { sel: true,  ini: "DR", name: "Daniel Romero",  dni: "44 102 776", from: "M. Quintero",  since: "22 Feb 2024", cap: 11, ops: 6, status: "Activo" },
                { sel: false, ini: "ML", name: "Matías León",     dni: "45 778 002", from: "F. Andrade",   since: "06 May 2024", cap:  8, ops: 4, status: "Activo" },
                { sel: false, ini: "JM", name: "Jorge Marín",     dni: "44 728 101", from: "R. Salas",     since: "03 Sep 2024", cap:  5, ops: 2, status: "Activo" },
                { sel: false, ini: "AT", name: "Andrea Torres",   dni: "47 412 008", from: "S. Ríos",      since: "06 May 2024", cap:  6, ops: 3, status: "Activo" },
              ]} />
          </div>
        </div>

        <FormSection num="2" title="Destino y motivo" sub="La reasignación actualiza la relación de supervisión y mueve las operaciones abiertas al nuevo broker">
          <div className="cl-grid c2">
            <Field label="Broker destino" required>
              <select defaultValue="rs">
                <option value="rs">Ricardo Salas — Lima Centro / Sur</option>
                <option>Felipe Andrade — Lima Este</option>
                <option>Sandra Ríos — Callao / Norte</option>
              </select>
            </Field>
            <Field label="Fecha efectiva" required><input defaultValue="24/05/2026" /></Field>
            <Field label="Tipo de intervención" required>
              <select>
                <option>Cese del broker origen</option>
                <option>Redistribución de zona</option>
                <option>Queja o solicitud escalada</option>
                <option>Otro — detallar en motivo</option>
              </select>
            </Field>
            <Field label="Mover operaciones abiertas">
              <select defaultValue="si">
                <option value="si">Sí — transferir captaciones y oportunidades activas</option>
                <option>No — cerrar primero las pendientes</option>
              </select>
            </Field>
          </div>
          <div style={{ marginTop: 12 }}>
            <Field label="Motivo (queda en auditoría)" required>
              <textarea rows={3} defaultValue="Cese definitivo del broker M. Quintero — 22/05/2026. Sus 6 agentes se redistribuyen: 3 a R. Salas y 3 a F. Andrade. Daniel Romero se transfiere a R. Salas por afinidad de zona (Lima Centro)." />
            </Field>
          </div>
        </FormSection>
      </div>

      <div className="cl-stack">
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Resumen</div></div>
          <div className="cl-card-body">
            <dl className="cl-kv">
              <dt>Agente</dt><dd>Daniel Romero</dd>
              <dt>Origen</dt><dd>M. Quintero</dd>
              <dt>Destino</dt><dd>R. Salas</dd>
              <dt>Operaciones</dt><dd>6 op. + 11 cap.</dd>
              <dt>Fecha</dt><dd>24 May 2026</dd>
              <dt>Tipo</dt><dd>Cese del broker origen</dd>
            </dl>
            <div className="cl-divider"></div>
            <button className="cl-btn primary" style={{ width: '100%', justifyContent: 'center' }}>
              <Icon name="check" size={13} /> Confirmar reasignación
            </button>
            <button onClick={() => navigate('brokers')} className="cl-btn ghost" style={{ width: '100%', marginTop: 6, justifyContent: 'center', color: '#4A5A6E' }}>Cancelar</button>
          </div>
        </div>
        <div className="cl-alert blue">
          <Icon name="info" size={15} />
          <span>Esta acción queda registrada en la <b>auditoría global</b> con usuario, fecha y motivo. El broker origen y destino reciben notificación.</span>
        </div>
      </div>
    </div>
  </AppShell>
  );
};

/* =================== CATÁLOGOS =================== */
const CatalogCard = ({ title, items }) => (
  <div className="cl-card">
    <div className="cl-card-head"><div className="cl-card-title">{title}</div><button className="cl-btn sm ghost" style={{ color: '#0E5BFF' }}><Icon name="plus" size={12} /> Agregar</button></div>
    <div className="cl-card-body" style={{ padding: 0 }}>
      {items.map((it, i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', padding: '10px 16px', borderTop: i === 0 ? 0 : '1px solid #EEF2F4', gap: 10 }}>
          {it.badge && <StatusBadge label={it.label} tone={it.badge} />}
          {!it.badge && <span style={{ fontSize: 13 }}>{it.label}</span>}
          <span className="cl-muted" style={{ fontSize: 11.5, marginLeft: 'auto' }}>{it.use}</span>
          <button className="cl-btn sm ghost" style={{ padding: 4 }}><Icon name="edit" size={12} /></button>
        </div>
      ))}
    </div>
  </div>
);

const ScreenCatalogs = () => (
  <AppShell role="Broker administrador"
    crumbs={["ControlLocal", "Administración", "Catálogos del sistema"]}
    title="Catálogos del sistema"
    subtitle="Valores maestros utilizados por los módulos comerciales. Edición restringida al rol administrador.">
    <div className="cl-tabs">
      <div className="cl-tab active">Estados</div>
      <div className="cl-tab">Canales de contacto</div>
      <div className="cl-tab">Tipos de documento</div>
      <div className="cl-tab">Resultados de interacción</div>
      <div className="cl-tab">Motivos de no continuidad</div>
    </div>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
      <CatalogCard title="Estados de captación" items={[
        { label: "Pendiente", badge: "amber", use: "Usado en 4 módulos" },
        { label: "Activa", badge: "green", use: "Usado en 6 módulos" },
        { label: "Observada", badge: "amber", use: "Usado en 3 módulos" },
        { label: "Rechazada", badge: "red", use: "Usado en 2 módulos" },
        { label: "Cerrada", badge: "gray", use: "Usado en 2 módulos" },
      ]} />
      <CatalogCard title="Estados de oportunidad" items={[
        { label: "Abierta", badge: "blue", use: "Estado inicial" },
        { label: "En seguimiento", badge: "blue", use: "Tras 1ª interacción" },
        { label: "Solicitud creada", badge: "info", use: "Tras crear solicitud" },
        { label: "Cerrada exitosa", badge: "green", use: "Cierre favorable" },
        { label: "Cerrada no continúa", badge: "gray", use: "Cliente desiste" },
        { label: "Cerrada no favorable", badge: "red", use: "Rechazo del broker" },
      ]} />
      <CatalogCard title="Estados de solicitud" items={[
        { label: "Registrada", badge: "gray", use: "Recién creada" },
        { label: "En revisión", badge: "amber", use: "Documentos en curso" },
        { label: "Observada", badge: "amber", use: "Con observaciones" },
        { label: "Aprobada", badge: "green", use: "Lista para cierre" },
        { label: "Rechazada", badge: "red", use: "Evaluación negativa" },
      ]} />
      <CatalogCard title="Estados de documento" items={[
        { label: "Pendiente", badge: "gray", use: "Por cargar" },
        { label: "Observado", badge: "amber", use: "Requiere corrección" },
        { label: "Aprobado", badge: "green", use: "Validado por broker" },
        { label: "Rechazado", badge: "red", use: "No subsanable" },
      ]} />
    </div>
  </AppShell>
);

/* =================== PROPIETARIOS =================== */
// Propietarios — registrados EXCLUSIVAMENTE por el agente inmobiliario.
// Ni el broker ni el broker administrador crean propietarios.
const ScreenOwners = () => (
  <AppShell role="Agente inmobiliario"
    crumbs={["ControlLocal", "Captación", "Propietarios"]}
    title="Propietarios" subtitle="Propietarios que registré para vincular a captaciones"
    actions={<><button className="cl-btn"><Icon name="upload" size={13} /> Importar</button><button className="cl-btn primary"><Icon name="plus" size={13} /> Registrar propietario</button></>}>
    <FilterBar search="Buscar por nombre, razón social, documento…">
      <select className="cl-select"><option>Tipo de persona</option><option>Persona natural</option><option>Persona jurídica</option></select>
      <select className="cl-select"><option>Estado</option><option>Activo</option><option>Inactivo</option></select>
    </FilterBar>
    <DataTable
      columns={[
        { label: "Propietario", render: r => <div><div style={{ fontWeight: 600 }}>{r.name}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.type}</div></div> },
        { label: "Documento", render: r => <span className="mono">{r.doc}</span> },
        { label: "Teléfono", render: r => <span className="mono">{r.phone}</span> },
        { label: "Correo", render: r => <span className="cl-muted">{r.email}</span> },
        { label: "Locales", render: r => <span><b>{r.locales}</b></span> },
        { label: "Estado", render: r => <StatusBadge label={r.status} /> },
        { label: "", render: () => <RowActions /> },
      ]}
      rows={[
        { name: "Inmobiliaria Pacífico S.A.C.", type: "Persona jurídica · RUC", doc: "20 553 102 884", phone: "+51 1 432 8800", email: "contacto@pacifico.com.pe", locales: 6, status: "Activo" },
        { name: "Carlos Mendoza Rivera", type: "Persona natural · DNI", doc: "08 412 991", phone: "+51 998 220 411", email: "cmendoza@gmail.com", locales: 2, status: "Activo" },
        { name: "Grupo Bermúdez E.I.R.L.", type: "Persona jurídica · RUC", doc: "20 502 998 110", phone: "+51 1 222 1108", email: "admin@bermudez.pe", locales: 4, status: "Activo" },
        { name: "Ana Lucía Pereyra", type: "Persona natural · DNI", doc: "09 778 002", phone: "+51 987 412 008", email: "alpereyra@hotmail.com", locales: 1, status: "Activo" },
        { name: "Comercial Andina S.R.L.", type: "Persona jurídica · RUC", doc: "20 471 220 008", phone: "+51 1 718 4400", email: "ventas@andina.com.pe", locales: 3, status: "Inactivo" },
        { name: "Roberto Linares Cruz", type: "Persona natural · DNI", doc: "07 823 145", phone: "+51 991 552 008", email: "rlinares@yahoo.com", locales: 1, status: "Activo" },
      ]} />
    <Pagination total={86} />
  </AppShell>
);

/* =================== LOCALES COMERCIALES =================== */
const LocalCard = ({ data }) => (
  <div style={{ background: '#fff', border: '1px solid #DDE5E8', borderRadius: 10, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
    <div style={{ aspectRatio: '16/10', background: `repeating-linear-gradient(135deg, #E8EDF3 0 8px, #F1F4F8 8px 16px)`, position: 'relative' }}>
      <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center' }}>
        <div style={{ fontFamily: 'ui-monospace, Menlo, monospace', fontSize: 10, color: '#8392A7', background: 'rgba(255,255,255,0.85)', padding: '4px 8px', borderRadius: 4 }}>foto del local</div>
      </div>
      <div style={{ position: 'absolute', top: 10, left: 10 }}><StatusBadge label={data.status} /></div>
      <div style={{ position: 'absolute', top: 10, right: 10, background: 'rgba(11,31,51,0.78)', color: '#fff', fontSize: 11, padding: '3px 8px', borderRadius: 4, fontFamily: 'monospace' }}>{data.code}</div>
    </div>
    <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div style={{ fontWeight: 600, fontSize: 13.5 }}>{data.address}</div>
      <div style={{ fontSize: 11.5, color: '#8392A7', display: 'flex', gap: 6, alignItems: 'center' }}>
        <Icon name="mapPin" size={12} /> {data.district}
      </div>
      <div style={{ display: 'flex', gap: 10, fontSize: 11.5, color: '#4A5A6E', marginTop: 4 }}>
        <span><b>{data.area}</b> m²</span>
        <span>·</span>
        <span>{data.rubro}</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', marginTop: 8 }}>
        <div>
          <div style={{ fontSize: 11, color: '#8392A7' }}>Precio referencial</div>
          <div style={{ fontSize: 17, fontWeight: 600 }}>{data.price} <span style={{ fontSize: 11, color: '#8392A7' }}>USD / mes</span></div>
        </div>
        <button className="cl-btn sm primary">Ver detalle</button>
      </div>
      <div style={{ borderTop: '1px solid #EEF2F4', marginTop: 8, paddingTop: 8, fontSize: 11.5, color: '#4A5A6E' }}>
        Propietario · <b>{data.owner}</b>
      </div>
    </div>
  </div>
);

// Locales comerciales — registrados EXCLUSIVAMENTE por el agente.
const ScreenLocales = () => (
  <AppShell role="Agente inmobiliario"
    crumbs={["ControlLocal", "Captación", "Locales comerciales"]}
    title="Locales comerciales" subtitle="Locales que registré · alta del local, propietario y ficha de captación"
    actions={<><button className="cl-btn"><Icon name="layers" size={13} /> Vista tabla</button><button className="cl-btn primary"><Icon name="plus" size={13} /> Registrar local</button></>}>
    <FilterBar search="Buscar por dirección, código, propietario…">
      <select className="cl-select"><option>Distrito</option><option>Miraflores</option><option>San Isidro</option><option>Surco</option></select>
      <select className="cl-select"><option>Rango de precio</option><option>USD 500 – 1500</option><option>USD 1500 – 3000</option><option>USD 3000+</option></select>
      <select className="cl-select"><option>Metraje</option><option>{`< 50 m²`}</option><option>50–100 m²</option><option>100–200 m²</option></select>
      <select className="cl-select"><option>Rubro</option><option>Restaurante</option><option>Moda</option><option>Servicios</option></select>
      <select className="cl-select"><option>Estado</option><option>Disponible</option><option>En captación</option></select>
    </FilterBar>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 14 }}>
      {[
        { code: "LC-0218", status: "Activa", address: "Av. La Marina 245", district: "San Miguel, Lima", area: 120, rubro: "Restaurante / Café", price: "2 800", owner: "Inmobiliaria Pacífico" },
        { code: "LC-0226", status: "Activa", address: "Calle Schell 412, of. 1", district: "Miraflores, Lima", area: 68, rubro: "Moda / Boutique", price: "1 950", owner: "C. Mendoza" },
        { code: "LC-0231", status: "Pendiente", address: "Av. Petit Thouars 1875", district: "Jesús María, Lima", area: 95, rubro: "Servicios", price: "1 600", owner: "Grupo Bermúdez" },
        { code: "LC-0234", status: "Activa", address: "Jr. Berlín 230", district: "Miraflores, Lima", area: 52, rubro: "Café / Postres", price: "1 450", owner: "A. Pereyra" },
        { code: "LC-0238", status: "Observada", address: "Av. Aviación 4012", district: "San Borja, Lima", area: 180, rubro: "Retail", price: "3 600", owner: "Comercial Andina" },
        { code: "LC-0242", status: "Activa", address: "Av. Salaverry 2120", district: "Jesús María, Lima", area: 88, rubro: "Servicios médicos", price: "2 200", owner: "R. Linares" },
      ].map((d, i) => <LocalCard key={i} data={d} />)}
    </div>
    <Pagination total={248} />
  </AppShell>
);

Object.assign(window, { ScreenBrokers, ScreenAgents, ScreenReasignarAgentes, ScreenCatalogs, ScreenOwners, ScreenLocales });
