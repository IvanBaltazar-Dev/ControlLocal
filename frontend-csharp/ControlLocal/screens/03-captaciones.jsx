// Screens 10-12: Captaciones (listado), Registro/edición de captación, Revisión de captación

// Listado de captaciones — vista del AGENTE inmobiliario. El agente es el
// único perfil que registra captaciones y las envía a revisión. El broker
// usa una bandeja dedicada (ScreenBandejaCaptaciones).
const ScreenCaptaciones = () => {
  const { navigate } = useNav();
  return (
  <AppShell role="Agente inmobiliario"
    crumbs={["ControlLocal", "Captación", "Captaciones"]}
    title="Mis captaciones" subtitle="Incorporación de locales comerciales al proceso de gestión"
    actions={<><button className="cl-btn"><Icon name="download" size={13} /> Exportar</button><button onClick={() => navigate('captacion-form')} className="cl-btn primary"><Icon name="plus" size={13} /> Nueva captación</button></>}>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5,1fr)', gap: 12, marginBottom: 14 }}>
      <MetricCard icon="clock" label="Borradores / En revisión" value="3" tone="amber" footer="enviadas a mi broker" />
      <MetricCard icon="check" label="Activas" value="8" tone="green" />
      <MetricCard icon="alert" label="Observadas" value="1" tone="amber" footer="esperan subsanación" />
      <MetricCard icon="x" label="Rechazadas" value="1" tone="red" />
      <MetricCard icon="folder" label="Cerradas" value="1" tone="navy" />
    </div>
    <div className="cl-tabs">
      <div className="cl-tab active">Todas <span className="cl-tab-count">14</span></div>
      <div className="cl-tab">Borradores <span className="cl-tab-count">2</span></div>
      <div className="cl-tab">En revisión <span className="cl-tab-count">3</span></div>
      <div className="cl-tab">Observadas <span className="cl-tab-count">1</span></div>
      <div className="cl-tab">Activas <span className="cl-tab-count">8</span></div>
    </div>
    <FilterBar search="Buscar por código, local, dirección…">
      <select className="cl-select"><option>Estado</option><option>Borrador</option><option>En revisión</option><option>Activa</option><option>Observada</option><option>Rechazada</option></select>
      <select className="cl-select"><option>Distrito</option></select>
      <select className="cl-select"><option>Vigencia</option><option>Próximas a vencer</option></select>
    </FilterBar>
    <DataTable
      columns={[
        { label: "Código", render: r => <span className="mono" style={{ fontWeight: 600, color: '#0E5BFF' }}>{r.code}</span> },
        { label: "Local asociado", render: r => <div><div style={{ fontWeight: 600 }}>{r.local}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.district} · {r.area} m²</div></div> },
        { label: "Propietario", key: "owner" },
        { label: "Agente", render: r => <div className="cl-flex"><span className="cl-avatar" style={{ width: 22, height: 22, fontSize: 9 }}>{r.agent.split(' ').map(x=>x[0]).join('')}</span><span style={{ fontSize: 12 }}>{r.agent}</span></div> },
        { label: "Vigencia", render: r => <div><div className="mono" style={{ fontSize: 12 }}>{r.validity}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.left}</div></div> },
        { label: "Comisión", render: r => <span><b>{r.comm}</b>%</span> },
        { label: "Estado", render: r => <StatusBadge label={r.status} /> },
        { label: "", render: r => (
          <div onClick={(e) => e.stopPropagation()}>
            {r.status === 'Observada'
              ? <button onClick={() => navigate('captacion-form')} className="cl-btn sm warn">Subsanar</button>
              : <button onClick={() => navigate('captacion-form')} className="cl-btn sm">Ver detalle</button>}
          </div>
        ) },
      ]}
      rows={[
        { code: "CAP-0218", local: "Av. La Marina 245",       district: "San Miguel",  area: 120, owner: "Inmobiliaria Pacífico", agent: "Valentina Mora", validity: "01 Abr – 01 Oct 2026", left: "venc. en 130 días", comm: "5.0", status: "Activa" },
        { code: "CAP-0226", local: "Calle Schell 412",         district: "Miraflores",  area:  68, owner: "Carlos Mendoza",         agent: "Valentina Mora", validity: "15 May – 15 Nov 2026", left: "venc. en 175 días", comm: "4.5", status: "Activa" },
        { code: "CAP-0233", local: "Av. Caminos del Inca 1820", district: "Surco",       area: 140, owner: "A. Pereyra",             agent: "Valentina Mora", validity: "Borrador",            left: "no enviado",          comm: "—",  status: "Pendiente" },
        { code: "CAP-0234", local: "Jr. Berlín 230",            district: "Miraflores",  area:  52, owner: "A. Pereyra",             agent: "Valentina Mora", validity: "10 May – 10 Nov 2026", left: "venc. en 170 días", comm: "5.5", status: "Activa" },
        { code: "CAP-0237", local: "Av. Salaverry 2120",       district: "Jesús María", area:  88, owner: "R. Linares",             agent: "Valentina Mora", validity: "En revisión",          left: "enviada hace 1d",     comm: "5.0", status: "Pendiente" },
        { code: "CAP-0241", local: "Av. Perú 2845",            district: "San Martín",  area:  72, owner: "Inmobiliaria Pacífico", agent: "Valentina Mora", validity: "Observada",           left: "requiere ajuste",    comm: "4.5", status: "Observada" },
        { code: "CAP-0244", local: "Av. Brasil 2890",          district: "Magdalena",   area:  60, owner: "Inmobiliaria Pacífico", agent: "Valentina Mora", validity: "23 May – 23 Ago 2026", left: "venc. en 91 días",  comm: "5.0", status: "Rechazada" },
      ]} />
    <Pagination total={14} />
  </AppShell>
  );
};

