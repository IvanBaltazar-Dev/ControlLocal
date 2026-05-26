// Screens 17-22: Interacciones, Visitas/agenda, Solicitudes, Documentos, Evaluación, Cierre

const ScreenInteracciones = () => (
  <AppShell role="Agente inmobiliario"
    crumbs={["ControlLocal", "Oportunidades", "OP-1098", "Interacciones"]}
    title="Interacciones comerciales"
    subtitle="Registro de todas las comunicaciones con el cliente — vinculadas a OP-1098"
    actions={<button className="cl-btn primary"><Icon name="plus" size={13} /> Nueva interacción</button>}>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 12, marginBottom: 14 }}>
      <MetricCard icon="activity" label="Total interacciones" value="38" tone="blue" />
      <MetricCard icon="phone" label="Llamadas" value="18" tone="info" />
      <MetricCard icon="mail" label="Correos" value="12" tone="navy" />
      <MetricCard icon="check" label="Con resultado positivo" value="22" tone="green" />
    </div>
    <FilterBar search="Buscar por observación, canal, agente…">
      <select className="cl-select"><option>Canal</option><option>Llamada</option><option>Correo</option><option>WhatsApp</option><option>Presencial</option></select>
      <select className="cl-select"><option>Resultado</option><option>Interesado</option><option>Sin contacto</option></select>
      <select className="cl-select"><option>Oportunidad</option><option>OP-1098</option></select>
    </FilterBar>
    <DataTable
      columns={[
        { label: "Fecha", render: r => <div><div style={{ fontWeight: 500, fontSize: 12 }}>{r.date}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.time}</div></div> },
        { label: "Canal", render: r => <div className="cl-flex" style={{ gap: 6 }}><Icon name={r.icon} size={13} color="#8392A7" /><span style={{ fontSize: 12.5 }}>{r.channel}</span></div> },
        { label: "Oportunidad", render: r => <span className="mono" style={{ color: '#0E5BFF' }}>{r.op}</span> },
        { label: "Cliente", key: "client" },
        { label: "Agente", key: "agent" },
        { label: "Resultado", render: r => <StatusBadge label={r.result} tone={r.tone} /> },
        { label: "Observaciones", render: r => <span style={{ fontSize: 12, color: '#4A5A6E' }}>{r.note}</span> },
        { label: "", render: () => <RowActions items={["view","edit"]} /> },
      ]}
      rows={[
        { date: "24 May", time: "11:00", channel: "Llamada", icon: "phone", op: "OP-1098", client: "Inversiones Trébol", agent: "V. Mora", result: "Interesado", tone: "green", note: "Confirma visita 16:00, pide plano" },
        { date: "24 May", time: "09:42", channel: "Correo", icon: "mail", op: "OP-1085", client: "Café Lima", agent: "V. Mora", result: "Solicita info", tone: "blue", note: "Quiere comparar dos locales similares" },
        { date: "23 May", time: "17:30", channel: "WhatsApp", icon: "activity", op: "OP-1094", client: "Boutique Lila", agent: "V. Mora", result: "Interesado", tone: "green", note: "Confirma datos para solicitud" },
        { date: "23 May", time: "10:14", channel: "Presencial", icon: "users", op: "OP-1089", client: "Bodegas del Norte", agent: "D. Romero", result: "Sin contacto", tone: "amber", note: "Visita oficina, cliente no se encontraba" },
        { date: "22 May", time: "16:08", channel: "Llamada", icon: "phone", op: "OP-1085", client: "Café Lima", agent: "V. Mora", result: "Interesado", tone: "green", note: "Reagenda visita para 25/05" },
        { date: "22 May", time: "12:00", channel: "Correo", icon: "mail", op: "OP-1077", client: "Carla Espinoza", agent: "C. Vega", result: "No interesado", tone: "red", note: "Cliente desistió, no continúa" },
      ]} />
    <Pagination total={38} />
  </AppShell>
);

