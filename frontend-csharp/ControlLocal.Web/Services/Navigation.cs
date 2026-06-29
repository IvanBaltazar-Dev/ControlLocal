namespace ControlLocal.Web.Services;

public record NavItem(string Icon, string Label, string Route, string? Pill = null);

// Sub-grupo dentro de una seccion colapsable.
// Ahora tambien puede plegarse/desplegarse dentro de "Consulta comercial".
public record NavGroup(
    string Label,
    NavItem[] Items,
    bool Collapsible = true,
    bool StartCollapsed = true
);

// Una seccion del menu. Si Collapsible es true, sus opciones viven en Groups (no en Items)
// y la seccion se pliega/despliega; nace plegada para aligerar el render inicial.
public record NavSection(string Section, NavItem[] Items, bool Collapsible = false, NavGroup[]? Groups = null);

public record RoleUser(string Initials, string Name, string Short);

// Menu lateral derivado de la matriz de permisos.
public static class Navigation
{
    // Contenido comercial de consulta, compartido por broker y admin. Va dentro de una seccion
    // colapsable para que el trabajo principal del rol (supervision/administracion + cierre) quede arriba.
    private static readonly NavItem[] OperacionItems =
    {
        new("compass", "Prospecciones", "prospecciones"),
        new("phone", "Interacciones", "interacciones"),
        new("pin", "Captaciones", "captaciones"),
        new("spark", "Oportunidades", "oportunidades"),
        new("calendar", "Visitas", "visitas"),
        new("clipboardList", "Solicitudes de alquiler", "solicitudes"),
    };

    private static readonly NavItem[] RegistroItems =
    {
        new("idCard", "Propietarios", "owners"),
        new("warehouse", "Locales comerciales", "locales"),
        new("users", "Clientes interesados", "clientes"),
    };

    // Un colapsable compartido por broker y admin.
    // Al abrir "Consulta comercial", los subgrupos nacen compactos:
    // - Consulta de Operaciones
    // - Consulta de Registros
    private static readonly NavSection ConsultaComercial = new(
        "Consulta comercial",
        Array.Empty<NavItem>(),
        Collapsible: true,
        Groups: new[]
        {
            new NavGroup(
                "Consulta de Operaciones",
                OperacionItems,
                Collapsible: true,
                StartCollapsed: true
            ),

            new NavGroup(
                "Consulta de Registros",
                RegistroItems,
                Collapsible: true,
                StartCollapsed: true
            ),
        });

    public static readonly Dictionary<string, NavSection[]> SideNavByRole = new()
    {
        [Roles.Admin] = new[]
        {
            new NavSection("INICIO", new[]
            {
                new NavItem("home", "Dashboard", "dashboard"),
            }),

            new NavSection("DIRECCION", new[]
            {
                new NavItem("chart", "Reportes globales", "reportes"),
                new NavItem("route", "Operaciones globales", "seguimiento-comercial"),
            }),

            new NavSection("EQUIPO", new[]
            {
                new NavItem("briefcase", "Brokers", "brokers"),
                new NavItem("users", "Reasignar agentes", "reasignar"),
                new NavItem("history", "Historial de reasignaciones", "historial-reasignaciones"),
            }),

            new NavSection("CIERRE", new[]
            {
                new NavItem("checkCircle", "Cierres exitosos", "propiedades-alquiladas"),
                new NavItem("coins", "Comisiones", "comisiones"),
            }),

            ConsultaComercial,

            new NavSection("SISTEMA", new[]
            {
                new NavItem("archive", "Catálogos del sistema", "catalogs"),
            }),
        },

        [Roles.Broker] = new[]
        {
            new NavSection("INICIO", new[]
            {
                new NavItem("home", "Dashboard", "dashboard"),
            }),

            new NavSection("SUPERVISION", new[]
            {
                new NavItem("shieldCheck", "Captaciones por revisar", "bandeja-captaciones"),
                new NavItem("clipboardCheck", "Solicitudes por revisar", "solicitudes-revisar"),
            }),

            new NavSection("EQUIPO", new[]
            {
                new NavItem("users", "Mis agentes", "agents"),
                new NavItem("chart", "Reportes de equipo", "reportes"),
                new NavItem("route", "Operaciones del equipo", "seguimiento-comercial"),
                new NavItem("building", "Propiedades del equipo", "propiedades-equipo"),
                new NavItem("swap", "Reasignar captaciones", "reasignar-captaciones"),
                new NavItem("history", "Historial de reasignaciones", "historial-reasignaciones"),
            }),

            new NavSection("CIERRE", new[]
            {
                new NavItem("checkCircle", "Cierres exitosos", "propiedades-alquiladas"),
                new NavItem("coins", "Comisiones", "comisiones"),
            }),

            ConsultaComercial,
        },

        [Roles.Agente] = new[]
        {
            new NavSection("INICIO", new[]
            {
                new NavItem("home", "Dashboard", "dashboard"),
                new NavItem("chart", "Mi reporte", "reportes"),
            }),

            new NavSection("COMERCIAL", new[]
            {
                new NavItem("compass", "Prospecciones", "prospecciones"),
                new NavItem("phone", "Interacciones", "interacciones"),
                new NavItem("pin", "Captaciones", "captaciones"),
                new NavItem("spark", "Oportunidades", "oportunidades"),
                new NavItem("calendar", "Visitas", "visitas"),
                new NavItem("clipboardList", "Solicitudes de alquiler", "solicitudes"),
            }),

            new NavSection("CIERRES", new[]
            {
                new NavItem("checkCircle", "Cierres exitosos", "propiedades-alquiladas"),
                new NavItem("coins", "Comisiones", "comisiones"),
            }),

            new NavSection("REGISTRO", new[]
            {
                new NavItem("idCard", "Propietarios", "owners"),
                new NavItem("warehouse", "Locales comerciales", "locales"),
                new NavItem("users", "Clientes interesados", "clientes"),
            }),
        },
    };

