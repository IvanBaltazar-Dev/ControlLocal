// Screens 13-16: Clientes interesados, Oportunidades, Crear oportunidad, Detalle 360

// Listado de clientes interesados — vista del AGENTE. El registro de
// clientes es exclusivo del agente; brokers y administradores no lo hacen.
const ScreenClientes = () => (
  <AppShell role="Agente inmobiliario"
    crumbs={["ControlLocal", "Comercial", "Clientes interesados"]}
    title="Clientes interesados" subtitle="Prospectos comerciales registrados por mí para vincular a captaciones activas"
    actions={<button className="cl-btn primary"><Icon name="plus" size={13} /> Registrar cliente</button>}>
    <FilterBar search="Buscar por nombre, razón social, documento, rubro…">
      <select className="cl-select"><option>Tipo de persona</option></select>
      <select className="cl-select"><option>Rubro comercial</option><option>Restaurante</option><option>Moda</option><option>Servicios</option></select>
      <select className="cl-select"><option>Estado</option><option>Activo</option><option>Sin oportunidad</option></select>
      <select className="cl-select"><option>Agente</option></select>
    </FilterBar>
    <DataTable
      columns={[
        { label: "Cliente", render: r => <div><div style={{ fontWeight: 600 }}>{r.name}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.type}</div></div> },
        { label: "Documento", render: r => <span className="mono">{r.doc}</span> },
        { label: "Contacto", render: r => <div><div className="mono" style={{ fontSize: 12 }}>{r.phone}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.email}</div></div> },
        { label: "Rubro", render: r => <StatusBadge label={r.rubro} tone="blue" plain /> },
        { label: "Presupuesto", render: r => <span><b>USD {r.budget}</b> / mes</span> },
        { label: "Oportunidades", render: r => <span><b>{r.ops}</b> activa{r.ops===1?'':'s'}</span> },
        { label: "Estado", render: r => <StatusBadge label={r.status} /> },
        { label: "", render: () => <RowActions /> },
      ]}
      rows={[
        { name: "Inversiones Trébol S.A.C.", type: "Persona jurídica · RUC", doc: "20 553 712 008", phone: "+51 1 432 1200", email: "compras@trebol.pe", rubro: "Restaurante", budget: "2 500 – 3 200", ops: 1, status: "Activo" },
        { name: "Boutique Lila", type: "Persona jurídica · RUC", doc: "20 471 998 110", phone: "+51 1 718 4400", email: "lila@gmail.com", rubro: "Moda", budget: "1 500 – 2 000", ops: 1, status: "Activo" },
        { name: "Bodegas del Norte E.I.R.L.", type: "Persona jurídica · RUC", doc: "20 502 220 008", phone: "+51 1 222 1108", email: "ventas@bdn.com.pe", rubro: "Retail", budget: "1 200 – 1 800", ops: 1, status: "Activo" },
        { name: "Café Lima", type: "Persona natural · RUC", doc: "10 552 408 002", phone: "+51 987 412 008", email: "cafelima@gmail.com", rubro: "Café", budget: "1 000 – 1 500", ops: 1, status: "Activo" },
        { name: "Carla Espinoza", type: "Persona natural · DNI", doc: "45 893 211", phone: "+51 991 552 008", email: "cespinoza@yahoo.com", rubro: "Servicios", budget: "800 – 1 200", ops: 0, status: "Activo" },
        { name: "Plásticos del Sur S.R.L.", type: "Persona jurídica · RUC", doc: "20 778 002 411", phone: "+51 1 822 3300", email: "info@plassur.pe", rubro: "Retail", budget: "2 000 – 2 800", ops: 0, status: "Activo" },
      ]} />
    <Pagination total={142} />
  </AppShell>
);

