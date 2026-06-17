namespace ControlLocal.Web.Services;

public record NavItem(string Icon, string Label, string Route, string? Pill = null);
public record NavSection(string Section, NavItem[] Items);
public record RoleUser(string Initials, string Name, string Short);

// Menú lateral derivado de la matriz de permisos.
public static class Navigation
{
    public static readonly Dictionary<string, NavSection[]> SideNavByRole = new()
    {
        [Roles.Admin] = new[]
        {
            new NavSection("Principal", new[]
            {
                new NavItem("home", "Dashboard global", "dashboard"),
                new NavItem("chart", "Reportes globales", "reportes"),
            }),
            new NavSection("Administración", new[]
            {
                new NavItem("briefcase", "Brokers", "brokers"),
                new NavItem("users", "Reasignar agentes", "reasignar"),
                new NavItem("folder", "Catálogos del sistema", "catalogs"),
            }),
        },
        [Roles.Broker] = new[]
        {
            new NavSection("Principal", new[]
            {
                new NavItem("home", "Dashboard de equipo", "dashboard"),
                new NavItem("chart", "Reportes de equipo", "reportes"),
            }),
            new NavSection("Mi equipo", new[]
            {
                new NavItem("users", "Mis agentes", "agents"),
                new NavItem("swap", "Reasignar captaciones", "reasignar-captaciones"),
            }),
            new NavSection("Bandejas de revisión", new[]
            {
                new NavItem("pin", "Captaciones por revisar", "bandeja-captaciones", "9"),
                new NavItem("fileText", "Solicitudes por evaluar", "solicitudes", "6"),
            }),
            new NavSection("Seguimiento", new[]
            {
                new NavItem("target", "Operaciones del equipo", "oportunidades"),
            }),
        },
        [Roles.Agente] = new[]
        {
            new NavSection("Principal", new[]
            {
                new NavItem("home", "Dashboard", "dashboard"),
            }),
            new NavSection("Registro", new[]
            {
                new NavItem("users", "Clientes interesados", "clientes"),
                new NavItem("user", "Propietarios", "owners"),
                new NavItem("store", "Locales comerciales", "locales"),
            }),
            new NavSection("Operación", new[]
            {
                new NavItem("pin", "Captaciones", "captaciones", "14"),
                new NavItem("target", "Oportunidades comerciales", "oportunidades"),
                new NavItem("target", "Interacciones comerciales", "interacciones", "5"),
                new NavItem("calendar", "Visitas", "visitas"),
                new NavItem("fileText", "Solicitudes de alquiler", "solicitudes"),
            }),
        },
    };

    // Relaciona rutas de detalle y edición con su opción principal.
    public static readonly Dictionary<string, string> RouteToNav = new()
    {
        ["profile"] = "dashboard",
        ["cambiar-contrasena"] = "dashboard",
        ["cliente-form"] = "clientes",
        ["cliente-detail"] = "clientes",
        ["owner-form"] = "owners",
        ["owner-detail"] = "owners",
        ["local-form"] = "locales",
        ["local-detail"] = "locales",
        ["captacion-form"] = "captaciones",
        ["captacion-detail"] = "captaciones",
        ["oportunidad-detail"] = "oportunidades",
        ["oportunidad-form"] = "oportunidades",
        ["captacion-review"] = "bandeja-captaciones",
        ["interaccion-form"] = "interacciones",
        ["interaccion-detail"] = "interacciones",
        ["visita-form"] = "visitas",
        ["solicitud-form"] = "solicitudes",
        ["solicitud-detail"] = "solicitudes",
        ["documentos"] = "solicitudes",
        ["evaluacion"] = "solicitudes",
        ["cierre"] = "captaciones",
        ["agente-form"] = "agents",
        ["agente-detail"] = "agents",
        ["broker-profile"] = "brokers",
        ["broker-form"] = "brokers",
        ["broker-nuevo"] = "brokers",
        ["oportunidades"] = "oportunidades",
    };

    public static readonly Dictionary<string, RoleUser> RoleUsers = new()
    {
        [Roles.Admin] = new RoleUser("AT", "Alejandro Téllez", "Admin general"),
        [Roles.Broker] = new RoleUser("RS", "Ricardo Salas", "Broker supervisor — Lima Centro"),
        [Roles.Agente] = new RoleUser("VM", "Valentina Mora", "Agente — Lima Centro"),
    };

    public static readonly Dictionary<string, string> TopbarSearch = new()
    {
        [Roles.Admin] = "Buscar broker, agente, captación…",
        [Roles.Broker] = "Buscar agente, captación, solicitud…",
        [Roles.Agente] = "Buscar captación, local, cliente, solicitud…",
    };

    public static RoleUser UserFor(string role) =>
        RoleUsers.TryGetValue(role, out var u) ? u : RoleUsers[Roles.Agente];

    public static string SearchFor(string role) =>
        TopbarSearch.TryGetValue(role, out var s) ? s : TopbarSearch[Roles.Agente];
}
