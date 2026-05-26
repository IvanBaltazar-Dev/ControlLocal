// Screens 23-24: Reportes e indicadores, Actividad reciente / auditoría

// Reportes: dual view por rol.
//  • Broker normal → Reportes de equipo (solo agentes a su cargo).
//  • Broker administrador → Reportes globales (todos los brokers).
//  El logo aparece en la cabecera de exportaciones (PDF / Excel).
const ScreenReportes = () => {
  const { role } = useNav();
  const isAdmin = role === "Broker administrador";
  return (
  <AppShell role={role || "Broker"}
    crumbs={isAdmin ? ["ControlLocal", "Reportes globales"] : ["ControlLocal", "Reportes de equipo"]}
    title={isAdmin ? "Reportes globales" : "Reportes de equipo"}
    subtitle={isAdmin
      ? "Indicadores consolidados de toda la corredora — todos los brokers y agentes"
      : "Desempeño de los agentes bajo tu supervisión · últimos 90 días"}
    actions={
      <>
        <select className="cl-select"><option>Últimos 90 días</option><option>Este mes</option><option>Este año</option></select>
        {isAdmin && <select className="cl-select"><option>Todos los brokers</option><option>Ricardo Salas</option><option>Felipe Andrade</option><option>Sandra Ríos</option></select>}
        <button className="cl-btn"><Icon name="download" size={13} /> Exportar PDF</button>
        <button className="cl-btn primary"><Icon name="download" size={13} /> Exportar Excel</button>
      </>
    }>
    {/* Cabecera para exportación — incluye el logo de ControlLocal */}
    <div className="cl-card" style={{ marginBottom: 14 }}>
      <div className="cl-card-body" style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 16px' }}>
        <BrandMark size={42} variant="primary" />
        <div style={{ flex: 1 }}>
          <div style={{ fontWeight: 600, fontSize: 14 }}>ControlLocal · {isAdmin ? "Reporte global de la corredora" : "Reporte de equipo — Ricardo Salas"}</div>
          <div className="cl-muted" style={{ fontSize: 12 }}>Generado el 24 May 2026 · Período 24 Feb – 24 May 2026 {isAdmin ? "· 8 brokers · 42 agentes" : "· 7 agentes bajo supervisión"}</div>
        </div>
        <span className="cl-pill" style={{ background: '#E8F0FF', color: '#0E5BFF' }}>Vista previa de exportación</span>
      </div>
    </div>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5,1fr)', gap: 12, marginBottom: 14 }}>
      <MetricCard icon="target" label="Oportunidades abiertas" value="22" delta="+8% vs período ant." tone="blue" />
      <MetricCard icon="fileText" label="Con solicitud creada" value="38" delta="+12%" tone="info" />
      <MetricCard icon="check" label="Cerradas exitosas" value="14" delta="+4 cierres" tone="green" />
      <MetricCard icon="x" label="No favorables" value="6" delta="−2" deltaDir="down" tone="red" />
      <MetricCard icon="flag" label="No continúan" value="4" delta="estable" deltaDir="flat" tone="navy" />
    </div>

    <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 14 }}>
      <div className="cl-card">
        <div className="cl-card-head"><div className="cl-card-title">Evolución de oportunidades (12 semanas)</div><span className="cl-muted" style={{ fontSize: 12 }}>Abiertas vs cerradas</span></div>
        <div className="cl-card-body">
          <svg width="100%" height="220" viewBox="0 0 720 220">
            {[0, 0.25, 0.5, 0.75, 1].map((t, i) => (
              <g key={i}>
                <line x1="40" x2="700" y1={20 + 160 * t} y2={20 + 160 * t} stroke="#EEF2F4" />
                <text x="32" y={24 + 160 * t} textAnchor="end" fontSize="10" fill="#8392A7">{Math.round(50 - 50 * t)}</text>
              </g>
            ))}
            {(() => {
              const open = [22, 28, 24, 32, 30, 38, 36, 42, 40, 45, 42, 48];
              const closed = [4, 6, 5, 8, 7, 12, 10, 14, 13, 16, 14, 18];
              const x = (i) => 40 + (660 / 11) * i;
              const y = (v) => 20 + (1 - v / 50) * 160;
              const path = (a) => a.map((v, i) => `${i ? 'L' : 'M'} ${x(i)} ${y(v)}`).join(' ');
              return (
                <>
                  <path d={path(open) + ` L ${x(11)} 180 L ${x(0)} 180 Z`} fill="#0E5BFF" opacity="0.10" />
                  <path d={path(open)} stroke="#0E5BFF" strokeWidth="2" fill="none" />
                  {open.map((v, i) => <circle key={i} cx={x(i)} cy={y(v)} r="3" fill="#0E5BFF" />)}
                  <path d={path(closed)} stroke="#16A34A" strokeWidth="2" fill="none" />
                  {closed.map((v, i) => <circle key={i} cx={x(i)} cy={y(v)} r="3" fill="#16A34A" />)}
                  {['S1','S2','S3','S4','S5','S6','S7','S8','S9','S10','S11','S12'].map((l, i) => (
                    <text key={i} x={x(i)} y={210} textAnchor="middle" fontSize="10" fill="#8392A7">{l}</text>
                  ))}
                </>
              );
            })()}
          </svg>
          <div style={{ display: 'flex', gap: 18, marginTop: 6, fontSize: 12 }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><span style={{ width: 10, height: 10, borderRadius: 2, background: '#0E5BFF' }}></span> Abiertas</span>
            <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><span style={{ width: 10, height: 10, borderRadius: 2, background: '#16A34A' }}></span> Cerradas exitosas</span>
          </div>
        </div>
      </div>

      <div className="cl-card">
        <div className="cl-card-head"><div className="cl-card-title">Embudo de conversión</div></div>
        <div className="cl-card-body">
          {[
            ['Oportunidades creadas','110','100%','#0B1F33'],
            ['Con visita realizada','78','71%','#0E5BFF'],
            ['Con solicitud creada','52','47%','#0284C7'],
            ['Aprobadas','32','29%','#F59E0B'],
            ['Cerradas exitosas','22','20%','#16A34A'],
          ].map(([t, n, p, c], i) => (
            <div key={i} style={{ marginBottom: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}>
                <span>{t}</span><span><b>{n}</b> <span className="cl-muted">· {p}</span></span>
              </div>
              <div style={{ height: 10, background: '#EEF2F4', borderRadius: 4 }}><div style={{ width: p, height: '100%', background: c, borderRadius: 4 }}></div></div>
            </div>
          ))}
          <div className="cl-divider"></div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
            <span className="cl-muted">Tasa Oportunidad → Visita</span><b>71%</b>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginTop: 4 }}>
            <span className="cl-muted">Tasa Oportunidad → Solicitud</span><b>47%</b>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginTop: 4 }}>
            <span className="cl-muted">Tasa Solicitud → Cierre exitoso</span><b style={{ color: '#16A34A' }}>42%</b>
          </div>
        </div>
      </div>
    </div>

    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginTop: 14 }}>
      <div className="cl-card">
        <div className="cl-card-head"><div className="cl-card-title">Desempeño por agente</div></div>
        <div className="cl-card-body" style={{ padding: 0 }}>
          <table className="cl-table">
            <thead><tr><th>Agente</th><th>Oport.</th><th>Visitas</th><th>Solic.</th><th>Cierres</th><th>Conv.</th></tr></thead>
            <tbody>
              {[
                ['Valentina Mora','12','9','7','5','42%','green'],
                ['Daniel Romero','10','7','5','3','30%','amber'],
                ['Carolina Vega','9','6','4','3','33%','amber'],
                ['Matías León','8','5','3','2','25%','amber'],
                ['Andrea Torres','7','4','2','1','14%','red'],
              ].map((r,i)=>(
                <tr key={i}>
                  <td><div className="cl-flex"><span className="cl-avatar" style={{ width:24, height:24, fontSize:10 }}>{r[0].split(' ').map(x=>x[0]).join('')}</span>{r[0]}</div></td>
                  <td><b>{r[1]}</b></td><td>{r[2]}</td><td>{r[3]}</td><td><b>{r[4]}</b></td>
                  <td><StatusBadge label={r[5]} tone={r[6]} plain /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="cl-card">
        <div className="cl-card-head"><div className="cl-card-title">Avance comercial por propiedad</div></div>
        <div className="cl-card-body" style={{ padding: 0 }}>
          <table className="cl-table">
            <thead><tr><th>Local</th><th>Oport.</th><th>Visitas</th><th>Cierre</th><th>Avance</th></tr></thead>
            <tbody>
              {[
                ['Av. La Marina 245','4','6','Sí','100','green'],
                ['Calle Schell 412','3','5','En curso','70','amber'],
                ['Jr. Berlín 230','3','4','En curso','55','amber'],
                ['Av. Petit Thouars 1875','2','2','En curso','35','blue'],
                ['Av. Aviación 4012','2','3','No favorable','20','red'],
              ].map((r,i)=>(
                <tr key={i}>
                  <td>{r[0]}</td><td>{r[1]}</td><td>{r[2]}</td><td>{r[3]}</td>
                  <td style={{ width: 110 }}><div style={{ height: 6, background: '#EEF2F4', borderRadius: 3 }}><div style={{ width: r[4]+'%', height: '100%', background: { green:'#16A34A', amber:'#F59E0B', blue:'#0E5BFF', red:'#DC2626' }[r[5]], borderRadius: 3 }}></div></div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 14, marginTop: 14 }}>
      <div className="cl-card">
        <div className="cl-card-head"><div className="cl-card-title">Cierres por resultado</div></div>
        <div className="cl-card-body" style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <DonutChart segments={[
            { value: 14, color: '#16A34A' },
            { value: 6, color: '#DC2626' },
            { value: 4, color: '#8392A7' },
          ]} size={130} />
          <div style={{ flex: 1, fontSize: 12 }}>
            {[
              ['#16A34A','Exitosa','14','58%'],
              ['#DC2626','No favorable','6','25%'],
              ['#8392A7','No continúa','4','17%'],
            ].map(([c,n,v,p],i) => (
              <div key={i} style={{ display:'flex', alignItems:'center', gap:8, padding:'4px 0' }}>
                <span style={{ width: 9, height: 9, borderRadius: 2, background: c }}></span>
                <span style={{ flex: 1 }}>{n}</span>
                <b>{v}</b> <span className="cl-muted">{p}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
      <div className="cl-card">
        <div className="cl-card-head"><div className="cl-card-title">Tiempo promedio por etapa</div></div>
        <div className="cl-card-body">
          {[
            ['Captación → Oportunidad','3.2 días','blue'],
            ['Oportunidad → Visita','5.1 días','info'],
            ['Visita → Solicitud','4.4 días','amber'],
            ['Solicitud → Cierre','6.8 días','green'],
          ].map(([t,v,c],i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '7px 0', borderTop: i ? '1px solid #EEF2F4' : 0 }}>
              <div style={{ width: 8, height: 8, borderRadius: 50, background: { blue:'#0E5BFF', info:'#0284C7', amber:'#F59E0B', green:'#16A34A' }[c] }}></div>
              <span style={{ fontSize: 12, flex: 1 }}>{t}</span><b>{v}</b>
            </div>
          ))}
        </div>
      </div>
      <div className="cl-card">
        <div className="cl-card-head"><div className="cl-card-title">Motivos de no continuidad</div></div>
        <div className="cl-card-body">
          {[
            ['Precio fuera de presupuesto','#0E5BFF','42%'],
            ['Ubicación no convenció','#0284C7','22%'],
            ['Encontró otra opción','#F59E0B','18%'],
            ['Cambio de plan comercial','#8392A7','12%'],
            ['Otros','#DC2626','6%'],
          ].map(([t,c,p],i) => (
            <div key={i} style={{ marginBottom: 9 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}><span>{t}</span><b>{p}</b></div>
              <div style={{ height: 5, background: '#EEF2F4', borderRadius: 3 }}><div style={{ width: p, height: '100%', background: c, borderRadius: 3 }}></div></div>
            </div>
          ))}
        </div>
      </div>
    </div>
  </AppShell>
  );
};

/* =================== AUDITORÍA GLOBAL ===================
   Solo el broker administrador entra aquí. Es el registro central de
   trazabilidad: usuario, acción, módulo, antes/después. El broker normal
   no ve esta pantalla (no se renderiza en su sidebar). */
const ScreenActividad = () => (
  <AppShell role="Broker administrador"
    crumbs={["ControlLocal", "Supervisión", "Auditoría global"]}
    title="Auditoría global"
    subtitle="Registro central de acciones sensibles de todos los brokers y agentes — incluye reasignaciones e intervenciones"
    actions={<button className="cl-btn"><Icon name="download" size={13} /> Exportar registro</button>}>
    <FilterBar search="Buscar por usuario, módulo, referencia…">
      <select className="cl-select"><option>Módulo</option><option>Captación</option><option>Oportunidad</option><option>Solicitud</option><option>Documentos</option><option>Cierre</option></select>
      <select className="cl-select"><option>Acción</option><option>Creación</option><option>Aprobación</option><option>Rechazo</option><option>Edición</option></select>
      <select className="cl-select"><option>Usuario</option></select>
      <select className="cl-select"><option>Período</option><option>Hoy</option><option>Últimos 7 días</option><option>Últimos 30 días</option></select>
    </FilterBar>
    <DataTable
      columns={[
        { label: "Fecha y hora", render: r => <div><div style={{ fontWeight: 500, fontSize: 12 }}>{r.date}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.time}</div></div> },
        { label: "Usuario", render: r => <div className="cl-flex"><span className="cl-avatar" style={{ width: 24, height: 24, fontSize: 10 }}>{r.user.split(' ').map(x=>x[0]).join('').slice(0,2)}</span><div><div style={{ fontSize: 12 }}>{r.user}</div><div className="cl-muted" style={{ fontSize: 11 }}>{r.role}</div></div></div> },
        { label: "Módulo", render: r => <StatusBadge label={r.module} tone="blue" plain /> },
        { label: "Acción", render: r => <span style={{ fontSize: 12, fontWeight: 500 }}>{r.action}</span> },
        { label: "Referencia", render: r => <span className="mono" style={{ color: '#0E5BFF' }}>{r.ref}</span> },
        { label: "Estado anterior", render: r => r.before ? <StatusBadge label={r.before} /> : <span className="cl-muted">—</span> },
        { label: "Estado nuevo", render: r => r.after ? <StatusBadge label={r.after} /> : <span className="cl-muted">—</span> },
        { label: "Observación", render: r => <span style={{ fontSize: 12, color: '#4A5A6E' }}>{r.note}</span> },
      ]}
      rows={[
        { date: "24 May 2026", time: "11:42", user: "Ricardo Salas", role: "Broker", module: "Captación", action: "Aprobada", ref: "CAP-0218", before: "Pendiente", after: "Activa", note: "Documentación verificada" },
        { date: "24 May 2026", time: "09:14", user: "Valentina Mora", role: "Agente", module: "Oportunidad", action: "Cerrada exitosa", ref: "OP-1083", before: "Solicitud creada", after: "Cerrada exitosa", note: "Contrato firmado · USD 2 750" },
        { date: "24 May 2026", time: "08:32", user: "Valentina Mora", role: "Agente", module: "Interacción", action: "Registrada", ref: "OP-1098", before: null, after: null, note: "Llamada · interesado" },
        { date: "23 May 2026", time: "17:30", user: "Daniel Romero", role: "Agente", module: "Solicitud", action: "Creada", ref: "SOL-0428", before: null, after: "Registrada", note: "Desde OP-1083" },
        { date: "23 May 2026", time: "14:32", user: "Valentina Mora", role: "Agente", module: "Documentos", action: "Cargado", ref: "SOL-0425", before: "Pendiente", after: "Aprobado", note: "Carta propuesta firmada" },
        { date: "23 May 2026", time: "10:14", user: "Ricardo Salas", role: "Broker", module: "Solicitud", action: "Observada", ref: "SOL-0428", before: "En revisión", after: "Observada", note: "Falta firma del contador" },
        { date: "22 May 2026", time: "17:30", user: "Alejandro Téllez", role: "Broker administrador", module: "Administración", action: "Reasignación entre brokers", ref: "AG-DR", before: "M. Quintero", after: "R. Salas", note: "Cese del broker origen — D. Romero reasignado" },
        { date: "22 May 2026", time: "17:25", user: "Mariana Quintero", role: "Broker", module: "Administración", action: "Broker desactivado", ref: "BR-MQ", before: "Activo", after: "Inactivo", note: "Cese definitivo registrado por admin" },
        { date: "22 May 2026", time: "14:08", user: "Daniel Romero", role: "Agente", module: "Captación", action: "Enviada a revisión", ref: "CAP-0231", before: "Borrador", after: "Pendiente", note: "Adjuntó 3 documentos" },
        { date: "22 May 2026", time: "12:00", user: "Carolina Vega", role: "Agente", module: "Cierre", action: "No favorable", ref: "OP-1077", before: "Solicitud creada", after: "Cerrada no favorable", note: "Evaluación rechazada" },
        { date: "21 May 2026", time: "16:22", user: "Daniel Romero", role: "Agente", module: "Documentos", action: "Cargado", ref: "CAP-0231", before: null, after: "Pendiente", note: "3 archivos · 4.1 MB" },
      ]} />
    <Pagination total={1284} />
  </AppShell>
);

Object.assign(window, { ScreenReportes, ScreenActividad });