// Oportunidades comerciales — la pantalla se adapta según el rol activo:
//   • Agente: ve las suyas, puede crear nuevas sobre captaciones activas.
//   • Broker: ve oportunidades del EQUIPO bajo su supervisión, sin crear.
//   • Admin: no entra aquí por menú — si llega, ve modo lectura global.
const ScreenOportunidades = () => {
  const { navigate, role } = useNav();
  const isBroker = role === "Broker";
  const isAdmin  = role === "Broker administrador";
  const effectiveRole = role || "Agente inmobiliario";
  return (
  <AppShell role={effectiveRole}
    crumbs={isBroker ? ["ControlLocal", "Bandejas de revisión", "Operaciones del equipo"]
          : isAdmin  ? ["ControlLocal", "Supervisión", "Oportunidades (lectura)"]
          :            ["ControlLocal", "Comercial", "Oportunidades"]}
    title={isBroker ? "Operaciones del equipo"
         : isAdmin  ? "Oportunidades — vista global"
         :            "Mis oportunidades comerciales"}
    subtitle={isBroker ? "Seguimiento de oportunidades abiertas por los agentes bajo tu supervisión"
           : isAdmin  ? "Vista de auditoría — lectura. La operación diaria la realiza cada broker sobre su equipo."
           :            "Seguimiento de clientes interesados sobre captaciones activas"}
    actions={
      isBroker ? (
        <>
          <button className="cl-btn"><Icon name="download" size={13} /> Exportar</button>
          <button onClick={() => navigate('bandeja-captaciones')} className="cl-btn"><Icon name="pin" size={13} /> Captaciones por revisar</button>
        </>
      ) : isAdmin ? (
        <>
          <button className="cl-btn"><Icon name="download" size={13} /> Exportar</button>
        </>
      ) : (
        <>
          <button className="cl-btn"><Icon name="grid" size={13} /> Vista tablero</button>
          <button onClick={() => navigate('oportunidad-nueva')} className="cl-btn primary"><Icon name="plus" size={13} /> Nueva oportunidad</button>
        </>
      )
    }>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6,1fr)', gap: 10, marginBottom: 14 }}>
      <MetricCard icon="target" label="Abiertas" value="22" tone="blue" />
      <MetricCard icon="handshake" label="En seguimiento" value="18" tone="info" />
      <MetricCard icon="fileText" label="Solicitud creada" value="14" tone="amber" />
      <MetricCard icon="check" label="Cerradas exitosas" value="9" tone="green" />
      <MetricCard icon="x" label="No favorables" value="3" tone="red" />
      <MetricCard icon="alert" label="No continúan" value="4" tone="navy" />
    </div>
    <div className="cl-tabs">
      <div className="cl-tab active">Todas <span className="cl-tab-count">70</span></div>
      <div className="cl-tab">Mis oportunidades <span className="cl-tab-count">8</span></div>
      <div className="cl-tab">Sin actividad <span className="cl-tab-count">5</span></div>
      <div className="cl-tab">Por cerrar <span className="cl-tab-count">12</span></div>
    </div>
    <FilterBar search="Buscar oportunidad, cliente, captación…">
      <select className="cl-select"><option>Estado</option></select>
      <select className="cl-select"><option>Agente</option></select>
      <select className="cl-select"><option>Captación</option></select>
      <select className="cl-select"><option>Última actividad</option><option>Hoy</option><option>Esta semana</option><option>Sin actividad 7d+</option></select>
    </FilterBar>
    <DataTable
      columns={[
        { label: "Oportunidad", render: r => <div><span className="mono" style={{ fontWeight: 600, color: '#0E5BFF' }}>{r.code}</span><div className="cl-muted" style={{ fontSize: 11, marginTop: 2 }}>Creada {r.created}</div></div> },
        { label: "Cliente interesado", render: r => <div><div style={{ fontWeight: 600 }}>{r.client}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.rubro}</div></div> },
        { label: "Captación / local", render: r => <div><div style={{ fontSize: 12.5 }}>{r.local}</div><div className="cl-muted mono" style={{ fontSize: 11 }}>{r.capCode}</div></div> },
        { label: "Agente", render: r => <div className="cl-flex"><span className="cl-avatar" style={{ width: 22, height: 22, fontSize: 9 }}>{r.agent.split(' ').map(x=>x[0]).join('')}</span><span style={{ fontSize: 12 }}>{r.agent}</span></div> },
        { label: "Estado", render: r => <StatusBadge label={r.status} /> },
        { label: "Última interacción", render: r => <div><div style={{ fontSize: 12 }}>{r.last}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.lastWhen}</div></div> },
        { label: "Próxima visita", render: r => r.next ? <div><div style={{ fontSize: 12, fontWeight: 500 }}>{r.next}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.nextWhen}</div></div> : <span className="cl-muted">—</span> },
        { label: "", render: () => <button onClick={() => navigate('detail-360')} className="cl-btn sm primary">Detalle 360</button> },
      ]}
      rows={[
        { code: "OP-1098", created: "20 May", client: "Inversiones Trébol", rubro: "Restaurante", local: "Av. La Marina 245", capCode: "CAP-0218", agent: "Valentina Mora", status: "En seguimiento", last: "Llamada · interesado", lastWhen: "Hoy 11:00", next: "Visita guiada", nextWhen: "Hoy 16:00" },
        { code: "OP-1094", created: "18 May", client: "Boutique Lila", rubro: "Moda", local: "Calle Schell 412", capCode: "CAP-0226", agent: "Valentina Mora", status: "Solicitud creada", last: "Solicitud registrada", lastWhen: "Ayer 17:30", next: null },
        { code: "OP-1089", created: "16 May", client: "Bodegas del Norte", rubro: "Retail", local: "Av. Petit Thouars 1875", capCode: "CAP-0231", agent: "Daniel Romero", status: "Abierta", last: "Correo enviado", lastWhen: "16 May", next: "Visita", nextWhen: "26 May 10:00" },
        { code: "OP-1085", created: "14 May", client: "Café Lima", rubro: "Café", local: "Jr. Berlín 230", capCode: "CAP-0234", agent: "Valentina Mora", status: "En seguimiento", last: "Visita realizada", lastWhen: "22 May", next: "Visita 2da", nextWhen: "25 May 09:30" },
        { code: "OP-1083", created: "12 May", client: "Trébol S.A.C.", rubro: "Restaurante", local: "Av. La Marina 245", capCode: "CAP-0218", agent: "Valentina Mora", status: "Cerrada exitosa", last: "Contrato firmado", lastWhen: "Hoy 09:14", next: null },
        { code: "OP-1077", created: "10 May", client: "Carla Espinoza", rubro: "Servicios", local: "Av. Salaverry 2120", capCode: "CAP-0242", agent: "Carolina Vega", status: "Cerrada no favorable", last: "Evaluación rechazada", lastWhen: "22 May", next: null },
        { code: "OP-1071", created: "08 May", client: "Plásticos del Sur", rubro: "Retail", local: "Av. Aviación 4012", capCode: "CAP-0238", agent: "Matías León", status: "Cerrada no continúa", last: "Cliente desistió", lastWhen: "20 May", next: null },
      ]} />
    <Pagination total={70} />
  </AppShell>
  );
};

