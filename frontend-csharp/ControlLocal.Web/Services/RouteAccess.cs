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
        ["Reportes"] = [Roles.Admin, Roles.Broker],

        ["Brokers"] = [Roles.Admin],
        ["BrokerProfile"] = [Roles.Admin],
        ["BrokerForm"] = [Roles.Admin],
        ["BrokerNuevo"] = [Roles.Admin],
        ["Reasignar"] = [Roles.Admin],
        ["Catalogs"] = [Roles.Admin],

        ["Agents"] = [Roles.Broker],
        ["AgenteDetail"] = [Roles.Broker],
        ["AgenteForm"] = [Roles.Broker],
        ["ReasignarCaptaciones"] = [Roles.Broker],
        ["BandejaCaptaciones"] = [Roles.Broker],
        ["CaptacionReview"] = [Roles.Broker],
        ["Evaluacion"] = [Roles.Broker],
        ["Oportunidades"] = [Roles.Broker, Roles.Agente],
        ["OportunidadForm"] = [Roles.Agente],
        ["Cierre"] = [Roles.Admin, Roles.Broker],

        ["Clientes"] = [Roles.Agente],
        ["ClienteForm"] = [Roles.Agente],
        ["ClienteDetail"] = [Roles.Agente],
        ["Owners"] = [Roles.Agente],
        ["OwnerForm"] = [Roles.Agente],
        ["OwnerDetail"] = [Roles.Agente],
        ["Locales"] = [Roles.Agente],
        ["LocalForm"] = [Roles.Agente],
        ["LocalDetail"] = [Roles.Agente],
        ["Captaciones"] = [Roles.Agente],
        ["CaptacionForm"] = [Roles.Agente],
        ["Interacciones"] = [Roles.Agente],
        ["InteraccionForm"] = [Roles.Agente],
        ["InteraccionDetail"] = [Roles.Agente],
        ["Visitas"] = [Roles.Agente],
        ["VisitaForm"] = [Roles.Agente],
        ["SolicitudForm"] = [Roles.Agente],
        ["SolicitudDetail"] = [Roles.Agente],
        ["Documentos"] = [Roles.Agente],

        ["Solicitudes"] = [Roles.Broker, Roles.Agente],
        ["CaptacionDetail"] = [Roles.Broker, Roles.Agente],
        ["OportunidadDetail"] = [Roles.Broker, Roles.Agente],
        ["FichaPropiedad"] = [Roles.Broker, Roles.Agente],
    };

    public static bool IsPublic(Type pageType) => PublicPages.Contains(pageType.Name);

    public static bool CanAccess(Type pageType, string role) =>
        RolesByPage.TryGetValue(pageType.Name, out var roles) && roles.Contains(role);
}