/* =================== REGISTRO/EDICIÓN =================== */
const ScreenCaptacionForm = () => {
  const { navigate } = useNav();
  return (
  <AppShell role="Agente inmobiliario"
    crumbs={["ControlLocal", "Mis captaciones", "CAP-0233 · Edición"]}
    title="Nueva captación · CAP-0233"
    subtitle="Borrador · última modificación hace 6 minutos por V. Mora"
    actions={
      <>
        <button onClick={() => navigate('captaciones')} className="cl-btn ghost">Cancelar</button>
        <button onClick={() => navigate('captaciones')} className="cl-btn">Guardar borrador</button>
        <button onClick={() => navigate('captaciones')} className="cl-btn primary"><Icon name="arrowRight" size={13} /> Enviar a revisión</button>
      </>
    }>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 280px', gap: 14 }}>
      <div>
        <FormSection num="1" title="Datos del local" sub="Selecciona un local registrado o crea uno nuevo">
          <div className="cl-grid c2">
            <Field label="Local comercial" required>
              <div style={{ position: 'relative' }}>
                <input defaultValue="LC-0247 · Av. Caminos del Inca 1820, Surco" />
                <div style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)', color: '#8392A7' }}>
                  <Icon name="search" size={14} />
                </div>
              </div>
            </Field>
            <Field label="Propietario">
              <input defaultValue="Ana Lucía Pereyra · DNI 09 778 002" disabled />
            </Field>
            <Field label="Distrito"><input defaultValue="Santiago de Surco" disabled /></Field>
            <Field label="Metraje (m²)"><input defaultValue="140" disabled /></Field>
            <Field label="Rubro permitido" required>
              <select defaultValue="Restaurante"><option>Restaurante / Café</option><option>Moda / Boutique</option><option>Servicios</option><option>Retail</option></select>
            </Field>
            <Field label="Tipo de inmueble">
              <select><option>Local en calle</option><option>Local en galería</option><option>Espacio en centro comercial</option></select>
            </Field>
          </div>
        </FormSection>

        <FormSection num="2" title="Condiciones comerciales" sub="Precios y condiciones del propietario">
          <div className="cl-grid c3">
            <Field label="Precio referencial" required>
              <div style={{ display: 'flex' }}>
                <select style={{ width: 70, borderRadius: '7px 0 0 7px', borderRight: 0 }}><option>USD</option><option>PEN</option></select>
                <input defaultValue="2 800" style={{ borderRadius: '0 7px 7px 0' }} />
              </div>
            </Field>
            <Field label="Garantía exigida (meses)"><input defaultValue="2" /></Field>
            <Field label="Adelanto (meses)"><input defaultValue="1" /></Field>
            <Field label="Mantenimiento (USD)"><input defaultValue="180" /></Field>
            <Field label="Incluye servicios"><select><option>No</option><option>Sí, parcial</option><option>Sí, total</option></select></Field>
            <Field label="Plazo mínimo (meses)"><input defaultValue="24" /></Field>
          </div>
        </FormSection>

        <FormSection num="3" title="Vigencia y comisión" sub="Duración de la captación y comisión pactada con el propietario">
          <div className="cl-grid c3">
            <Field label="Fecha de inicio" required><input defaultValue="24/05/2026" /></Field>
            <Field label="Fecha de fin" required><input defaultValue="24/11/2026" /></Field>
            <Field label="Vigencia"><input defaultValue="6 meses" disabled /></Field>
            <Field label="Comisión pactada (%)" required><input defaultValue="5.0" /></Field>
            <Field label="Tipo de comisión"><select><option>Sobre primera renta</option><option>Sobre renta anual</option></select></Field>
            <Field label="Exclusividad"><select><option>Sí, exclusiva</option><option>No exclusiva</option></select></Field>
          </div>
        </FormSection>

        <FormSection num="4" title="Observaciones" sub="Notas internas visibles para el broker">
          <Field label="Observaciones de la captación">
            <textarea defaultValue="Propietario solicita evaluación previa del cliente. Local en buen estado, con instalaciones para gastronomía. Disponible para visitas con cita previa." rows={4} />
          </Field>
          <div style={{ marginTop: 12 }}>
            <label className="cl-label">Documentos adjuntos</label>
            <div style={{ border: '1.5px dashed #DDE5E8', borderRadius: 8, padding: 18, textAlign: 'center', background: '#FAFBFD' }}>
              <Icon name="upload" size={22} color="#8392A7" />
              <div style={{ fontSize: 13, marginTop: 6 }}>Arrastra los archivos aquí o <a style={{ color: '#0E5BFF', fontWeight: 500 }}>selecciona</a></div>
              <div style={{ fontSize: 11, color: '#8392A7', marginTop: 4 }}>PDF, JPG, PNG · Máx 10 MB por archivo</div>
            </div>
            <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
              {[
                ['Contrato_de_corretaje.pdf','842 KB'],
                ['Ficha_tecnica_local.pdf','1.2 MB'],
              ].map(([f, s], i) => (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px', border: '1px solid #DDE5E8', borderRadius: 7, background: '#fff' }}>
                  <Icon name="fileText" size={15} color="#0284C7" />
                  <span style={{ fontSize: 12.5, flex: 1 }}>{f}</span>
                  <span className="cl-muted" style={{ fontSize: 11 }}>{s}</span>
                  <button className="cl-btn ghost sm" style={{ padding: 4 }}><Icon name="x" size={12} /></button>
                </div>
              ))}
            </div>
          </div>
        </FormSection>
      </div>

      {/* Side panel */}
      <div className="cl-stack">
        <div className="cl-card" style={{ position: 'sticky', top: 14 }}>
          <div className="cl-card-head"><div className="cl-card-title">Progreso del formulario</div></div>
          <div className="cl-card-body">
            {[
              ['Datos del local','done'],
              ['Condiciones comerciales','done'],
              ['Vigencia y comisión','done'],
              ['Observaciones','active'],
            ].map(([t, s], i) => (
              <div key={i} style={{ display: 'flex', gap: 10, padding: '7px 0', alignItems: 'center', fontSize: 12.5 }}>
                <div style={{ width: 18, height: 18, borderRadius: '50%',
                  background: s === 'done' ? '#16A34A' : s === 'active' ? '#fff' : '#fff',
                  border: s === 'active' ? '2px solid #0E5BFF' : s === 'done' ? '2px solid #16A34A' : '2px solid #DDE5E8',
                  display: 'grid', placeItems: 'center', color: '#fff', flex: '0 0 18px' }}>
                  {s === 'done' && <Icon name="check" size={10} color="#fff" />}
                </div>
                <span style={{ flex: 1, color: s === 'pending' ? '#8392A7' : '#0B1F33', fontWeight: s === 'active' ? 600 : 400 }}>{t}</span>
              </div>
            ))}
            <div className="cl-divider"></div>
            <div style={{ fontSize: 12 }}>
              <div className="cl-muted">Completitud</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4 }}>
                <div style={{ flex: 1, height: 6, background: '#EEF2F4', borderRadius: 3 }}>
                  <div style={{ width: '88%', height: '100%', background: '#16A34A', borderRadius: 3 }}></div>
                </div>
                <b>88%</b>
              </div>
            </div>
          </div>
        </div>
        <div className="cl-alert blue">
          <Icon name="info" size={15} />
          <span>Solo las captaciones <b>activas</b> pueden generar oportunidades comerciales. Envía a revisión cuando completes los datos.</span>
        </div>
      </div>
    </div>
  </AppShell>
  );
};