const ScreenOportunidadNueva = () => {
  const { navigate } = useNav();
  return (
  <AppShell role="Agente inmobiliario"
    crumbs={["ControlLocal", "Oportunidades", "Nueva oportunidad"]}
    title="Crear oportunidad comercial"
    subtitle="Vincula un cliente interesado con una captación activa para iniciar el seguimiento"
    actions={
      <>
        <button onClick={() => navigate('oportunidades')} className="cl-btn ghost">Cancelar</button>
        <button onClick={() => navigate('detail-360')} className="cl-btn primary"><Icon name="check" size={13} /> Crear oportunidad</button>
      </>
    }>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 14 }}>
      <div className="cl-stack">
        <div className="cl-alert amber">
          <Icon name="alert" size={15} />
          <span><b>Reglas de creación.</b> Solo puedes crear una oportunidad sobre una <b>captación activa</b>. No debe existir otra oportunidad abierta para el mismo cliente y captación.</span>
        </div>

        <FormSection num="1" title="Captación asociada" sub="Solo se muestran captaciones activas">
          <Field label="Captación activa" required>
            <select defaultValue="CAP-0218">
              <option>CAP-0218 · Av. La Marina 245, San Miguel · USD 2 800</option>
              <option>CAP-0226 · Calle Schell 412, Miraflores · USD 1 950</option>
              <option>CAP-0234 · Jr. Berlín 230, Miraflores · USD 1 450</option>
            </select>
          </Field>
          <div style={{ marginTop: 14, padding: 14, background: '#FAFBFD', border: '1px solid #DDE5E8', borderRadius: 8 }}>
            <div className="cl-spread">
              <div>
                <div style={{ fontSize: 14, fontWeight: 600 }}>Av. La Marina 245</div>
                <div className="cl-muted" style={{ fontSize: 12, marginTop: 2 }}>San Miguel · 120 m² · Restaurante / Café</div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: 15, fontWeight: 600 }}>USD 2 800 <span className="cl-muted" style={{ fontSize: 11 }}>/ mes</span></div>
                <div style={{ marginTop: 4 }}><StatusBadge label="Activa" /></div>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 18, marginTop: 12, fontSize: 12 }}>
              <div><span className="cl-muted">Propietario · </span><b>Inmobiliaria Pacífico</b></div>
              <div><span className="cl-muted">Vigencia · </span><b>hasta 01 Oct 2026</b></div>
              <div><span className="cl-muted">Comisión · </span><b>5.0%</b></div>
            </div>
          </div>
        </FormSection>

        <FormSection num="2" title="Cliente interesado" sub="Selecciona un cliente registrado o crea uno nuevo">
          <div className="cl-grid c2">
            <Field label="Cliente interesado" required>
              <select defaultValue="trebol">
                <option value="trebol">Inversiones Trébol S.A.C. — RUC 20 553 712 008</option>
                <option>Boutique Lila</option>
              </select>
            </Field>
            <Field label="Rubro de interés">
              <input defaultValue="Restaurante / Café" disabled />
            </Field>
            <Field label="Contacto principal"><input defaultValue="Mauricio Castillo · +51 998 220 411" disabled /></Field>
            <Field label="Presupuesto declarado"><input defaultValue="USD 2 500 – 3 200 / mes" disabled /></Field>
          </div>
          <div className="cl-alert blue" style={{ marginTop: 14 }}>
            <Icon name="check" size={15} />
            <span>Verificado: <b>Inversiones Trébol</b> no tiene otra oportunidad abierta sobre <b>CAP-0218</b>.</span>
          </div>
        </FormSection>

        <FormSection num="3" title="Asignación y notas" sub="Agente responsable de la oportunidad">
          <div className="cl-grid c2">
            <Field label="Agente responsable" required>
              <select defaultValue="vm"><option value="vm">Valentina Mora — Lima Centro</option></select>
            </Field>
            <Field label="Origen del lead">
              <select><option>Web corporativa</option><option>Referido</option><option>Redes sociales</option><option>Llamada en frío</option></select>
            </Field>
          </div>
          <div style={{ marginTop: 14 }}>
            <Field label="Observaciones iniciales">
              <textarea rows={3} defaultValue="Cliente busca abrir segunda sede en San Miguel. Visita preliminar programada para verificación de fachada y servicios." />
            </Field>
          </div>
        </FormSection>
      </div>

      <div className="cl-stack">
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Validaciones</div></div>
          <div className="cl-card-body" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {[
              ['Captación seleccionada está activa','ok'],
              ['Cliente no tiene oportunidad abierta sobre esta captación','ok'],
              ['Agente responsable asignado','ok'],
              ['Observaciones iniciales registradas','ok'],
            ].map(([t, s], i) => (
              <div key={i} style={{ display: 'flex', gap: 8, fontSize: 12.5 }}>
                <div style={{ width: 18, height: 18, borderRadius: '50%', background: '#16A34A', color: '#fff', display: 'grid', placeItems: 'center', flex: '0 0 18px' }}>
                  <Icon name="check" size={11} color="#fff" />
                </div>
                <span>{t}</span>
              </div>
            ))}
          </div>
        </div>
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Qué pasa después</div></div>
          <div className="cl-card-body" style={{ fontSize: 12.5, color: '#4A5A6E', lineHeight: 1.55 }}>
            Al crear la oportunidad, esta queda en estado <b>Abierta</b> y se habilitan las pestañas del Detalle 360: Interacciones, Visitas, Solicitud, Documentos y Cierre.
          </div>
        </div>
      </div>
    </div>
  </AppShell>
  );
};

