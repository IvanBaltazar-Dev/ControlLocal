// Component architecture overview — visual map of reusable components for Blazor handoff

const CompBox = ({ name, type, sub, color = "#0E5BFF", style }) => (
  <div style={{
    background: '#fff', border: `1px solid ${color}33`, borderLeft: `3px solid ${color}`,
    borderRadius: 8, padding: '10px 12px', fontSize: 12, ...style
  }}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
      <div style={{ fontWeight: 600, fontSize: 12.5, color: '#0B1F33' }}>{name}</div>
      {type && <span style={{ fontSize: 9, fontFamily: 'monospace', color: color, background: color + '14', padding: '1px 6px', borderRadius: 3, letterSpacing: '0.04em', textTransform: 'uppercase' }}>{type}</span>}
    </div>
    {sub && <div style={{ fontSize: 11, color: '#8392A7', marginTop: 3, lineHeight: 1.45 }}>{sub}</div>}
  </div>
);

const ScreenArchitecture = () => (
  <div style={{
    width: '100%', height: '100%', padding: 36,
    fontFamily: "'Inter', sans-serif", color: '#0B1F33',
    background: 'linear-gradient(180deg, #fff 0%, #F6F8FC 100%)',
    overflow: 'auto'
  }}>
    <div style={{ maxWidth: 1280, margin: '0 auto' }}>
      <div style={{ fontSize: 11, letterSpacing: '0.08em', textTransform: 'uppercase', color: '#8392A7' }}>ControlLocal · Documento técnico</div>
      <div style={{ fontSize: 26, fontWeight: 600, letterSpacing: '-0.015em', marginTop: 4 }}>Arquitectura visual de componentes</div>
      <div style={{ fontSize: 13, color: '#4A5A6E', marginTop: 8, maxWidth: 720, lineHeight: 1.55 }}>
        Mapa de componentes reutilizables del prototipo, agrupados por capa, pensados para una implementación
        futura en <b>Blazor (Razor Components)</b> consumiendo servicios <b>REST</b> de un backend en <b>Java</b>.
        Cada bloque visual corresponde a un componente Razor potencial.
      </div>

      {/* CAPA 1: Tokens */}
      <div style={{ marginTop: 32 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <div style={{ width: 26, height: 26, borderRadius: 6, background: '#0B1F33', color: '#fff', display: 'grid', placeItems: 'center', fontSize: 12, fontWeight: 700 }}>1</div>
          <div style={{ fontSize: 16, fontWeight: 600 }}>Tokens y estilos base</div>
          <span style={{ fontSize: 11, color: '#8392A7' }}>· <code style={{ background: '#F1F4F8', padding: '1px 6px', borderRadius: 3 }}>wwwroot/css/tokens.css</code></span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10 }}>
          <CompBox name="Paleta de color" type="tokens" sub="navy, primary, fondo, gris borde, verde, ámbar, rojo, info" color="#0B1F33" />
          <CompBox name="Tipografía" type="tokens" sub="Inter · escala 11–24px · pesos 400/500/600" color="#0B1F33" />
          <CompBox name="Espaciado y radios" type="tokens" sub="4px base · radios 6/8/10/12 · sombras suaves" color="#0B1F33" />
          <CompBox name="Iconografía" type="library" sub="Set de íconos line · 1.6px stroke · 16/20px" color="#0B1F33" />
        </div>
      </div>

      {/* CAPA 2: Layout */}
      <div style={{ marginTop: 28 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <div style={{ width: 26, height: 26, borderRadius: 6, background: '#0E5BFF', color: '#fff', display: 'grid', placeItems: 'center', fontSize: 12, fontWeight: 700 }}>2</div>
          <div style={{ fontSize: 16, fontWeight: 600 }}>Layout / Shell</div>
          <span style={{ fontSize: 11, color: '#8392A7' }}>· <code style={{ background: '#F1F4F8', padding: '1px 6px', borderRadius: 3 }}>Shared/MainLayout.razor</code></span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10 }}>
          <CompBox name="<Sidebar>" type="layout" sub="Menú lateral fijo · agrupado por módulo · estado activo · contadores por sección" color="#0E5BFF" />
          <CompBox name="<Topbar>" type="layout" sub="Buscador global · notificaciones · fecha · menú de perfil" color="#0E5BFF" />
          <CompBox name="<Breadcrumbs>" type="layout" sub="Migas de pan dinámicas según ruta" color="#0E5BFF" />
          <CompBox name="<PageHeader>" type="layout" sub="Título, subtítulo y zona de acciones primarias" color="#0E5BFF" />
        </div>
      </div>

      {/* CAPA 3: UI Kit */}
      <div style={{ marginTop: 28 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <div style={{ width: 26, height: 26, borderRadius: 6, background: '#0284C7', color: '#fff', display: 'grid', placeItems: 'center', fontSize: 12, fontWeight: 700 }}>3</div>
          <div style={{ fontSize: 16, fontWeight: 600 }}>UI Kit · Componentes transversales</div>
          <span style={{ fontSize: 11, color: '#8392A7' }}>· <code style={{ background: '#F1F4F8', padding: '1px 6px', borderRadius: 3 }}>Components/UI/</code></span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10 }}>
          <CompBox name="<Button>" type="ui" sub="Variantes: primary, default, ghost, success, warn, danger · tamaños sm/md" color="#0284C7" />
          <CompBox name="<StatusBadge>" type="ui" sub="Estados de captación, oportunidad, solicitud, visita, documento · tonos predefinidos" color="#0284C7" />
          <CompBox name="<MetricCard>" type="ui" sub="Tarjeta de KPI con ícono, valor, variación y nota auxiliar" color="#0284C7" />
          <CompBox name="<DataTable>" type="ui" sub="Tabla administrativa · columnas configurables · renderers · paginación" color="#0284C7" />
          <CompBox name="<FilterBar>" type="ui" sub="Búsqueda + selects + acciones de exportar/filtros avanzados" color="#0284C7" />
          <CompBox name="<FormSection>" type="ui" sub="Sección numerada con título, subtítulo y grid de Fields" color="#0284C7" />
          <CompBox name="<Field>" type="ui" sub="Label + control + hint + estado required/disabled" color="#0284C7" />
          <CompBox name="<Tabs>" type="ui" sub="Navegación de pestañas con contador opcional" color="#0284C7" />
          <CompBox name="<Timeline>" type="ui" sub="Hilo cronológico con estados done/active/warn/red" color="#0284C7" />
          <CompBox name="<Modal>" type="ui" sub="Capa modal con head/body/foot · tamaño sm/lg" color="#0284C7" />
          <CompBox name="<Alert>" type="ui" sub="Mensajes blue/amber/red/green con ícono y descripción" color="#0284C7" />
          <CompBox name="<Pagination>" type="ui" sub="Navegación de páginas + contador de resultados" color="#0284C7" />
          <CompBox name="<RowActions>" type="ui" sub="Acciones por fila: ver, editar, más, eliminar" color="#0284C7" />
          <CompBox name="<Avatar>" type="ui" sub="Iniciales o foto · tamaños 24/28/30/88" color="#0284C7" />
          <CompBox name="<DocumentRow>" type="ui" sub="Línea de documento con tipo, estado, acciones y observaciones" color="#0284C7" />
          <CompBox name="<EmptyState>" type="ui" sub="Estado vacío con ícono, mensaje y CTA" color="#0284C7" />
        </div>
      </div>

      {/* CAPA 4: Charts */}
      <div style={{ marginTop: 28 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <div style={{ width: 26, height: 26, borderRadius: 6, background: '#16A34A', color: '#fff', display: 'grid', placeItems: 'center', fontSize: 12, fontWeight: 700 }}>4</div>
          <div style={{ fontSize: 16, fontWeight: 600 }}>Visualización de datos</div>
          <span style={{ fontSize: 11, color: '#8392A7' }}>· <code style={{ background: '#F1F4F8', padding: '1px 6px', borderRadius: 3 }}>Components/Charts/</code></span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10 }}>
          <CompBox name="<Sparkline>" type="chart" sub="Mini gráfico de líneas para tendencias" color="#16A34A" />
          <CompBox name="<BarChart>" type="chart" sub="Volumen por período · etiquetas semanales" color="#16A34A" />
          <CompBox name="<DonutChart>" type="chart" sub="Distribución por estado de oportunidad o documento" color="#16A34A" />
          <CompBox name="<FunnelBar>" type="chart" sub="Embudo de conversión con porcentajes por etapa" color="#16A34A" />
        </div>
      </div>

      {/* CAPA 5: Módulos / páginas */}
      <div style={{ marginTop: 28 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <div style={{ width: 26, height: 26, borderRadius: 6, background: '#F59E0B', color: '#fff', display: 'grid', placeItems: 'center', fontSize: 12, fontWeight: 700 }}>5</div>
          <div style={{ fontSize: 16, fontWeight: 600 }}>Módulos del sistema · 24 pantallas principales</div>
          <span style={{ fontSize: 11, color: '#8392A7' }}>· <code style={{ background: '#F1F4F8', padding: '1px 6px', borderRadius: 3 }}>Pages/</code></span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 12 }}>
          <div style={{ background: '#fff', border: '1px solid #DDE5E8', borderRadius: 10, padding: 14 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#0E5BFF', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Icon name="lock" size={13} /> Acceso (2)
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
              <div>01 · Login.razor</div>
              <div>02 · RecoverPassword.razor</div>
            </div>
          </div>
          <div style={{ background: '#fff', border: '1px solid #DDE5E8', borderRadius: 10, padding: 14 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#0E5BFF', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Icon name="home" size={13} /> Inicio (2)
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
              <div>03 · Dashboard.razor <span className="cl-muted">(3 vistas por rol)</span></div>
              <div>04 · MyProfile.razor</div>
            </div>
          </div>
          <div style={{ background: '#fff', border: '1px solid #DDE5E8', borderRadius: 10, padding: 14 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#0E5BFF', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Icon name="briefcase" size={13} /> Administración (3)
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
              <div>05 · Brokers.razor</div>
              <div>06 · Agents.razor</div>
              <div>07 · Catalogs.razor</div>
            </div>
          </div>
          <div style={{ background: '#fff', border: '1px solid #DDE5E8', borderRadius: 10, padding: 14 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#0E5BFF', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Icon name="pin" size={13} /> Captación (5)
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
              <div>08 · Owners.razor</div>
              <div>09 · Locales.razor</div>
              <div>10 · Captaciones.razor</div>
              <div>11 · CaptacionForm.razor</div>
              <div>12 · CaptacionReview.razor</div>
            </div>
          </div>
          <div style={{ background: '#fff', border: '1px solid #DDE5E8', borderRadius: 10, padding: 14 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#0E5BFF', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Icon name="target" size={13} /> Comercial · Oportunidad (4)
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
              <div>13 · Clients.razor</div>
              <div>14 · Opportunities.razor</div>
              <div>15 · OpportunityNew.razor</div>
              <div style={{ fontWeight: 600, color: '#0E5BFF' }}>16 · OpportunityDetail360.razor ★</div>
            </div>
          </div>
          <div style={{ background: '#fff', border: '1px solid #DDE5E8', borderRadius: 10, padding: 14 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#0E5BFF', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Icon name="activity" size={13} /> Comercial · Operación (6)
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
              <div>17 · Interactions.razor</div>
              <div>18 · Visits.razor</div>
              <div>19 · Requests.razor</div>
              <div>20 · RequestDocuments.razor</div>
              <div>21 · RequestEvaluation.razor</div>
              <div>22 · OpportunityClose.razor</div>
            </div>
          </div>
          <div style={{ background: '#fff', border: '1px solid #DDE5E8', borderRadius: 10, padding: 14 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#0E5BFF', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Icon name="chart" size={13} /> Reportes (2)
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
              <div>23 · Reports.razor</div>
              <div>24 · Activity.razor</div>
            </div>
          </div>
          <div style={{ background: '#0B1F33', color: '#fff', borderRadius: 10, padding: 14 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#fff', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Icon name="component" size={13} color="#fff" /> Total
            </div>
            <div style={{ fontSize: 26, fontWeight: 600, letterSpacing: '-0.02em' }}>24 pantallas</div>
            <div style={{ fontSize: 11, color: '#8FA0B8', marginTop: 4, lineHeight: 1.5 }}>
              7 módulos · 12 componentes UI · 4 charts · 1 layout
            </div>
          </div>
        </div>
      </div>

      {/* Capa 6: Servicios */}
      <div style={{ marginTop: 28 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <div style={{ width: 26, height: 26, borderRadius: 6, background: '#DC2626', color: '#fff', display: 'grid', placeItems: 'center', fontSize: 12, fontWeight: 700 }}>6</div>
          <div style={{ fontSize: 16, fontWeight: 600 }}>Capa de servicios · cliente REST</div>
          <span style={{ fontSize: 11, color: '#8392A7' }}>· <code style={{ background: '#F1F4F8', padding: '1px 6px', borderRadius: 3 }}>Services/Api/</code> · backend Java</span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10 }}>
          {[
            ['IAuthService','POST /auth/login · /auth/recover'],
            ['IBrokerService','GET/POST/PUT /brokers'],
            ['IAgentService','GET/POST/PUT /agents · /agents/{id}/reassign'],
            ['IOwnerService','GET/POST/PUT /owners'],
            ['ILocalService','GET/POST/PUT /locales'],
            ['ICaptacionService','POST /captaciones · approve · reject · adjust'],
            ['IClientService','GET/POST /clients'],
            ['IOpportunityService','POST /opportunities · close · timeline'],
            ['IInteractionService','POST /opportunities/{id}/interactions'],
            ['IVisitService','POST /opportunities/{id}/visits'],
            ['IRequestService','POST /requests · evaluate · approve · reject'],
            ['IDocumentService','POST /requests/{id}/documents · review'],
            ['IReportService','GET /reports/kpi · funnel · performance'],
            ['IActivityService','GET /activity · /audit'],
            ['ICatalogService','GET /catalogs/{type}'],
            ['INotificationService','GET /notifications · WebSocket'],
          ].map(([n, s], i) => <CompBox key={i} name={n} type="service" sub={s} color="#DC2626" />)}
        </div>
      </div>

      <div style={{ marginTop: 36, padding: 18, background: '#0B1F33', borderRadius: 10, color: '#C9D4E2', fontSize: 12.5, lineHeight: 1.6 }}>
        <div style={{ color: '#fff', fontSize: 13, fontWeight: 600, marginBottom: 6 }}>Notas para implementación Blazor</div>
        Mapear cada pantalla a una página Razor bajo <code style={{ color: '#82B1FF' }}>/Pages/{'{Modulo}'}/{'{Pantalla}'}.razor</code>.
        Los componentes UI deben ser <b>parametrizados por &lt;Parameter&gt;</b> (no acoplar a modelos), y consumir
        DTOs desde la capa <code style={{ color: '#82B1FF' }}>Services/Api</code>. Los estados visuales se mantienen
        como enums + diccionario de tono — replicar el mapeo definido en <code style={{ color: '#82B1FF' }}>StatusBadge</code>.
        Para Detail360 usar <code style={{ color: '#82B1FF' }}>CascadingValue&lt;OpportunityContext&gt;</code> para
        compartir el modelo entre tabs.
      </div>
    </div>
  </div>
);

window.ScreenArchitecture = ScreenArchitecture;