/* =================== REVISIÓN DE CAPTACIÓN (Broker) ===================
   Vista del BROKER NORMAL. Solo puede revisar captaciones enviadas por
   agentes bajo su supervisión. Acciones disponibles: aprobar, observar,
   rechazar o reasignar al mismo agente / a otro agente del mismo broker. */
const ScreenCaptacionReview = () => {
  const { navigate } = useNav();
  return (
  <AppShell role="Broker"
    crumbs={["ControlLocal", "Captaciones por revisar", "CAP-0231"]}
    title="Revisión de captación · CAP-0231"
    subtitle="Enviada por Carolina Vega (tu equipo) · hace 2 días"
    actions={
      <>
        <button onClick={() => navigate('bandeja-captaciones')} className="cl-btn"><Icon name="chevronLeft" size={13} /> Volver a la bandeja</button>
        <button className="cl-btn"><Icon name="history" size={13} /> Historial</button>
        <button onClick={() => navigate('bandeja-captaciones')} className="cl-btn danger"><Icon name="x" size={13} /> Rechazar</button>
        <button onClick={() => navigate('bandeja-captaciones')} className="cl-btn warn"><Icon name="alert" size={13} /> Observar</button>
        <button onClick={() => navigate('bandeja-captaciones')} className="cl-btn success"><Icon name="check" size={13} /> Aprobar</button>
      </>
    }>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 14 }}>
      <div className="cl-stack">
        <div className="cl-card">
          <div className="cl-card-head">
            <div>
              <div className="cl-card-title">Resumen de la captación</div>
              <div className="cl-card-sub">CAP-0231 · Pendiente de aprobación</div>
            </div>
            <StatusBadge label="Pendiente" />
          </div>
          <div className="cl-card-body" style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: 18 }}>
            <div>
              <div style={{ aspectRatio: '16/10', background: 'repeating-linear-gradient(135deg, #E8EDF3 0 8px, #F1F4F8 8px 16px)', borderRadius: 8, position: 'relative' }}>
                <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', fontFamily: 'monospace', fontSize: 11, color: '#8392A7', background: 'rgba(255,255,255,0.85)', width: 'fit-content', height: 'fit-content', margin: 'auto', padding: '6px 10px', borderRadius: 4 }}>foto del local</div>
              </div>
              <div style={{ marginTop: 14 }}>
                <div style={{ fontSize: 15, fontWeight: 600 }}>Av. Petit Thouars 1875</div>
                <div className="cl-muted" style={{ fontSize: 12, display:'flex', gap: 4, alignItems: 'center', marginTop: 3 }}>
                  <Icon name="mapPin" size={12} /> Jesús María, Lima · 95 m² · Servicios
                </div>
              </div>
            </div>
            <dl className="cl-kv">
              <dt>Propietario</dt><dd>Grupo Bermúdez E.I.R.L.</dd>
              <dt>RUC</dt><dd className="mono">20 502 998 110</dd>
              <dt>Precio referencial</dt><dd>USD 1 600 / mes</dd>
              <dt>Garantía</dt><dd>2 meses</dd>
              <dt>Plazo mínimo</dt><dd>24 meses</dd>
              <dt>Vigencia captación</dt><dd>20 May – 20 Ago 2026</dd>
              <dt>Comisión pactada</dt><dd>5.0% · sobre primera renta</dd>
              <dt>Exclusividad</dt><dd>Sí, exclusiva</dd>
              <dt>Agente</dt><dd>Carolina Vega <span className="cl-muted">(tu equipo)</span></dd>
            </dl>
          </div>
        </div>

        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Observaciones del agente</div></div>
          <div className="cl-card-body" style={{ fontSize: 12.5, color: '#4A5A6E', lineHeight: 1.55 }}>
            Local en buen estado estructural. Cuenta con licencia de funcionamiento vigente para giros de servicios. Propietario solicita evaluación financiera del cliente antes de aprobar visita.
          </div>
        </div>

        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Documentos adjuntos</div><span className="cl-muted" style={{ fontSize: 12 }}>3 archivos · 4.1 MB</span></div>
          <div className="cl-card-body" style={{ padding: 0 }}>
            {[
              ['Contrato_corretaje_firmado.pdf','1.4 MB','Aprobado'],
              ['Ficha_tecnica_local.pdf','1.2 MB','Aprobado'],
              ['Licencia_funcionamiento.pdf','1.5 MB','Observado'],
            ].map((d, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', padding: '12px 16px', borderTop: i === 0 ? 0 : '1px solid #EEF2F4', gap: 12 }}>
                <Icon name="fileText" size={16} color="#0284C7" />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 12.5, fontWeight: 500 }}>{d[0]}</div>
                  <div className="cl-muted" style={{ fontSize: 11 }}>{d[1]}</div>
                </div>
                <StatusBadge label={d[2]} />
                <button className="cl-btn sm"><Icon name="eye" size={12} /> Ver</button>
              </div>
            ))}
          </div>
        </div>

        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Observaciones del broker</div><span className="cl-muted" style={{ fontSize: 12 }}>Visible para el agente</span></div>
          <div className="cl-card-body">
            <Field label="Tu evaluación">
              <textarea defaultValue="Documentación parcialmente correcta. Solicito corregir la licencia de funcionamiento (vencida 03/2026) y confirmar comisión por escrito del propietario." rows={4} />
            </Field>
            <div className="cl-help">Si observas la captación, el agente recibirá estas notas para subsanar. Si la rechazas, la captación se cierra y no genera oportunidades.</div>
          </div>
        </div>
      </div>

      {/* Side panel */}
      <div className="cl-stack">
        <div className="cl-alert amber">
          <Icon name="alert" size={15} />
          <span><b>Licencia de funcionamiento vencida.</b> Confirma con el propietario antes de aprobar la captación.</span>
        </div>
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Línea de tiempo</div></div>
          <div className="cl-card-body">
            <Timeline items={[
              { time: '22 May · 14:08', title: 'Enviada a revisión', body: 'Por C. Vega', tone: 'done' },
              { time: '21 May · 16:22', title: 'Documentos cargados', body: '3 archivos · 4.1 MB', tone: 'done' },
              { time: '20 May · 10:00', title: 'Captación registrada', body: 'Borrador creado por C. Vega', tone: 'done' },
              { time: 'Pendiente', title: 'Aprobación del broker', body: 'Acción requerida', tone: 'active' },
            ]} />
          </div>
        </div>
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Acciones rápidas</div></div>
          <div className="cl-card-body" style={{ display:'flex', flexDirection: 'column', gap: 8 }}>
            <button className="cl-btn success" style={{ justifyContent: 'center' }}><Icon name="check" size={13} /> Aprobar captación</button>
            <button className="cl-btn warn" style={{ justifyContent: 'center' }}><Icon name="alert" size={13} /> Observar (devolver al agente)</button>
            <button className="cl-btn danger" style={{ justifyContent: 'center' }}><Icon name="x" size={13} /> Rechazar</button>
            <div className="cl-divider"></div>
            <button className="cl-btn ghost" style={{ color: '#0E5BFF', justifyContent: 'flex-start' }}><Icon name="users" size={13} /> Reasignar a otro agente de mi equipo</button>
            <div className="cl-help" style={{ marginTop: 2 }}>La reasignación está limitada a agentes bajo tu supervisión directa.</div>
          </div>
        </div>
      </div>
    </div>
  </AppShell>
  );
};