/* =================== AGENDA Y VISITAS =================== */
const CalendarMonth = () => {
  const days = [];
  const start = 28; // Sun before
  for (let i = 0; i < 35; i++) {
    const d = start + i - 28;
    let n, prev = false, next = false;
    if (d < 1) { n = 30 + d; prev = true; }
    else if (d > 31) { n = d - 31; next = true; }
    else n = d;
    days.push({ n, prev, next, d });
  }
  const events = {
    5: [['Visita · Boutique Lila','blue']],
    8: [['Visita · Café Lima','green'], ['Llamada · Trébol','info']],
    12: [['Visita reprogramada','amber']],
    15: [['Visita · Bodegas Norte','blue']],
    18: [['Visita · 2 programadas','blue']],
    22: [['Visita realizada · Café Lima','green']],
    24: [['Visita · Trébol 16:00','blue'], ['Visita 2da · Café Lima','blue']],
    26: [['Visita · Bodegas Norte','blue']],
    28: [['Cierre OP-1098','green']],
  };
  return (
    <div className="cl-card">
      <div className="cl-card-head">
        <div className="cl-flex" style={{ gap: 10 }}>
          <button className="cl-btn sm"><Icon name="chevronLeft" size={13} /></button>
          <div className="cl-card-title">Mayo 2026</div>
          <button className="cl-btn sm"><Icon name="chevronRight" size={13} /></button>
        </div>
        <div className="cl-flex" style={{ gap: 6 }}>
          <button className="cl-btn sm">Hoy</button>
          <select className="cl-select" style={{ height: 30 }}><option>Mes</option><option>Semana</option><option>Día</option></select>
        </div>
      </div>
      <div className="cl-card-body" style={{ padding: 0 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7,1fr)', background: '#FAFBFD', borderBottom: '1px solid #DDE5E8' }}>
          {['Dom','Lun','Mar','Mié','Jue','Vie','Sáb'].map(d => (
            <div key={d} style={{ padding: '8px 10px', fontSize: 11, color: '#8392A7', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>{d}</div>
          ))}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7,1fr)' }}>
          {days.map((d, i) => {
            const ev = events[d.n] || [];
            const today = !d.prev && !d.next && d.n === 24;
            return (
              <div key={i} style={{
                minHeight: 92, padding: 6, borderRight: i % 7 < 6 ? '1px solid #EEF2F4' : 0,
                borderBottom: i < 28 ? '1px solid #EEF2F4' : 0,
                background: today ? '#E8F0FF' : '#fff',
                opacity: d.prev || d.next ? 0.4 : 1,
              }}>
                <div style={{ fontSize: 11, color: today ? '#0E5BFF' : '#4A5A6E', fontWeight: today ? 700 : 500, marginBottom: 4 }}>{d.n}</div>
                {ev.slice(0, 2).map((e, j) => (
                  <div key={j} style={{
                    fontSize: 10.5, padding: '2px 6px', borderRadius: 3,
                    background: { blue: '#E8F0FF', green: '#E6F6EC', amber: '#FEF3DC', info: '#E0F1FA' }[e[1]],
                    color: { blue: '#0a48cc', green: '#0d7a36', amber: '#95630a', info: '#02669c' }[e[1]],
                    marginBottom: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                  }}>{e[0]}</div>
                ))}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

const ScreenVisitas = () => (
  <AppShell role="Agente inmobiliario"
    crumbs={["ControlLocal", "Comercial", "Visitas"]}
    title="Visitas"
    subtitle="Visitas programadas a locales — vinculadas a oportunidades comerciales"
    actions={<><button className="cl-btn"><Icon name="refresh" size={13} /> Sincronizar</button><button className="cl-btn primary"><Icon name="plus" size={13} /> Programar visita</button></>}>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 12, marginBottom: 14 }}>
      <MetricCard icon="calendar" label="Programadas" value="14" tone="blue" />
      <MetricCard icon="check" label="Realizadas" value="42" tone="green" />
      <MetricCard icon="refresh" label="Reprogramadas" value="6" tone="amber" />
      <MetricCard icon="x" label="Canceladas" value="3" tone="red" />
    </div>
    <CalendarMonth />
    <div style={{ marginTop: 14 }} />
    <div className="cl-card">
      <div className="cl-card-head"><div className="cl-card-title">Listado de visitas</div></div>
      <div className="cl-card-body" style={{ padding: 0 }}>
        <DataTable
          columns={[
            { label: "Fecha y hora", render: r => <div><div style={{ fontWeight: 600 }}>{r.date}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.time}</div></div> },
            { label: "Oportunidad", render: r => <span className="mono" style={{ color: '#0E5BFF' }}>{r.op}</span> },
            { label: "Cliente", key: "client" },
            { label: "Local", render: r => <div><div style={{ fontSize: 12.5 }}>{r.local}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.district}</div></div> },
            { label: "Agente", key: "agent" },
            { label: "Estado", render: r => <StatusBadge label={r.status} /> },
            { label: "Resultado", render: r => r.result ? <span style={{ fontSize: 12 }}>{r.result}</span> : <span className="cl-muted">—</span> },
            { label: "", render: () => <RowActions /> },
          ]}
          rows={[
            { date: "24 May 2026", time: "16:00", op: "OP-1098", client: "Inversiones Trébol", local: "Av. La Marina 245", district: "San Miguel", agent: "V. Mora", status: "Programada", result: null },
            { date: "25 May 2026", time: "09:30", op: "OP-1085", client: "Café Lima", local: "Jr. Berlín 230", district: "Miraflores", agent: "V. Mora", status: "Programada", result: null },
            { date: "26 May 2026", time: "10:00", op: "OP-1089", client: "Bodegas del Norte", local: "Av. Petit Thouars 1875", district: "Jesús María", agent: "D. Romero", status: "Programada", result: null },
            { date: "22 May 2026", time: "11:00", op: "OP-1085", client: "Café Lima", local: "Jr. Berlín 230", district: "Miraflores", agent: "V. Mora", status: "Realizada", result: "Cliente conforme · solicita propuesta" },
            { date: "20 May 2026", time: "15:30", op: "OP-1077", client: "Carla Espinoza", local: "Av. Salaverry 2120", district: "Jesús María", agent: "C. Vega", status: "Cancelada", result: "Cliente desistió" },
            { date: "18 May 2026", time: "10:30", op: "OP-1071", client: "Plásticos del Sur", local: "Av. Aviación 4012", district: "San Borja", agent: "M. León", status: "Reprogramada", result: "Cliente solicitó reagendar" },
          ]} />
      </div>
    </div>
  </AppShell>
);

/* =================== SOLICITUDES =================== */
/* =================== SOLICITUDES ===================
   Agente: ve y crea sus solicitudes desde oportunidades con interés.
   Broker: ve solicitudes pendientes de evaluación de SU equipo (bandeja).
   El broker normal NO crea solicitudes, solo evalúa / aprueba / cierra. */
const ScreenSolicitudes = () => {
  const { navigate, role } = useNav();
  const isBroker = role === "Broker";
  return (
  <AppShell role={role || "Agente inmobiliario"}
    crumbs={isBroker
      ? ["ControlLocal", "Bandejas de revisión", "Solicitudes por evaluar"]
      : ["ControlLocal", "Comercial", "Solicitudes de alquiler"]}
    title={isBroker ? "Solicitudes por evaluar" : "Solicitudes de alquiler"}
    subtitle={isBroker
      ? "Solicitudes presentadas por agentes bajo tu supervisión — esperan tu decisión"
      : "Formalización de oportunidades comerciales con interés confirmado"}
    actions={isBroker
      ? <button className="cl-btn"><Icon name="download" size={13} /> Exportar</button>
      : <button onClick={() => navigate('oportunidades')} className="cl-btn primary"><Icon name="plus" size={13} /> Nueva solicitud desde oportunidad</button>}>
    {isBroker && (
      <div className="cl-alert blue" style={{ marginBottom: 14 }}>
        <Icon name="info" size={15} />
        <span>Como broker puedes <b>aprobar, observar, rechazar o cerrar</b> solicitudes — nunca crearlas. El registro lo hace el agente, desde una oportunidad con interés confirmado.</span>
      </div>
    )}
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5,1fr)', gap: 12, marginBottom: 14 }}>
      <MetricCard icon="fileText" label="Registradas" value="8" tone="navy" />
      <MetricCard icon="clock" label="En revisión" value="14" tone="amber" />
      <MetricCard icon="alert" label="Observadas" value="4" tone="amber" />
      <MetricCard icon="check" label="Aprobadas" value="22" tone="green" />
      <MetricCard icon="x" label="Rechazadas" value="3" tone="red" />
    </div>
    <FilterBar search="Buscar por código, cliente, local…">
      <select className="cl-select"><option>Estado</option></select>
      <select className="cl-select"><option>Agente</option></select>
      <select className="cl-select"><option>Rango de monto</option></select>
    </FilterBar>
    <DataTable
      columns={[
        { label: "Código", render: r => <div><span className="mono" style={{ color: '#0E5BFF', fontWeight: 600 }}>{r.code}</span><div className="cl-muted mono" style={{ fontSize: 11, marginTop: 2 }}>{r.op}</div></div> },
        { label: "Cliente", key: "client" },
        { label: "Local", render: r => <div><div style={{ fontSize: 12.5 }}>{r.local}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.district}</div></div> },
        { label: "Monto propuesto", render: r => <span><b>USD {r.amount}</b> <span className="cl-muted" style={{ fontSize: 11 }}>/ mes</span></span> },
        { label: "Plazo", render: r => <span>{r.term} meses</span> },
        { label: "Documentos", render: r => <div><span style={{ fontWeight: 600 }}>{r.docs}</span><div style={{ height: 4, background: '#EEF2F4', borderRadius: 2, marginTop: 4 }}><div style={{ width: r.docPct + '%', height: '100%', background: r.docPct === 100 ? '#16A34A' : '#F59E0B', borderRadius: 2 }}></div></div></div> },
        { label: "Registrada", key: "date" },
        { label: "Estado", render: r => <StatusBadge label={r.status} /> },
        { label: "", render: r => (
          <button onClick={() => navigate(r.status === 'Aprobada' || r.docPct === 100 ? 'evaluacion' : 'documentos')}
            className="cl-btn sm primary">
            {r.status === 'Aprobada' ? 'Ver cierre' : r.docPct === 100 ? 'Evaluar' : 'Documentos'}
          </button>
        ) },
      ]}
      rows={[
        { code: "SOL-0425", op: "OP-1094", client: "Boutique Lila", local: "Calle Schell 412", district: "Miraflores", amount: "1 850", term: 24, docs: "6/6", docPct: 100, date: "23 May", status: "En revisión" },
        { code: "SOL-0428", op: "OP-1083", client: "Inversiones Trébol", local: "Av. La Marina 245", district: "San Miguel", amount: "2 750", term: 36, docs: "5/6", docPct: 83, date: "22 May", status: "Observada" },
        { code: "SOL-0430", op: "OP-1085", client: "Café Lima", local: "Jr. Berlín 230", district: "Miraflores", amount: "1 400", term: 24, docs: "4/6", docPct: 66, date: "22 May", status: "Registrada" },
        { code: "SOL-0421", op: "OP-1077", client: "Carla Espinoza", local: "Av. Salaverry 2120", district: "Jesús María", amount: "2 100", term: 24, docs: "6/6", docPct: 100, date: "18 May", status: "Aprobada" },
        { code: "SOL-0415", op: "OP-1071", client: "Plásticos del Sur", local: "Av. Aviación 4012", district: "San Borja", amount: "3 500", term: 36, docs: "6/6", docPct: 100, date: "15 May", status: "Rechazada" },
        { code: "SOL-0418", op: "OP-1068", client: "Restaurantes Bocca", local: "Av. Brasil 2890", district: "Magdalena", amount: "1 950", term: 24, docs: "6/6", docPct: 100, date: "14 May", status: "Aprobada" },
      ]} />
    <Pagination total={51} />
  </AppShell>
  );
};

/* =================== DOCUMENTOS DE SOLICITUD =================== */
const ScreenDocumentos = () => {
  const { navigate } = useNav();
  return (
  <AppShell role="Agente inmobiliario"
    crumbs={["ControlLocal", "Solicitudes", "SOL-0428", "Documentos"]}
    title="Documentos de solicitud · SOL-0428"
    subtitle="Solicitud sobre OP-1083 · Cliente Inversiones Trébol · 5 de 6 documentos cargados"
    actions={<><button onClick={() => navigate('solicitudes')} className="cl-btn"><Icon name="chevronLeft" size={13} /> Volver a solicitudes</button><button className="cl-btn"><Icon name="download" size={13} /> Descargar todos</button><button onClick={() => navigate('evaluacion')} className="cl-btn primary"><Icon name="check" size={13} /> Marcar documentos completos</button></>}>
    <div className="cl-tabs">
      <div className="cl-tab">Resumen</div>
      <div className="cl-tab active">Documentos <span className="cl-tab-count">5/6</span></div>
      <div className="cl-tab">Historial</div>
      <div className="cl-tab">Observaciones</div>
    </div>

    <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 14 }}>
      <div className="cl-stack">
        <div className="cl-alert amber">
          <Icon name="alert" size={15} />
          <span><b>1 documento pendiente y 1 observado.</b> Carga la constancia de RUC actualizada y corrige las observaciones del estado financiero antes de continuar.</span>
        </div>

        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Documentos requeridos</div><span className="cl-muted" style={{ fontSize: 12 }}>Tipo · Estado · Revisado por</span></div>
          <div className="cl-card-body" style={{ padding: 0 }}>
            {[
              { name: "Copia DNI representante legal.pdf", type: "DNI", size: "412 KB", uploaded: "22 May 14:10", status: "Aprobado", note: "Documento legible y vigente", rev: "R. Salas" },
              { name: "Vigencia de poder.pdf", type: "Vigencia de poder", size: "1.1 MB", uploaded: "22 May 14:12", status: "Aprobado", note: "Vigente a 06/2026", rev: "R. Salas" },
              { name: "Estado_financiero_2025.xlsx", type: "Estado financiero", size: "284 KB", uploaded: "22 May 14:30", status: "Observado", note: "Falta firma del contador colegiado y EE.FF. del último año", rev: "R. Salas" },
              { name: "Carta_garantía_solidaria.pdf", type: "Carta garantía", size: "620 KB", uploaded: "23 May 09:14", status: "Aprobado", note: "Garante: Sr. Carlos Trujillo", rev: "R. Salas" },
              { name: "Carta_propuesta_alquiler.pdf", type: "Propuesta", size: "320 KB", uploaded: "23 May 09:18", status: "Aprobado", note: "Monto, plazo y condiciones acordes", rev: "R. Salas" },
              { name: "Constancia_RUC_actualizada.pdf", type: "Constancia RUC", size: "—", uploaded: "—", status: "Pendiente", note: "Documento no cargado", rev: "—" },
            ].map((d, i) => (
              <div key={i} style={{ display: 'flex', gap: 12, padding: '14px 16px', borderTop: i === 0 ? 0 : '1px solid #EEF2F4', alignItems: 'flex-start' }}>
                <div style={{ width: 36, height: 44, borderRadius: 4, background: d.status === 'Pendiente' ? '#F1F4F8' : '#E8F0FF', display: 'grid', placeItems: 'center', flex: '0 0 36px' }}>
                  <Icon name="fileText" size={18} color={d.status === 'Pendiente' ? '#8392A7' : '#0284C7'} />
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{d.name}</div>
                    <StatusBadge label={d.status} />
                  </div>
                  <div className="cl-muted" style={{ fontSize: 11.5, marginTop: 3 }}>{d.type} · {d.size} · cargado {d.uploaded}</div>
                  <div style={{ fontSize: 12, color: '#4A5A6E', marginTop: 6, fontStyle: d.note === 'Documento no cargado' ? 'italic' : 'normal' }}>
                    {d.note}{d.rev !== '—' && <span className="cl-muted"> · revisado por {d.rev}</span>}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 6 }}>
                  {d.status !== 'Pendiente' && <button className="cl-btn sm"><Icon name="eye" size={12} /> Ver</button>}
                  {d.status === 'Pendiente' && <button className="cl-btn sm primary"><Icon name="upload" size={12} /> Cargar</button>}
                  {d.status === 'Observado' && <button className="cl-btn sm warn"><Icon name="refresh" size={12} /> Recargar</button>}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="cl-stack">
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Avance</div></div>
          <div className="cl-card-body">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative', marginBottom: 14 }}>
              <DonutChart segments={[
                { value: 4, color: '#16A34A' },
                { value: 1, color: '#F59E0B' },
                { value: 1, color: '#DDE5E8' },
              ]} size={140} thickness={20} />
              <div style={{ position: 'absolute', textAlign: 'center' }}>
                <div style={{ fontSize: 26, fontWeight: 700, letterSpacing: '-0.02em' }}>5/6</div>
                <div style={{ fontSize: 11, color: '#8392A7' }}>completados</div>
              </div>
            </div>
            {[
              ['#16A34A','Aprobados','4'],
              ['#F59E0B','Observados','1'],
              ['#DDE5E8','Pendientes','1'],
            ].map(([c,n,v],i) => (
              <div key={i} style={{ display:'flex', alignItems:'center', gap:8, padding:'4px 0', fontSize: 12 }}>
                <span style={{ width: 9, height: 9, borderRadius: 2, background: c }}></span>
                <span style={{ flex: 1 }}>{n}</span>
                <b>{v}</b>
              </div>
            ))}
          </div>
        </div>
        <div className="cl-alert blue">
          <Icon name="info" size={15} />
          <span>Al aprobar todos los documentos, la solicitud pasará automáticamente a <b>En revisión</b> por el broker.</span>
        </div>
      </div>
    </div>
  </AppShell>
  );
};

/* =================== EVALUACIÓN DE SOLICITUD =================== */
const ScreenEvaluacion = () => {
  const { navigate } = useNav();
  return (
  <AppShell role="Broker"
    crumbs={["ControlLocal", "Solicitudes por evaluar", "SOL-0425"]}
    title="Evaluación de solicitud · SOL-0425"
    subtitle="Solicitud completa · 6/6 documentos aprobados · pendiente decisión del broker"
    actions={
      <>
        <button onClick={() => navigate('solicitudes')} className="cl-btn"><Icon name="chevronLeft" size={13} /> Volver</button>
        <button className="cl-btn"><Icon name="history" size={13} /> Historial</button>
        <button onClick={() => navigate('solicitudes')} className="cl-btn danger"><Icon name="x" size={13} /> Rechazar</button>
        <button onClick={() => navigate('documentos')} className="cl-btn warn"><Icon name="alert" size={13} /> Solicitar ajustes</button>
        <button onClick={() => navigate('cierre')} className="cl-btn success"><Icon name="check" size={13} /> Aprobar</button>
      </>
    }>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 14 }}>
      <div className="cl-stack">
        <div className="cl-card">
          <div className="cl-card-head">
            <div>
              <div className="cl-card-title">Resumen de la solicitud</div>
              <div className="cl-card-sub">SOL-0425 · OP-1094 · cliente Boutique Lila</div>
            </div>
            <StatusBadge label="En revisión" />
          </div>
          <div className="cl-card-body">
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 18 }}>
              <dl className="cl-kv">
                <dt>Cliente</dt><dd>Boutique Lila</dd>
                <dt>RUC</dt><dd className="mono">20 471 998 110</dd>
                <dt>Representante</dt><dd>Lila Castillo</dd>
                <dt>Rubro</dt><dd>Moda / Boutique</dd>
              </dl>
              <dl className="cl-kv">
                <dt>Local</dt><dd>Calle Schell 412</dd>
                <dt>Captación</dt><dd className="mono" style={{ color: '#0E5BFF' }}>CAP-0226</dd>
                <dt>Propietario</dt><dd>C. Mendoza</dd>
                <dt>Distrito</dt><dd>Miraflores</dd>
              </dl>
              <dl className="cl-kv">
                <dt>Monto propuesto</dt><dd><b>USD 1 850</b> / mes</dd>
                <dt>Garantía</dt><dd>2 meses · USD 3 700</dd>
                <dt>Plazo</dt><dd>24 meses</dd>
                <dt>Inicio tentativo</dt><dd>01 Jul 2026</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Documentación revisada</div><span className="cl-badge green">6/6 aprobados</span></div>
          <div className="cl-card-body" style={{ padding: 0 }}>
            {[
              ['DNI representante legal','Aprobado'],
              ['Vigencia de poder','Aprobado'],
              ['Estado financiero 2025','Aprobado'],
              ['Carta garantía solidaria','Aprobado'],
              ['Constancia RUC','Aprobado'],
              ['Propuesta firmada','Aprobado'],
            ].map((d, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', padding: '10px 16px', borderTop: i === 0 ? 0 : '1px solid #EEF2F4', gap: 10 }}>
                <Icon name="fileText" size={14} color="#0284C7" />
                <span style={{ fontSize: 12.5, flex: 1 }}>{d[0]}</span>
                <StatusBadge label={d[1]} />
              </div>
            ))}
          </div>
        </div>

        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Evaluación final del broker</div></div>
          <div className="cl-card-body">
            <div className="cl-grid c2">
              <Field label="Análisis financiero" required>
                <select defaultValue="aceptable">
                  <option value="bueno">Bueno · cliente solvente</option>
                  <option value="aceptable">Aceptable · cliente medio</option>
                  <option value="riesgo">Con riesgo</option>
                </select>
              </Field>
              <Field label="Recomendación">
                <select defaultValue="aprobar">
                  <option value="aprobar">Aprobar con condiciones estándar</option>
                  <option>Aprobar con garantía adicional</option>
                  <option>Rechazar</option>
                </select>
              </Field>
            </div>
            <div style={{ marginTop: 14 }}>
              <Field label="Observación final del broker">
                <textarea rows={4} defaultValue="Cliente con histórico comercial consistente. EE.FF. muestran liquidez adecuada. Recomiendo aprobación bajo condiciones estándar (garantía 2 meses, plazo 24 meses). Coordinar firma de contrato dentro de la primera semana." />
              </Field>
            </div>
          </div>
        </div>
      </div>

      <div className="cl-stack">
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Indicadores</div></div>
          <div className="cl-card-body">
            {[
              ['Capacidad de pago','Buena','green','78'],
              ['Histórico comercial','Sólido','green','85'],
              ['Documentación','Completa','green','100'],
              ['Riesgo evaluado','Bajo','green','22'],
            ].map(([t, v, tn, p], i) => (
              <div key={i} style={{ marginBottom: 10 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}>
                  <span>{t}</span><StatusBadge label={v} tone={tn} plain />
                </div>
                <div style={{ height: 6, background: '#EEF2F4', borderRadius: 3 }}><div style={{ width: p + '%', height: '100%', background: '#16A34A', borderRadius: 3 }}></div></div>
              </div>
            ))}
          </div>
        </div>

        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Historial</div></div>
          <div className="cl-card-body">
            <Timeline items={[
              { time: '23 May 14:32', title: 'Solicitud completa', body: 'Todos los documentos aprobados', tone: 'done' },
              { time: '23 May 10:14', title: 'Último documento cargado', body: 'Carta propuesta firmada · V. Mora', tone: 'done' },
              { time: '22 May 14:30', title: 'Solicitud creada', body: 'A partir de OP-1094 · V. Mora', tone: 'done' },
              { time: 'Pendiente', title: 'Decisión del broker', body: 'Acción requerida', tone: 'active' },
            ]} />
          </div>
        </div>
      </div>
    </div>
  </AppShell>
  );
};

