// Navigable prototype — wraps the existing screens in a NavProvider that
// swaps the visible screen and propagates the role chosen at login.
// Each "route" key maps to one screen; navigate(route, { role }) updates both.

const ROUTE_MAP = {
  // Auth
  'login':                () => <ScreenLogin />,
  'recover':              () => <ScreenRecover />,
  // Common
  'dashboard':            () => <ScreenDashboard />,
  'profile':              () => <ScreenProfile />,
  // Broker administrador
  'brokers':              () => <ScreenBrokers />,
  'reasignar':            () => <ScreenReasignarAgentes />,
  'catalogs':             () => <ScreenCatalogs />,
  'actividad':            () => <ScreenActividad />,
  // Broker normal
  'agents':               () => <ScreenAgents />,
  'bandeja-captaciones':  () => <ScreenBandejaCaptaciones />,
  'captacion-review':     () => <ScreenCaptacionReview />,
  // Agente inmobiliario
  'owners':               () => <ScreenOwners />,
  'locales':              () => <ScreenLocales />,
  'captaciones':          () => <ScreenCaptaciones />,
  'captacion-form':       () => <ScreenCaptacionForm />,
  'clientes':             () => <ScreenClientes />,
  'oportunidades':        () => <ScreenOportunidades />,
  'oportunidad-nueva':    () => <ScreenOportunidadNueva />,
  'detail-360':           () => <ScreenDetail360 />,
  'interacciones':        () => <ScreenInteracciones />,
  'visitas':              () => <ScreenVisitas />,
  'solicitudes':          () => <ScreenSolicitudes />,
  'documentos':           () => <ScreenDocumentos />,
  'evaluacion':           () => <ScreenEvaluacion />,
  'cierre':               () => <ScreenCierre />,
  // Reportes (dual: broker / admin)
  'reportes':             () => <ScreenReportes />,
};

// Permission matrix — single source of truth for which routes each role can
// reach. Used as a soft guard so a hash-typed URL respects the rules.
const ROLE_ROUTES = {
  "Broker administrador": new Set([
    'login','recover','dashboard','profile',
    'brokers','reasignar','catalogs','actividad','reportes',
    // read-only peek at oportunidades is allowed via auditoría
    'oportunidades','detail-360','interacciones',
  ]),
  "Broker": new Set([
    'login','recover','dashboard','profile',
    'agents','bandeja-captaciones','captacion-review',
    'solicitudes','evaluacion','documentos',
    'oportunidades','detail-360','interacciones',
    'reportes',
  ]),
  "Agente inmobiliario": new Set([
    'login','recover','dashboard','profile',
    'owners','locales','captaciones','captacion-form',
    'clientes','oportunidades','oportunidad-nueva','detail-360',
    'interacciones','visitas','solicitudes','documentos','cierre',
  ]),
};

// Display label for the role pill in the floating debug widget.
const ROLE_LABEL = {
  "Broker administrador": "Broker administrador",
  "Broker": "Broker",
  "Agente inmobiliario": "Agente inmobiliario",
};

const PrototypeApp = () => {
  const [route, setRoute] = React.useState(() => {
    const h = (location.hash || '').replace(/^#\/?/, '');
    return ROUTE_MAP[h] ? h : 'login';
  });
  const [role, setRole] = React.useState("Agente inmobiliario");

  const navigate = React.useCallback((nextRoute, opts) => {
    if (!ROUTE_MAP[nextRoute]) {
      console.warn('[prototype] unknown route:', nextRoute);
      return;
    }
    if (opts && opts.role) setRole(opts.role);
    setRoute(nextRoute);
    try {
      history.replaceState(null, '', '#/' + nextRoute);
      const scroller = document.querySelector('.cl-main') || window;
      scroller.scrollTo && scroller.scrollTo(0, 0);
      window.scrollTo(0, 0);
    } catch (_) {}
  }, []);

  React.useEffect(() => {
    const onHash = () => {
      const h = (location.hash || '').replace(/^#\/?/, '');
      if (ROUTE_MAP[h] && h !== route) setRoute(h);
    };
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, [route]);

  const Screen = ROUTE_MAP[route];
  const ctxValue = React.useMemo(() => ({ navigate, role, setRole, route }), [navigate, role, route]);

  return (
    <NavCtx.Provider value={ctxValue}>
      <Screen />
      {/* Small role/route hint, fixed bottom-right — non-intrusive. */}
      {route !== 'login' && route !== 'recover' && (
        <div style={{
          position: 'fixed', right: 14, bottom: 14, zIndex: 999,
          background: 'rgba(11,31,51,0.92)', color: '#fff',
          padding: '8px 12px', borderRadius: 8, fontSize: 11,
          fontFamily: "'Inter', system-ui, sans-serif",
          boxShadow: '0 6px 20px rgba(0,0,0,0.25)',
          display: 'flex', gap: 10, alignItems: 'center',
        }}>
          <span style={{ opacity: 0.6, textTransform: 'uppercase', letterSpacing: '0.06em' }}>Rol</span>
          <b>{ROLE_LABEL[role]}</b>
          <span style={{ width: 1, height: 14, background: 'rgba(255,255,255,0.18)' }} />
          <span style={{ opacity: 0.6 }}>{route}</span>
          <button
            onClick={() => navigate('login')}
            style={{
              marginLeft: 4, background: 'transparent', color: '#fff',
              border: '1px solid rgba(255,255,255,0.2)', padding: '3px 8px',
              borderRadius: 5, cursor: 'pointer', fontSize: 11, fontFamily: 'inherit',
            }}>Cambiar rol</button>
        </div>
      )}
    </NavCtx.Provider>
  );
};

window.PrototypeApp = PrototypeApp;