Object.assign(window, { ScreenCaptaciones, ScreenCaptacionForm, ScreenCaptacionReview, ScreenBandejaCaptaciones });

/* =================== BANDEJA DE CAPTACIONES (Broker) ===================
   El broker normal no registra captaciones. Su pantalla principal en este
   módulo es una bandeja con las captaciones enviadas por sus agentes que
   esperan revisión. Solo ve operaciones de agentes bajo su supervisión. */
function ScreenBandejaCaptaciones() {
  const { navigate } = useNav();
  return (
  <AppShell role="Broker"
    crumbs={["ControlLocal", "Bandejas de revisión", "Captaciones por revisar"]}
    title="Captaciones por revisar"
    subtitle="Solo se muestran captaciones de los agentes bajo tu supervisión"
    actions={<><button className="cl-btn"><Icon name="download" size={13} /> Exportar</button><button onClick={() => navigate('oportunidades')} className="cl-btn"><Icon name="target" size={13} /> Operaciones del equipo</button></>}>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 12, marginBottom: 14 }}>
      <MetricCard icon="clock" label="Pendientes de aprobación" value="9" tone="amber" footer="3 ingresadas hoy" />
      <MetricCard icon="alert" label="Observadas — esperan ajuste" value="4" tone="amber" footer="Acción del agente" />
      <MetricCard icon="check" label="Activas (mis agentes)" value="47" tone="green" />
      <MetricCard icon="users" label="Agentes activos" value="7" tone="info" />
    </div>
    <div className="cl-tabs">
      <div className="cl-tab active">Pendientes <span className="cl-tab-count">9</span></div>
      <div className="cl-tab">Observadas <span className="cl-tab-count">4</span></div>
      <div className="cl-tab">Activas <span className="cl-tab-count">47</span></div>
      <div className="cl-tab">Rechazadas <span className="cl-tab-count">3</span></div>
      <div className="cl-tab">Cerradas <span className="cl-tab-count">12</span></div>
    </div>
    <FilterBar search="Buscar por código, local, agente…">
      <select className="cl-select"><option>Agente (mi equipo)</option><option>Valentina Mora</option><option>Carolina Vega</option><option>Andrea Torres</option></select>
      <select className="cl-select"><option>Antigüedad</option><option>Hoy</option><option>+ 24h</option><option>+ 48h</option></select>
      <select className="cl-select"><option>Distrito</option></select>
    </FilterBar>
    <DataTable
      columns={[
        { label: "Código", render: r => <span className="mono" style={{ fontWeight: 600, color: '#0E5BFF' }}>{r.code}</span> },
        { label: "Local", render: r => <div><div style={{ fontWeight: 600 }}>{r.local}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.district} · {r.area} m² · {r.rubro}</div></div> },
        { label: "Propietario", key: "owner" },
        { label: "Agente del equipo", render: r => <div className="cl-flex"><span className="cl-avatar" style={{ width: 22, height: 22, fontSize: 9 }}>{r.agent.split(' ').map(x=>x[0]).join('')}</span><span style={{ fontSize: 12 }}>{r.agent}</span></div> },
        { label: "Enviada", render: r => <div><div style={{ fontSize: 12 }}>{r.sent}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.age}</div></div> },
        { label: "Comisión", render: r => <span><b>{r.comm}</b>%</span> },
        { label: "Estado", render: r => <StatusBadge label={r.status} /> },
        { label: "", render: () => <button onClick={() => navigate('captacion-review')} className="cl-btn sm primary">Revisar</button> },
      ]}
      rows={[
        { code: "CAP-0231", local: "Av. Petit Thouars 1875", district: "Jesús María", area: 95, rubro: "Servicios", owner: "Grupo Bermúdez", agent: "Carolina Vega", sent: "22 May 14:08", age: "hace 2d", comm: "5.0", status: "Pendiente" },
        { code: "CAP-0233", local: "Av. Caminos del Inca 1820", district: "Surco", area: 140, rubro: "Restaurante", owner: "A. Pereyra", agent: "Valentina Mora", sent: "23 May 11:30", age: "hace 1d", comm: "5.0", status: "Pendiente" },
        { code: "CAP-0236", local: "Av. Pardo 2120", district: "Miraflores", area: 78, rubro: "Moda", owner: "Inmobiliaria Pacífico", agent: "Andrea Torres", sent: "23 May 16:42", age: "hace 21h", comm: "4.5", status: "Pendiente" },
        { code: "CAP-0238", local: "Av. Aviación 4012", district: "San Borja", area: 180, rubro: "Retail", owner: "Comercial Andina", agent: "Jorge Marín", sent: "21 May 10:00", age: "hace 3d", comm: "5.0", status: "Observada" },
        { code: "CAP-0240", local: "Av. Tomás Marsano 3400", district: "Surco", area: 75, rubro: "Servicios", owner: "R. Linares", agent: "Carolina Vega", sent: "22 May 09:15", age: "hace 2d", comm: "4.5", status: "Pendiente" },
        { code: "CAP-0243", local: "Av. Brasil 2890", district: "Magdalena", area: 60, rubro: "Café", owner: "Inmobiliaria Pacífico", agent: "Paola Reyes", sent: "23 May 17:50", age: "hace 20h", comm: "5.0", status: "Observada" },
      ]} />
    <Pagination total={9} />
    <div className="cl-alert blue" style={{ marginTop: 14 }}>
      <Icon name="info" size={15} />
      <span>El broker normal puede <b>aprobar, observar, rechazar, cerrar, evaluar o reportar</b> operaciones — pero <b>nunca registrar</b> captaciones, locales, propietarios ni clientes. El registro es exclusivo del agente.</span>
    </div>
  </AppShell>
  );
}
