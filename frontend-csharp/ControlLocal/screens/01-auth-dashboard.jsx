// Screens 1-4: Login, Recuperar contraseña, Dashboard (×3 variants by rol), Mi perfil

const ScreenLogin = () => {
  const { navigate } = useNav();
  const [pickedRole, setPickedRole] = React.useState("Agente inmobiliario");
  const roles = [
    ["Broker administrador", "Administra brokers, audita y reasigna agentes entre brokers"],
    ["Broker",               "Supervisa a sus agentes · revisa captaciones, evalúa solicitudes"],
    ["Agente inmobiliario",  "Operación comercial · captaciones, oportunidades y cierres"],
  ];
  return (
  <div style={{
    width: '100%', height: '100%', display: 'flex', background: '#F6F8FC',
    fontFamily: "'Inter', -apple-system, sans-serif", color: '#0B1F33'
  }}>
    {/* Left brand panel */}
    <div style={{
      width: '46%', background: 'linear-gradient(160deg, #0B1F33 0%, #14304F 100%)',
      color: '#fff', padding: '48px', display: 'flex', flexDirection: 'column',
      position: 'relative', overflow: 'hidden'
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <BrandMark size={40} variant="primary" />
        <div>
          <div style={{ fontWeight: 600, fontSize: 18 }}>ControlLocal</div>
          <div style={{ fontSize: 11, letterSpacing: '0.06em', textTransform: 'uppercase', color: '#6E7E94' }}>Sistema de Corretaje Comercial</div>
        </div>
      </div>
      <div style={{ marginTop: 'auto' }}>
        <div style={{ fontSize: 28, fontWeight: 600, lineHeight: 1.2, letterSpacing: '-0.02em', maxWidth: 380 }}>
          Gestión integral del proceso de alquiler comercial.
        </div>
        <div style={{ fontSize: 13, color: '#8FA0B8', marginTop: 14, maxWidth: 380, lineHeight: 1.5 }}>
          Captación, oportunidades, visitas, solicitudes, evaluación y cierre — desde un único panel para brokers y agentes inmobiliarios.
        </div>
        <div style={{ display: 'flex', gap: 24, marginTop: 28, fontSize: 11, color: '#6E7E94' }}>
          <div><div style={{ color: '#fff', fontSize: 16, fontWeight: 600 }}>248</div>Locales activos</div>
          <div><div style={{ color: '#fff', fontSize: 16, fontWeight: 600 }}>34</div>Oportunidades</div>
          <div><div style={{ color: '#fff', fontSize: 16, fontWeight: 600 }}>14</div>Cierres este mes</div>
        </div>
      </div>
      {/* decorative diagonal grid */}
      <svg style={{ position: 'absolute', right: -60, top: -40, opacity: 0.06 }} width="400" height="400" viewBox="0 0 400 400">
        {Array.from({ length: 20 }).map((_, i) => (
          <line key={i} x1={i * 30} y1="0" x2="0" y2={i * 30} stroke="#fff" strokeWidth="1" />
        ))}
      </svg>
    </div>
    {/* Right form */}
    <div style={{ flex: 1, display: 'grid', placeItems: 'center', padding: 48 }}>
      <div style={{ width: 380 }}>
        <div style={{ fontSize: 11, letterSpacing: '0.08em', textTransform: 'uppercase', color: '#8392A7' }}>Bienvenido</div>
        <div style={{ fontSize: 24, fontWeight: 600, letterSpacing: '-0.015em', marginTop: 4 }}>Inicio de sesión</div>
        <div style={{ fontSize: 13, color: '#4A5A6E', marginTop: 6 }}>
          Ingresa con tus credenciales de la corredora para acceder al panel.
        </div>

        <div style={{ marginTop: 24 }}>
          <Field label="Usuario o correo corporativo" required>
            <input defaultValue="vmora@controllocal.pe" />
          </Field>
        </div>
        <div style={{ marginTop: 14 }}>
          <Field label="Contraseña" required>
            <div style={{ position: 'relative' }}>
              <input type="password" defaultValue="••••••••••" style={{ paddingRight: 38 }} />
              <div style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)', color: '#8392A7', cursor: 'pointer' }}>
                <Icon name="eye" size={15} />
              </div>
            </div>
          </Field>
        </div>

        {/* MVP: 'Mantener sesión activa' queda como mejora futura, no se muestra.
            Recuperación pública de contraseña fue removida — el restablecimiento
            es administrado internamente por el equipo de soporte. */}
        <div style={{ marginTop: 12, padding: '10px 12px', background: '#F6F8FC', borderRadius: 7, border: '1px solid #EEF2F4', fontSize: 11.5, color: '#4A5A6E', display: 'flex', gap: 8, alignItems: 'flex-start' }}>
          <Icon name="lock" size={14} color="#8392A7" />
          <span>¿No recuerdas tu contraseña? <a onClick={() => navigate('recover')} style={{ color: '#0E5BFF', fontWeight: 500, cursor: 'pointer' }}>Solícita un restablecimiento</a> al equipo de soporte interno.</span>
        </div>

        <div style={{ marginTop: 18 }}>
          <div style={{ fontSize: 11, letterSpacing: '0.08em', textTransform: 'uppercase', color: '#8392A7', marginBottom: 8 }}>Ingresar como — perfil de prueba</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {roles.map(([r, sub]) => (
              <label key={r} style={{
                display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px',
                border: `1px solid ${pickedRole === r ? '#0E5BFF' : '#DDE5E8'}`, borderRadius: 7,
                background: pickedRole === r ? '#F1F6FF' : '#fff', cursor: 'pointer'
              }}>
                <input type="radio" name="role" checked={pickedRole === r} onChange={() => setPickedRole(r)} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 12.5, fontWeight: 600 }}>{r}</div>
                  <div style={{ fontSize: 11, color: '#8392A7', marginTop: 1 }}>{sub}</div>
                </div>
              </label>
            ))}
          </div>
        </div>

        <button
          onClick={() => navigate('dashboard', { role: pickedRole })}
          className="cl-btn primary" style={{ width: '100%', marginTop: 18, padding: '11px 14px', justifyContent: 'center' }}>
          Ingresar
          <Icon name="arrowRight" size={14} />
        </button>

        <div style={{ marginTop: 22, padding: 12, background: '#F6F8FC', borderRadius: 8, fontSize: 11.5, color: '#4A5A6E', display: 'flex', gap: 8 }}>
          <Icon name="lock" size={14} color="#0284C7" />
          <span>Acceso restringido. Todas las acciones quedan registradas en la auditoría global.</span>
        </div>

        <div style={{ marginTop: 28, fontSize: 11, color: '#8392A7', textAlign: 'center' }}>
          v2.5.0 · Soporte interno: soporte@controllocal.pe
        </div>
      </div>
    </div>
  </div>
  );
};