    // Relaciona rutas de detalle y edicion con su opcion principal.
    public static readonly Dictionary<string, string> RouteToNav = new()
    {
        ["profile"] = "dashboard",
        ["cambiar-contrasena"] = "dashboard",
        ["reportes"] = "reportes",

        ["cliente-form"] = "clientes",
        ["cliente-detail"] = "clientes",
        ["cliente-contacto-detail"] = "clientes",

        ["owner-form"] = "owners",
        ["owner-detail"] = "owners",

        ["local-form"] = "locales",
        ["local-detail"] = "locales",

        ["prospecciones"] = "prospecciones",
        ["prospeccion-detail"] = "prospecciones",

        ["captacion-form"] = "captaciones",
        ["captacion-detail"] = "captaciones",
        ["captacion-review"] = "bandeja-captaciones",

        ["oportunidades"] = "oportunidades",
        ["oportunidad-detail"] = "oportunidades",
        ["oportunidad-form"] = "oportunidades",

        ["interaccion-form"] = "interacciones",
        ["interaccion-detail"] = "interacciones",

        ["visita-form"] = "visitas",

        ["solicitud-form"] = "solicitudes",
        ["solicitud-detail"] = "solicitudes",
        ["solicitudes-revisar"] = "solicitudes-revisar",
        ["documentos"] = "solicitudes",
        ["evaluacion"] = "solicitudes",

        ["cierre"] = "captaciones",

        ["agente-form"] = "agents",
        ["agente-detail"] = "agents",

        ["broker-profile"] = "brokers",
        ["broker-form"] = "brokers",
        ["broker-nuevo"] = "brokers",

        ["seguimiento-comercial"] = "seguimiento-comercial",
        ["propiedades-equipo"] = "propiedades-equipo",
        ["propiedades-alquiladas"] = "propiedades-alquiladas",
        ["comisiones"] = "comisiones",
    };

    public static readonly Dictionary<string, RoleUser> RoleUsers = new()
    {
        [Roles.Admin] = new RoleUser("AT", "Alejandro Tellez", "Admin general"),
        [Roles.Broker] = new RoleUser("RS", "Ricardo Salas", "Broker supervisor - Lima Centro"),
        [Roles.Agente] = new RoleUser("VM", "Valentina Mora", "Agente - Lima Centro"),
    };

    public static readonly Dictionary<string, string> TopbarSearch = new()
    {
        [Roles.Admin] = "Buscar broker, agente, captacion...",
        [Roles.Broker] = "Buscar agente, captacion, solicitud...",
        [Roles.Agente] = "Buscar captacion, local, cliente, solicitud...",
    };

    public static RoleUser UserFor(string role) =>
        RoleUsers.TryGetValue(role, out var u) ? u : RoleUsers[Roles.Agente];

    public static string SearchFor(string role) =>
        TopbarSearch.TryGetValue(role, out var s) ? s : TopbarSearch[Roles.Agente];
}

