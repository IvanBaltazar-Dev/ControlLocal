using Microsoft.AspNetCore.Components;

namespace ControlLocal.Web.Services;

// Mirrors the prototype's NavContext: holds the active role for the circuit and
// centralizes navigation so screens can switch routes/roles the same way the
// React prototype called navigate(route, { role }).
public class AppState
{
    private readonly NavigationManager _nav;

    public AppState(NavigationManager nav) => _nav = nav;

    // Internal role key (stable, backend-facing). UI shows RoleLabel instead.
    public string Role { get; private set; } = Roles.Agente;

    public string RoleLabel => Roles.Label(Role);

    public void SetRole(string role) => Role = role;

    // Usuario autenticado en la sesión actual (null si nadie ha iniciado sesión).
    public AuthUser? CurrentUser { get; private set; }

    public bool IsAuthenticated => CurrentUser is not null;

    // Inicia sesión: fija el usuario y sincroniza el rol que usan Sidebar/Topbar.
    public void SignIn(AuthUser user)
    {
        CurrentUser = user;
        Role = user.Role;
    }

    // Cierra sesión y vuelve al rol por defecto.
    public void SignOut()
    {
        CurrentUser = null;
        Role = Roles.Agente;
    }

    public void Navigate(string route, string? role = null)
    {
        if (!string.IsNullOrEmpty(role)) Role = role;
        _nav.NavigateTo("/" + route.TrimStart('/'));
    }
}

public static class Roles
{
    public const string Admin = "Broker administrador";
    public const string Broker = "Broker";
    public const string Agente = "Agente inmobiliario";

    private static readonly Dictionary<string, string> Labels = new()
    {
        [Admin] = "Admin general",
        [Broker] = "Broker supervisor",
        [Agente] = "Agente inmobiliario",
    };

    public static string Label(string role) => Labels.TryGetValue(role, out var l) ? l : role;
}
