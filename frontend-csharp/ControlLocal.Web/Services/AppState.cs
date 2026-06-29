using Microsoft.AspNetCore.Components;
using Microsoft.JSInterop;
using System.Text.Json;
using ControlLocal.Web.Services.Api;

namespace ControlLocal.Web.Services;

// Mantiene la identidad y el rol activos durante el circuito de Blazor.
public class AppState
{
    private readonly NavigationManager _nav;
    private readonly IJSRuntime _js;
    private readonly ApiSession _apiSession;
    private bool _initialized;

    public AppState(NavigationManager nav, IJSRuntime js, ApiSession apiSession)
    {
        _nav = nav;
        _js = js;
        _apiSession = apiSession;
    }

    // Clave interna estable; la interfaz muestra RoleLabel.
    public string Role { get; private set; } = Roles.Agente;

    public string RoleLabel => Roles.Label(Role);

    public void SetRole(string role) => Role = role;

    // Usuario autenticado en la sesión actual (null si nadie ha iniciado sesión).
    public AuthUser? CurrentUser { get; private set; }

    public bool IsAuthenticated => CurrentUser is not null;

    public bool SessionInitialized => _initialized;

    // Inicia sesión: fija el usuario y sincroniza el rol que usan Sidebar/Topbar.
    public void SignIn(AuthUser user)
    {
        CurrentUser = user;
        Role = user.Role;
    }

    public async Task SignInAsync(AuthUser user, string? token)
    {
        SignIn(user);
        _apiSession.Token = token;
        await GuardarSesionAsync(user, token);
    }

    public async Task EnsureInitializedAsync()
    {
        if (_initialized) return;

        await ReloadSessionAsync();
    }

    public async Task ReloadSessionAsync()
    {
        try
        {
            var json = await _js.InvokeAsync<string?>("controlLocal.session.get");
            _initialized = true;
            CurrentUser = null;
            Role = Roles.Agente;
            _apiSession.Token = null;

            if (string.IsNullOrWhiteSpace(json)) return;

            var session = JsonSerializer.Deserialize<BrowserSession>(json);
            if (session?.User is null || string.IsNullOrWhiteSpace(session.Token)) return;

            CurrentUser = session.User;
            Role = session.User.Role;
            _apiSession.Token = session.Token;
        }
        catch (JSException)
        {
            // No marcamos como inicializado: si el runtime JS aun no estaba listo,
            // el siguiente ciclo puede reintentar y recuperar la sesion persistida.
        }
        catch (JsonException)
        {
            _initialized = true;
            await LimpiarSesionAsync();
        }
    }

    // Cierra sesión y vuelve al rol por defecto.
    public void SignOut()
    {
        CurrentUser = null;
        Role = Roles.Agente;
        _apiSession.Token = null;
        _ = LimpiarSesionAsync();
    }

    public void Navigate(string route, string? role = null)
    {
        if (!string.IsNullOrEmpty(role)) Role = role;
        _nav.NavigateTo("/" + route.TrimStart('/'));
    }

    private async Task GuardarSesionAsync(AuthUser user, string? token)
    {
        if (string.IsNullOrWhiteSpace(token)) return;
        var json = JsonSerializer.Serialize(new BrowserSession(user, token));
        await _js.InvokeVoidAsync("controlLocal.session.set", json);
    }

    private async Task LimpiarSesionAsync()
    {
        try
        {
            await _js.InvokeVoidAsync("controlLocal.session.clear");
        }
        catch (JSException)
        {
        }
    }

    private sealed record BrowserSession(AuthUser User, string Token);
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