/* =================== CIERRE DE OPORTUNIDAD =================== */
const ScreenCierre = () => {
  const { navigate } = useNav();
  return (
  <AppShell role="Agente inmobiliario"
    crumbs={["ControlLocal", "Oportunidades", "OP-1094", "Cierre"]}
    title="Cierre de oportunidad · OP-1094"
    subtitle="Cliente Boutique Lila · Local Calle Schell 412 · Solicitud SOL-0425 aprobada"
    actions={<><button onClick={() => navigate('detail-360')} className="cl-btn ghost">Cancelar</button><button onClick={() => navigate('oportunidades')} className="cl-btn primary"><Icon name="check" size={13} /> Confirmar cierre</button></>}>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 14 }}>
      <div className="cl-stack">
        <div className="cl-card">
          <div className="cl-card-head">
            <div>
              <div className="cl-card-title">Resumen del caso</div>
              <div className="cl-card-sub">5 interacciones · 2 visitas realizadas · 1 solicitud aprobada</div>
            </div>
          </div>
          <div className="cl-card-body" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 18 }}>
            <dl className="cl-kv">
              <dt>Cliente</dt><dd>Boutique Lila</dd>
              <dt>Captación</dt><dd>CAP-0226</dd>
              <dt>Local</dt><dd>Calle Schell 412</dd>
              <dt>Distrito</dt><dd>Miraflores</dd>
            </dl>
            <dl className="cl-kv">
              <dt>Agente</dt><dd>Valentina Mora</dd>
              <dt>Broker</dt><dd>Ricardo Salas</dd>
              <dt>Solicitud</dt><dd>SOL-0425</dd>
              <dt>Evaluación</dt><dd><StatusBadge label="Aprobada" /></dd>
            </dl>
            <dl className="cl-kv">
              <dt>Renta acordada</dt><dd><b>USD 1 850</b> / mes</dd>
              <dt>Plazo</dt><dd>24 meses</dd>
              <dt>Inicio</dt><dd>01 Jul 2026</dd>
              <dt>Comisión</dt><dd>USD 925 · 1 cuota</dd>
            </dl>
          </div>
        </div>

        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Tipo de cierre</div></div>
          <div className="cl-card-body">
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
              <label style={{ border: '2px solid #16A34A', borderRadius: 10, padding: 14, cursor: 'pointer', background: '#F4FBF6' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div style={{ width: 32, height: 32, borderRadius: 8, background: '#16A34A', color: '#fff', display: 'grid', placeItems: 'center' }}>
                    <Icon name="check" size={16} color="#fff" />
                  </div>
                  <input type="radio" defaultChecked />
                </div>
                <div style={{ fontWeight: 600, fontSize: 13, marginTop: 10 }}>Cierre exitoso</div>
                <div style={{ fontSize: 11.5, color: '#4A5A6E', marginTop: 4, lineHeight: 1.5 }}>
                  Contrato firmado. Habilitado solo si existe <b>solicitud aprobada</b>.
                </div>
                <div style={{ marginTop: 8 }}><StatusBadge label="Disponible" tone="green" plain /></div>
              </label>

              <label style={{ border: '1px solid #DDE5E8', borderRadius: 10, padding: 14, cursor: 'pointer', background: '#fff', opacity: 0.55 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div style={{ width: 32, height: 32, borderRadius: 8, background: '#FCE7E7', color: '#DC2626', display: 'grid', placeItems: 'center' }}>
                    <Icon name="x" size={16} />
                  </div>
                  <input type="radio" />
                </div>
                <div style={{ fontWeight: 600, fontSize: 13, marginTop: 10 }}>Cierre no favorable</div>
                <div style={{ fontSize: 11.5, color: '#4A5A6E', marginTop: 4, lineHeight: 1.5 }}>
                  Solicitud o evaluación rechazada. <b>No aplica</b> — solicitud aprobada.
                </div>
                <div style={{ marginTop: 8 }}><StatusBadge label="No disponible" tone="gray" plain /></div>
              </label>

              <label style={{ border: '1px solid #DDE5E8', borderRadius: 10, padding: 14, cursor: 'pointer', background: '#fff', opacity: 0.55 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div style={{ width: 32, height: 32, borderRadius: 8, background: '#EEF2F4', color: '#5B6B7F', display: 'grid', placeItems: 'center' }}>
                    <Icon name="flag" size={16} />
                  </div>
                  <input type="radio" />
                </div>
                <div style={{ fontWeight: 600, fontSize: 13, marginTop: 10 }}>Cierre por no continuidad</div>
                <div style={{ fontSize: 11.5, color: '#4A5A6E', marginTop: 4, lineHeight: 1.5 }}>
                  Cliente desiste. <b>Bloqueado</b> porque ya existe solicitud aprobada.
                </div>
                <div style={{ marginTop: 8 }}><StatusBadge label="Bloqueado" tone="red" plain /></div>
              </label>
            </div>
          </div>
        </div>

        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Detalle del cierre exitoso</div></div>
          <div className="cl-card-body">
            <div className="cl-grid c3">
              <Field label="Fecha de cierre" required><input defaultValue="24/05/2026" /></Field>
              <Field label="Fecha de inicio de contrato"><input defaultValue="01/07/2026" /></Field>
              <Field label="Tipo de contrato"><select><option>Privado simple</option><option>Escritura pública</option></select></Field>
              <Field label="Monto firmado (USD/mes)"><input defaultValue="1 850" /></Field>
              <Field label="Plazo (meses)"><input defaultValue="24" /></Field>
              <Field label="Comisión cobrada (USD)"><input defaultValue="925" /></Field>
            </div>
            <div style={{ marginTop: 14 }}>
              <Field label="Observaciones finales">
                <textarea rows={3} defaultValue="Contrato firmado por ambas partes el 24/05/2026. Inicio efectivo de operaciones programado para 01/07/2026 luego de remodelación del local." />
              </Field>
            </div>
          </div>
        </div>
      </div>

      <div className="cl-stack">
        <div className="cl-alert green">
          <Icon name="check" size={15} />
          <span><b>Cierre exitoso habilitado.</b> Existe una solicitud aprobada (SOL-0425) y la evaluación del broker es favorable.</span>
        </div>
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Validaciones del cierre</div></div>
          <div className="cl-card-body" style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 12.5 }}>
            {[
              ['Solicitud aprobada existe','ok'],
              ['Documentación completa','ok'],
              ['Evaluación final aprobada','ok'],
              ['Sin observaciones pendientes','ok'],
            ].map(([t, s], i) => (
              <div key={i} style={{ display: 'flex', gap: 8 }}>
                <div style={{ width: 16, height: 16, borderRadius: '50%', background: '#16A34A', color: '#fff', display: 'grid', placeItems: 'center', flex: '0 0 16px' }}>
                  <Icon name="check" size={10} color="#fff" />
                </div>
                <span>{t}</span>
              </div>
            ))}
          </div>
        </div>
        <button onClick={() => navigate('oportunidades')} className="cl-btn success" style={{ padding: '12px 14px', justifyContent: 'center', fontSize: 13.5 }}>
          <Icon name="check" size={14} /> Confirmar cierre exitoso
        </button>
        <button onClick={() => navigate('detail-360')} className="cl-btn ghost" style={{ justifyContent: 'center', color: '#4A5A6E' }}>Cancelar</button>
      </div>
    </div>
  </AppShell>
  );
};

Object.assign(window, { ScreenInteracciones, ScreenVisitas, ScreenSolicitudes, ScreenDocumentos, ScreenEvaluacion, ScreenCierre });