/* =================== DETALLE 360 — pantalla central =================== */
const Detail360Header = () => {
  const { navigate } = useNav();
  return (
  <div style={{ background: 'linear-gradient(180deg, #fff 0%, #FAFBFD 100%)', border: '1px solid #DDE5E8', borderRadius: 12, padding: 18, marginBottom: 14 }}>
    <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
      <div style={{ width: 56, height: 56, borderRadius: 12, background: 'linear-gradient(135deg, #0E5BFF, #0a48cc)', display: 'grid', placeItems: 'center', color: '#fff' }}>
        <Icon name="target" size={26} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span className="mono" style={{ color: '#8392A7', fontSize: 12 }}>OP-1098</span>
          <StatusBadge label="En seguimiento" />
          <span style={{ fontSize: 11, color: '#8392A7' }}>· creada 20 May 2026 · 4 días</span>
        </div>
        <div style={{ fontSize: 20, fontWeight: 600, marginTop: 4, letterSpacing: '-0.015em' }}>
          Inversiones Trébol S.A.C. <span style={{ color: '#8392A7', fontWeight: 400 }}>·</span> Av. La Marina 245
        </div>
        <div style={{ display: 'flex', gap: 22, marginTop: 12, fontSize: 12 }}>
          <div><div className="cl-muted">Cliente</div><div style={{ fontWeight: 600, marginTop: 2 }}>Inversiones Trébol S.A.C.</div></div>
          <div><div className="cl-muted">Captación</div><div style={{ fontWeight: 600, marginTop: 2, color: '#0E5BFF' }}>CAP-0218 · Av. La Marina 245</div></div>
          <div><div className="cl-muted">Agente</div><div style={{ fontWeight: 600, marginTop: 2 }}>Valentina Mora</div></div>
          <div><div className="cl-muted">Próxima acción</div><div style={{ fontWeight: 600, marginTop: 2 }}>Visita guiada — hoy 16:00</div></div>
          <div><div className="cl-muted">Última interacción</div><div style={{ fontWeight: 600, marginTop: 2 }}>Llamada · hoy 11:00</div></div>
        </div>
      </div>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end', maxWidth: 360 }}>
        <button onClick={() => navigate('interacciones')} className="cl-btn"><Icon name="phone" size={13} /> Registrar interacción</button>
        <button onClick={() => navigate('visitas')} className="cl-btn"><Icon name="calendar" size={13} /> Programar visita</button>
        <button onClick={() => navigate('solicitudes')} className="cl-btn"><Icon name="fileText" size={13} /> Crear solicitud</button>
        <button onClick={() => navigate('cierre')} className="cl-btn danger"><Icon name="flag" size={13} /> Cerrar oportunidad</button>
      </div>
    </div>
    {/* Process steps */}
    <div style={{ display: 'flex', alignItems: 'center', gap: 0, marginTop: 18, padding: '14px 0 0', borderTop: '1px solid #EEF2F4' }}>
      {[
        ['Captación','done'],
        ['Oportunidad','done'],
        ['Interacciones','done'],
        ['Visitas','active'],
        ['Solicitud','pending'],
        ['Evaluación','pending'],
        ['Cierre','pending'],
      ].map(([t, s], i, arr) => (
        <React.Fragment key={i}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{
              width: 26, height: 26, borderRadius: '50%',
              background: s === 'done' ? '#16A34A' : s === 'active' ? '#fff' : '#fff',
              border: s === 'active' ? '2px solid #0E5BFF' : s === 'done' ? 0 : '2px solid #DDE5E8',
              color: '#fff', display: 'grid', placeItems: 'center',
              boxShadow: s === 'active' ? '0 0 0 4px rgba(14,91,255,0.15)' : 'none',
              fontSize: 11, fontWeight: 600
            }}>
              {s === 'done' ? <Icon name="check" size={12} color="#fff" /> : <span style={{ color: s === 'active' ? '#0E5BFF' : '#B5BFCB' }}>{i + 1}</span>}
            </div>
            <div style={{ fontSize: 12, fontWeight: s === 'active' ? 600 : 500, color: s === 'pending' ? '#8392A7' : '#0B1F33' }}>{t}</div>
          </div>
          {i < arr.length - 1 && <div style={{ flex: 1, height: 2, background: i < 3 ? '#16A34A' : '#DDE5E8', margin: '0 10px', borderRadius: 1 }}></div>}
        </React.Fragment>
      ))}
    </div>
  </div>
  );
};

