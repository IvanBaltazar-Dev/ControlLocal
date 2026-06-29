namespace ControlLocal.Web.Services;

public static class RouteAccess
{
    private static readonly HashSet<string> PublicPages =
    [
        "Login", "Recover", "Error", "NotFound", "AccesoDenegado",
    ];

    private static readonly Dictionary<string, string[]> RolesByPage = new()
    {
        ["Dashboard"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["Profile"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["CambiarContrasena"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["Reportes"] = [Roles.Admin, Roles.Broker, Roles.Agente],

        ["Brokers"] = [Roles.Admin],
        ["BrokerProfile"] = [Roles.Admin],
        ["BrokerForm"] = [Roles.Admin],
        ["BrokerNuevo"] = [Roles.Admin],
        ["Reasignar"] = [Roles.Admin],
        ["Catalogs"] = [Roles.Admin],

        ["Agents"] = [Roles.Broker],
        ["AgenteDetail"] = [Roles.Broker],
        ["PropiedadesEquipo"] = [Roles.Broker],
        ["AgenteForm"] = [Roles.Broker],
        ["ReasignarCaptaciones"] = [Roles.Broker],
        ["HistorialReasignaciones"] = [Roles.Admin, Roles.Broker],
        ["BandejaCaptaciones"] = [Roles.Broker],
        ["CaptacionReview"] = [Roles.Broker],
        ["Evaluacion"] = [Roles.Broker],
        ["SeguimientoComercial"] = [Roles.Admin, Roles.Broker],
        ["Oportunidades"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["OportunidadForm"] = [Roles.Agente],
        ["Cierre"] = [Roles.Admin, Roles.Broker],

        ["Clientes"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["ClienteForm"] = [Roles.Agente],
        ["ClienteDetail"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["ClienteContactoDetail"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["Owners"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["OwnerForm"] = [Roles.Agente],
        ["OwnerDetail"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["Locales"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["LocalForm"] = [Roles.Agente],
        ["LocalDetail"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["Prospecciones"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["ProspeccionDetail"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["Captaciones"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["CaptacionForm"] = [Roles.Agente],
        ["Interacciones"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["InteraccionForm"] = [Roles.Agente],
        ["InteraccionDetail"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["Visitas"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["VisitaForm"] = [Roles.Agente],
        ["SolicitudForm"] = [Roles.Agente],
        ["SolicitudDetail"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["Documentos"] = [Roles.Agente],

        ["Solicitudes"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["PropiedadesAlquiladas"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["Comisiones"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["CaptacionDetail"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["OportunidadDetail"] = [Roles.Admin, Roles.Broker, Roles.Agente],
        ["FichaPropiedad"] = [Roles.Admin, Roles.Broker, Roles.Agente],
    };

    public static bool IsPublic(Type pageType) => PublicPages.Contains(pageType.Name);

    public static bool CanAccess(Type pageType, string role) =>
        RolesByPage.TryGetValue(pageType.Name, out var roles) && roles.Contains(role);
}