// El restablecimiento público de contraseña NO existe en el MVP. Esta
// pantalla muestra al usuario la vía administrada internamente: el equipo
// de soporte (o el broker administrador) genera la contraseña temporal.
const ScreenRecover = () => {
  const { navigate } = useNav();
  return (
  <div style={{
    width: '100%', height: '100%', background: '#F6F8FC',
    fontFamily: "'Inter', -apple-system, sans-serif", color: '#0B1F33',
    display: 'grid', placeItems: 'center', padding: 40
  }}>
    <div style={{ width: 460, background: '#fff', borderRadius: 12, border: '1px solid #DDE5E8', padding: 32, boxShadow: '0 8px 28px rgba(11,31,51,0.06)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 26 }}>
        <BrandMark size={32} variant="primary" />
        <div style={{ fontWeight: 600, fontSize: 15 }}>ControlLocal</div>
      </div>

      <div style={{ width: 44, height: 44, borderRadius: 10, background: '#E8F0FF', color: '#0E5BFF', display: 'grid', placeItems: 'center', marginBottom: 14 }}>
        <Icon name="lock" size={20} />
      </div>
      <div style={{ fontSize: 20, fontWeight: 600, letterSpacing: '-0.015em' }}>Restablecimiento administrado</div>
      <div style={{ fontSize: 13, color: '#4A5A6E', marginTop: 8, lineHeight: 1.55 }}>
        En esta versión del sistema la recuperación automática de contraseña no está disponible. El restablecimiento lo gestiona internamente el equipo de soporte o el broker administrador.
      </div>

      <div style={{ marginTop: 18, padding: 14, background: '#F6F8FC', borderRadius: 8, border: '1px solid #EEF2F4' }}>
        <div style={{ fontSize: 11, color: '#8392A7', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>Cómo solicitarlo</div>
        <ol style={{ margin: 0, paddingLeft: 18, fontSize: 12.5, color: '#0B1F33', lineHeight: 1.7 }}>
          <li>Escribe a <b>soporte@controllocal.pe</b> desde tu correo corporativo, o</li>
          <li>Contacta a tu <b>broker administrador</b> para registrar el restablecimiento.</li>
          <li>Recibirás una contraseña temporal que deberás cambiar al iniciar sesión.</li>
        </ol>
      </div>

      <div className="cl-alert blue" style={{ marginTop: 16 }}>
        <Icon name="info" size={15} />
        <span>El flujo público de «enviar enlace» queda como mejora futura. Por seguridad, todas las solicitudes quedan registradas en la <b>auditoría global</b>.</span>
      </div>

      <button onClick={() => navigate('login')} className="cl-btn primary" style={{ width: '100%', marginTop: 18, padding: '10px 14px', justifyContent: 'center' }}>
        <Icon name="chevronLeft" size={13} /> Volver al inicio de sesión
      </button>
    </div>
  </div>
  );
};

/* =================== DASHBOARD =================== */
const DashboardBody = ({ role }) => {
  if (role === "Broker administrador") {
    return (
      <>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 14, marginBottom: 14 }}>
          <MetricCard icon="briefcase" label="Brokers activos" value="8" delta="+1 vs mes anterior" tone="navy" />
          <MetricCard icon="users" label="Agentes activos" value="42" delta="+4 vs mes anterior" tone="blue" />
          <MetricCard icon="pin" label="Captaciones vigentes" value="186" delta="+12%" tone="info" />
          <MetricCard icon="target" label="Oportunidades abiertas" value="64" delta="+8%" tone="amber" />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 14 }}>
          <div className="cl-card">
            <div className="cl-card-head">
              <div>
                <div className="cl-card-title">Actividad comercial — últimos 30 días</div>
                <div className="cl-card-sub">Volumen por etapa del proceso</div>
              </div>
              <select className="cl-select" style={{ width: 140 }}><option>Últimos 30 días</option></select>
            </div>
            <div className="cl-card-body">
              <BarChart data={[18, 24, 21, 30, 27, 36, 32, 41, 38, 44, 39, 48]} height={180}
                labels={['S1','S2','S3','S4','S5','S6','S7','S8','S9','S10','S11','S12']} />
            </div>
          </div>
          <div className="cl-card">
            <div className="cl-card-head">
              <div className="cl-card-title">Estados de oportunidad</div>
            </div>
            <div className="cl-card-body" style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
              <DonutChart segments={[
                { value: 22, color: '#0E5BFF' },
                { value: 18, color: '#0284C7' },
                { value: 14, color: '#16A34A' },
                { value: 6, color: '#DC2626' },
                { value: 4, color: '#8392A7' },
              ]} />
              <div style={{ flex: 1, fontSize: 12 }}>
                {[
                  ['#0E5BFF','Abierta','22'],
                  ['#0284C7','Solicitud creada','18'],
                  ['#16A34A','Cerrada exitosa','14'],
                  ['#DC2626','No favorable','6'],
                  ['#8392A7','No continúa','4'],
                ].map(([c,n,v],i) => (
                  <div key={i} style={{ display:'flex', alignItems:'center', gap:8, padding:'5px 0' }}>
                    <span style={{ width: 9, height: 9, borderRadius: 2, background: c }}></span>
                    <span style={{ flex: 1 }}>{n}</span>
                    <b>{v}</b>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginTop: 14 }}>
          <div className="cl-card">
            <div className="cl-card-head"><div className="cl-card-title">Desempeño por broker</div></div>
            <div className="cl-card-body" style={{ padding: 0 }}>
              <table className="cl-table">
                <thead><tr><th>Broker</th><th>Agentes</th><th>Captac.</th><th>Cierres</th><th>Conversión</th></tr></thead>
                <tbody>
                  {[
                    ['Ricardo Salas','7','38','9','24%'],
                    ['Mariana Quintero','6','31','8','26%'],
                    ['Felipe Andrade','5','27','5','19%'],
                    ['Sandra Ríos','4','22','4','18%'],
                  ].map((r,i)=>(
                    <tr key={i}>
                      <td><div className="cl-flex"><span className="cl-avatar" style={{ width:24, height:24, fontSize:10 }}>{r[0].split(' ').map(x=>x[0]).join('')}</span>{r[0]}</div></td>
                      <td>{r[1]}</td><td>{r[2]}</td><td><b>{r[3]}</b></td>
                      <td><StatusBadge label={r[4]} tone={parseInt(r[4])>=22?'green':parseInt(r[4])>=18?'amber':'red'} plain /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div className="cl-card">
            <div className="cl-card-head"><div className="cl-card-title">Actividad reciente</div><a className="cl-btn ghost sm" style={{ color: '#0E5BFF' }}>Ver todo</a></div>
            <div className="cl-card-body">
              <Timeline items={[
                { time: 'Hoy · 11:42', title: 'Captación aprobada', body: 'CAP-0218 · Local Av. La Marina 245 · Aprobada por R. Salas', tone: 'done' },
                { time: 'Hoy · 09:14', title: 'Oportunidad cerrada exitosa', body: 'OP-1083 · Cliente Inversiones Trébol S.A.C.', tone: 'done' },
                { time: 'Ayer · 17:30', title: 'Nuevo agente registrado', body: 'Daniel Romero asignado al broker M. Quintero' },
                { time: 'Ayer · 14:08', title: 'Solicitud observada', body: 'SOL-0421 · 3 documentos pendientes', tone: 'warn' },
              ]} />
            </div>
          </div>
        </div>
      </>
    );
  }

  if (role === "Broker") {
    return (
      <>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 14, marginBottom: 14 }}>
          <MetricCard icon="pin" label="Captaciones por revisar" value="9" delta="3 nuevas hoy" tone="amber" deltaDir="up" />
          <MetricCard icon="fileText" label="Solicitudes por evaluar" value="6" delta="2 vencen hoy" tone="red" deltaDir="up" />
          <MetricCard icon="target" label="Oportunidades activas" value="22" delta="+3 esta semana" tone="blue" />
          <MetricCard icon="check" label="Cierres recientes" value="5" delta="+2 vs semana ant." tone="green" />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <div className="cl-card">
            <div className="cl-card-head"><div className="cl-card-title">Pendientes prioritarios</div><span className="cl-badge red">8 críticos</span></div>
            <div className="cl-card-body" style={{ padding: 0 }}>
              <table className="cl-table">
                <thead><tr><th>Tipo</th><th>Referencia</th><th>Agente</th><th>Antigüedad</th><th></th></tr></thead>
                <tbody>
                  {[
                    ['Captación','CAP-0231','D. Romero','2d','red'],
                    ['Solicitud','SOL-0428','C. Vega','1d','amber'],
                    ['Captación','CAP-0229','M. León','1d','amber'],
                    ['Solicitud','SOL-0425','V. Mora','3h','blue'],
                    ['Captación','CAP-0226','A. Torres','5h','blue'],
                  ].map((r,i)=>(
                    <tr key={i}>
                      <td>{r[0]}</td>
                      <td className="mono">{r[1]}</td>
                      <td>{r[2]}</td>
                      <td><StatusBadge label={r[3]} tone={r[4]} plain /></td>
                      <td><button className="cl-btn sm">Revisar</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div className="cl-card">
            <div className="cl-card-head"><div className="cl-card-title">Conversión del embudo</div></div>
            <div className="cl-card-body">
              {[
                ['Oportunidades abiertas','64','100%','#0E5BFF'],
                ['Con visita realizada','41','64%','#0284C7'],
                ['Con solicitud creada','28','44%','#F59E0B'],
                ['Cerradas exitosas','14','22%','#16A34A'],
              ].map((r,i)=>(
                <div key={i} style={{ marginBottom: 12 }}>
                  <div style={{ display:'flex', justifyContent:'space-between', fontSize: 12, marginBottom: 4 }}>
                    <span>{r[0]}</span><span><b>{r[1]}</b> <span className="cl-muted">· {r[2]}</span></span>
                  </div>
                  <div style={{ height: 8, background: '#EEF2F4', borderRadius: 4, overflow: 'hidden' }}>
                    <div style={{ width: r[2], height: '100%', background: r[3], borderRadius: 4 }}></div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
        <div className="cl-card" style={{ marginTop: 14 }}>
          <div className="cl-card-head"><div className="cl-card-title">Cierres recientes</div></div>
          <div className="cl-card-body" style={{ padding: 0 }}>
            <table className="cl-table">
              <thead><tr><th>Oportunidad</th><th>Cliente</th><th>Local</th><th>Resultado</th><th>Agente</th><th>Cerrado</th></tr></thead>
              <tbody>
                {[
                  ['OP-1083','Inversiones Trébol S.A.C.','Av. La Marina 245','Cerrada exitosa','V. Mora','Hoy 09:14'],
                  ['OP-1077','Carla Espinoza','Calle Schell 412','Cerrada exitosa','C. Vega','22 May'],
                  ['OP-1071','Bodegas del Norte E.I.R.L.','Av. Petit Thouars 1875','Cerrada no continúa','D. Romero','20 May'],
                ].map((r,i)=>(
                  <tr key={i}>
                    <td className="mono">{r[0]}</td><td>{r[1]}</td><td>{r[2]}</td>
                    <td><StatusBadge label={r[3]} /></td><td>{r[4]}</td><td className="muted">{r[5]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </>
    );
  }

  // Agente
  return (
    <>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 14, marginBottom: 14 }}>
        <MetricCard icon="pin" label="Mis captaciones" value="14" footer="9 activas · 3 pendientes · 2 observadas" tone="info" />
        <MetricCard icon="target" label="Oportunidades en curso" value="8" delta="+2 esta semana" tone="blue" />
        <MetricCard icon="calendar" label="Visitas programadas" value="5" footer="Próxima: hoy 16:00" tone="amber" />
        <MetricCard icon="fileText" label="Solicitudes pendientes" value="3" footer="2 en revisión · 1 observada" tone="red" />
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: 14 }}>
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Mis oportunidades activas</div><a className="cl-btn ghost sm" style={{ color: '#0E5BFF' }}>Ver todas</a></div>
          <div className="cl-card-body" style={{ padding: 0 }}>
            <table className="cl-table">
              <thead><tr><th>Código</th><th>Cliente</th><th>Local</th><th>Estado</th><th>Próx. acción</th></tr></thead>
              <tbody>
                {[
                  ['OP-1098','Inversiones Trébol','Av. La Marina 245','En seguimiento','Visita · hoy 16:00'],
                  ['OP-1094','Boutique Lila','Calle Schell 412','Solicitud creada','Esperar evaluación'],
                  ['OP-1089','Bodegas del Norte','Av. Petit Thouars 1875','Abierta','Registrar interacción'],
                  ['OP-1085','Café Lima','Jr. Berlín 230','En seguimiento','Programar visita'],
                ].map((r,i)=>(
                  <tr key={i}>
                    <td className="mono">{r[0]}</td><td>{r[1]}</td><td>{r[2]}</td>
                    <td><StatusBadge label={r[3]} /></td><td className="muted">{r[4]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        <div className="cl-card">
          <div className="cl-card-head"><div className="cl-card-title">Agenda de hoy</div><span className="cl-badge blue">5 visitas</span></div>
          <div className="cl-card-body">
            <Timeline items={[
              { time: '09:30', title: 'Visita · Café Lima', body: 'Jr. Berlín 230, Miraflores · OP-1085', tone: 'done' },
              { time: '11:00', title: 'Llamada · Inversiones Trébol', body: 'Confirmar contrato · OP-1098', tone: 'done' },
              { time: '14:30', title: 'Visita · Boutique Lila', body: 'Calle Schell 412 · OP-1094', tone: 'active' },
              { time: '16:00', title: 'Visita · Inversiones Trébol', body: 'Av. La Marina 245 · OP-1098' },
              { time: '18:00', title: 'Registro de captación', body: 'CAP-0233 pendiente de envío' },
            ]} />
          </div>
        </div>
      </div>
      <div className="cl-card" style={{ marginTop: 14 }}>
        <div className="cl-card-head"><div className="cl-card-title">Tareas próximas</div></div>
        <div className="cl-card-body" style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 14 }}>
          {[
            ['Enviar a revisión','CAP-0233 · Local Surco','Vence mañana','amber'],
            ['Subir documentos','SOL-0425 · cliente Trébol','3 documentos faltantes','red'],
            ['Registrar interacción','OP-1089 · cliente sin contacto','Hace 5 días','amber'],
          ].map((r,i)=>(
            <div key={i} style={{ border:'1px solid #DDE5E8', borderRadius: 8, padding: 12, background: '#FAFBFD' }}>
              <div style={{ fontSize: 12, fontWeight: 600 }}>{r[0]}</div>
              <div style={{ fontSize: 11.5, color: '#4A5A6E', marginTop: 4 }}>{r[1]}</div>
              <div style={{ marginTop: 8 }}><StatusBadge label={r[2]} tone={r[3]} plain /></div>
            </div>
          ))}
        </div>
      </div>
    </>
  );
};

const ScreenDashboard = ({ role: roleProp }) => {
  const { navigate, role: navRole } = useNav();
  // If running inside the navigable prototype, derive role from context.
  const effective = navRole
    ? (navRole === "Broker administrador" ? "BrokerAdmin" : navRole === "Broker" ? "Broker" : "Agente")
    : (roleProp || "Agente");
  const roleNames = {
    "Agente": "Agente inmobiliario",
    "Broker": "Broker",
    "BrokerAdmin": "Broker administrador"
  };
  const titles = {
    "Agente":      ["Buen día, Valentina",         "Tu actividad comercial de hoy"],
    "Broker":      ["Panel del broker",             "Supervisión de los agentes a tu cargo · revisión de captaciones y solicitudes"],
    "BrokerAdmin": ["Panel administrativo global",  "Gestión de brokers, auditoría e indicadores de toda la corredora"],
  };
  // Role-correct top-right actions. Only the agent registers captaciones;
  // brokers and admins never trigger "Nueva captación" from the dashboard.
  const actionsByRole = {
    "Agente": (
      <>
        <button className="cl-btn"><Icon name="download" size={13} /> Exportar</button>
        <button onClick={() => navigate('captacion-form')} className="cl-btn primary"><Icon name="plus" size={13} /> Nueva captación</button>
      </>
    ),
    "Broker": (
      <>
        <button className="cl-btn"><Icon name="download" size={13} /> Exportar</button>
        <button onClick={() => navigate('bandeja-captaciones')} className="cl-btn primary"><Icon name="pin" size={13} /> Bandeja de captaciones</button>
      </>
    ),
    "BrokerAdmin": (
      <>
        <button className="cl-btn"><Icon name="download" size={13} /> Exportar PDF</button>
        <button onClick={() => navigate('brokers')} className="cl-btn primary"><Icon name="briefcase" size={13} /> Gestión de brokers</button>
      </>
    ),
  };
  const sideActive = {
    "Agente":      "Dashboard",
    "Broker":      "Dashboard de equipo",
    "BrokerAdmin": "Dashboard global",
  };
  return (
    <AppShell active={sideActive[effective]} role={roleNames[effective]}
      crumbs={["ControlLocal", titles[effective][0]]}
      title={titles[effective][0]} subtitle={titles[effective][1]}
      actions={actionsByRole[effective]}>
      <DashboardBody role={effective === "BrokerAdmin" ? "Broker administrador" : effective === "Broker" ? "Broker" : "Agente"} />
    </AppShell>
  );
};

/* =================== MI PERFIL =================== */
const ScreenProfile = () => {
  const { role } = useNav();
  return (
  <AppShell role={role || "Agente inmobiliario"}
    crumbs={["ControlLocal", "Mi perfil"]}
    title="Mi perfil" subtitle="Datos de tu cuenta de usuario en ControlLocal">
    <div style={{ display: 'grid', gridTemplateColumns: '300px 1fr', gap: 14 }}>
      <div className="cl-card" style={{ textAlign: 'center', padding: 22 }}>
        <div className="cl-avatar" style={{ width: 88, height: 88, fontSize: 28, margin: '0 auto' }}>{(ROLE_USER[role]||ROLE_USER["Agente inmobiliario"]).ini}</div>
        <div style={{ fontSize: 16, fontWeight: 600, marginTop: 12 }}>{(ROLE_USER[role]||ROLE_USER["Agente inmobiliario"]).name}</div>
        <div style={{ fontSize: 12, color: '#8392A7' }}>{role || "Agente inmobiliario"}</div>
        <div style={{ marginTop: 10 }}><StatusBadge label="Activo" /></div>
        <button className="cl-btn" style={{ width: '100%', marginTop: 18, justifyContent: 'center' }}>
          <Icon name="upload" size={13} /> Cambiar foto
        </button>
        <div className="cl-divider"></div>
        <div style={{ textAlign: 'left' }}>
          <div style={{ fontSize: 11, color: '#8392A7', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 }}>Resumen</div>
          <dl className="cl-kv">
            {role === "Broker administrador" ? (
              <>
                <dt>Alcance</dt><dd>Global — todos los brokers</dd>
                <dt>Brokers a cargo</dt><dd>8</dd>
                <dt>Ingreso</dt><dd>01 Oct 2023</dd>
                <dt>Último acceso</dt><dd>Hoy 07:48</dd>
              </>
            ) : role === "Broker" ? (
              <>
                <dt>Agentes a cargo</dt><dd>7 activos</dd>
                <dt>Zona</dt><dd>Lima Centro / Sur</dd>
                <dt>Ingreso</dt><dd>11 Ene 2024</dd>
                <dt>Último acceso</dt><dd>Hoy 07:55</dd>
              </>
            ) : (
              <>
                <dt>Broker</dt><dd>Ricardo Salas</dd>
                <dt>Zona</dt><dd>Lima Centro / Sur</dd>
                <dt>Ingreso</dt><dd>14 Feb 2024</dd>
                <dt>Último acceso</dt><dd>Hoy 08:12</dd>
              </>
            )}
          </dl>
        </div>
      </div>
      <div className="cl-stack">
        <FormSection num="1" title="Datos personales" sub="Información visible para el broker y el equipo administrativo">
          <div className="cl-grid c2">
            <Field label="Nombres"><input defaultValue="Valentina" /></Field>
            <Field label="Apellidos"><input defaultValue="Mora Quispe" /></Field>
            <Field label="Documento (DNI)"><input defaultValue="45 893 211" /></Field>
            <Field label="Teléfono"><input defaultValue="+51 987 412 008" /></Field>
            <Field label="Correo corporativo"><input defaultValue="vmora@controllocal.pe" /></Field>
            <Field label="Zona asignada"><select><option>Lima Centro / Sur</option></select></Field>
          </div>
        </FormSection>
        <FormSection num="2" title="Cuenta de acceso" sub="Usuario, rol y autenticación">
          <div className="cl-grid c2">
            <Field label="Usuario"><input defaultValue="vmora" disabled /></Field>
            <Field label="Rol"><input defaultValue="Agente inmobiliario" disabled /></Field>
            <Field label="Contraseña actual"><input type="password" defaultValue="••••••••" /></Field>
            <Field label="Nueva contraseña" hint="Mínimo 8 caracteres, una mayúscula y un número"><input type="password" placeholder="Ingresa nueva contraseña" /></Field>
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            <button className="cl-btn primary">Guardar cambios</button>
            <button className="cl-btn ghost" style={{ color: '#0E5BFF' }}>Cambiar contraseña</button>
          </div>
        </FormSection>
      </div>
    </div>
  </AppShell>
  );
};

Object.assign(window, { ScreenLogin, ScreenRecover, ScreenDashboard, ScreenProfile });