const ScreenDetail360 = () => {
  const { navigate } = useNav();
  return (
  <AppShell role="Agente inmobiliario"
    crumbs={["ControlLocal", "Oportunidades", "OP-1098 · Inversiones Trébol"]}
    title={null}>
    <Detail360Header />
    <div className="cl-tabs">
      <div className="cl-tab">Datos generales</div>
      <div className="cl-tab">Cliente</div>
      <div className="cl-tab">Captación / Local</div>
      <div onClick={() => navigate('interacciones')} className="cl-tab active">Interacciones <span className="cl-tab-count">5</span></div>
      <div onClick={() => navigate('visitas')} className="cl-tab">Visitas <span className="cl-tab-count">2</span></div>
      <div onClick={() => navigate('solicitudes')} className="cl-tab">Solicitud</div>
      <div onClick={() => navigate('solicitudes')} className="cl-tab">Documentos <span className="cl-tab-count">3</span></div>
      <div className="cl-tab">Evaluaciones</div>
      <div onClick={() => navigate('cierre')} className="cl-tab">Cierre</div>
    </div>

    <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 14 }}>
      <div className="cl-stack">
        <div className="cl-card">
          <div className="cl-card-head">
            <div>
              <div className="cl-card-title">Interacciones registradas</div>
              <div className="cl-card-sub">5 registros · última hoy 11:00</div>
            </div>
            <button className="cl-btn primary sm"><Icon name="plus" size={12} /> Nueva interacción</button>
          </div>
          <div className="cl-card-body" style={{ padding: 0 }}>
            <table className="cl-table">
              <thead><tr><th>Canal</th><th>Fecha</th><th>Agente</th><th>Resultado</th><th>Observaciones</th><th></th></tr></thead>
              <tbody>
                {[
                  ['Llamada','Hoy 11:00','V. Mora','Interesado, confirma visita','Confirmó asistencia a visita 16:00. Pidió incluir plano del local.','blue'],
                  ['WhatsApp','22 May 17:42','V. Mora','Cliente solicita info','Envió pliego de servicios. Cliente revisará en 24h.','green'],
                  ['Correo','21 May 09:08','V. Mora','Envío de propuesta','Adjunto: condiciones comerciales y ficha técnica.','green'],
                  ['Llamada','20 May 14:30','V. Mora','Sin contacto','No respondió. Reagenda para tarde.','amber'],
                  ['Correo','20 May 10:15','V. Mora','Lead inicial','Primer contacto desde formulario web.','blue'],
                ].map((r, i) => (
                  <tr key={i}>
                    <td><div className="cl-flex" style={{ gap: 6 }}><Icon name={r[0] === 'Llamada' ? 'phone' : r[0] === 'Correo' ? 'mail' : 'activity'} size={13} color="#8392A7" /><span style={{ fontSize: 12 }}>{r[0]}</span></div></td>
                    <td className="muted" style={{ fontSize: 11.5 }}>{r[1]}</td>
                    <td><span style={{ fontSize: 12 }}>{r[2]}</span></td>
                    <td><StatusBadge label={r[3]} tone={r[5]} plain /></td>
                    <td><span style={{ fontSize: 12, color: '#4A5A6E' }}>{r[4]}</span></td>
                    <td><RowActions items={["view","edit"]} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Registrar nueva interacción</div></div>
          <div className="cl-card-body">
            <div className="cl-grid c4">
              <Field label="Canal" required><select><option>Llamada</option><option>WhatsApp</option><option>Correo</option><option>Presencial</option></select></Field>
              <Field label="Fecha" required><input defaultValue="24/05/2026 14:20" /></Field>
              <Field label="Resultado" required><select><option>Interesado</option><option>Sin contacto</option><option>No interesado</option><option>Solicita info</option></select></Field>
              <Field label="Adjuntos"><input type="text" placeholder="Sin archivos" /></Field>
            </div>
            <div style={{ marginTop: 14 }}>
              <Field label="Observaciones"><textarea rows={3} placeholder="Describe brevemente la interacción…" /></Field>
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 14, justifyContent: 'flex-end' }}>
              <button className="cl-btn">Cancelar</button>
              <button className="cl-btn primary"><Icon name="check" size={12} /> Guardar interacción</button>
            </div>
          </div>
        </div>
      </div>

      <div className="cl-stack">
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Resumen de oportunidad</div></div>
          <div className="cl-card-body">
            <dl className="cl-kv">
              <dt>Estado</dt><dd><StatusBadge label="En seguimiento" /></dd>
              <dt>Creada</dt><dd>20 May 2026</dd>
              <dt>Antigüedad</dt><dd>4 días</dd>
              <dt>Interacciones</dt><dd>5</dd>
              <dt>Visitas</dt><dd>1 realizada · 1 programada</dd>
              <dt>Solicitud</dt><dd className="cl-muted">No creada</dd>
              <dt>Documentos</dt><dd>3 cargados</dd>
            </dl>
          </div>
        </div>

        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Línea de tiempo</div></div>
          <div className="cl-card-body">
            <Timeline items={[
              { time: 'Hoy 11:00', title: 'Interacción registrada', body: 'Llamada · interesado · V. Mora', tone: 'done' },
              { time: '22 May', title: 'Visita realizada', body: 'Resultado: positivo · V. Mora', tone: 'done' },
              { time: '21 May', title: 'Propuesta enviada', body: 'Correo · pliego adjunto', tone: 'done' },
              { time: '20 May', title: 'Oportunidad creada', body: 'Cliente Trébol · CAP-0218', tone: 'done' },
              { time: 'Hoy 16:00', title: 'Visita guiada', body: 'Programada · agente V. Mora', tone: 'active' },
            ]} />
          </div>
        </div>

        <div className="cl-alert blue">
          <Icon name="info" size={15} />
          <span>No se pueden registrar interacciones, visitas ni solicitudes si la oportunidad se cierra.</span>
        </div>
      </div>
    </div>
  </AppShell>
  );
};

Object.assign(window, { ScreenClientes, ScreenOportunidades, ScreenOportunidadNueva, ScreenDetail360 });