namespace ControlLocal.Web.Services;

// Autenticación de la corredora. La UI depende solo de IAuthService; hoy hay una
// implementación en memoria (MockAuthService) con las credenciales de prueba.
// Cuando el backend Java exponga POST /auth/login, se reemplaza por un
// HttpAuthService (mismo contrato, devuelve el mismo AuthUser desde el JWT/usuario
// del API) sin tocar Login.razor ni AppState.

// Identidad autenticada en sesión. Iniciales/Nombre/Rol alimentan el Topbar y el
// Sidebar (vía AppState.Role), así que coincide con Navigation.RoleUsers.
public record AuthUser(
    string Usuario,
    string Email,
    string Nombre,
    string Iniciales,
    string Role);

// Resultado de un intento de login. Error trae el mensaje listo para mostrar.
public record AuthResult(bool Success, string? Error = null, AuthUser? User = null);

public interface IAuthService
{
    // Valida usuario (o correo) + contraseña. Async desde ya para que el cambio a
    // REST (HttpClient) no altere la firma ni el código de la pantalla.
    Task<AuthResult> LoginAsync(string usuarioOEmail, string password, CancellationToken ct = default);
}

// Perfil de prueba: credencial demo + metadatos para los accesos rápidos del login.
// Única fuente de las credenciales; MockAuthService valida contra esta misma lista.
public record DemoPerfil(
    string Role,
    string Usuario,
    string Email,
    string Password,
    string Nombre,
    string Iniciales,
    string Icon,
    string Desc);

public static class DemoCredenciales
{
    public static readonly IReadOnlyList<DemoPerfil> Todos = new[]
    {
        new DemoPerfil(Roles.Agente, "vmora", "vmora@controllocal.pe", "Agente2026",
            "Valentina Mora", "VM", "user",
            "Gestiona propietarios, locales, captaciones y oportunidades."),
        new DemoPerfil(Roles.Broker, "rsalas", "rsalas@controllocal.pe", "Broker2026",
            "Ricardo Salas", "RS", "users",
            "Supervisa agentes y acompaña la operación comercial."),
        new DemoPerfil(Roles.Admin, "atellez", "atellez@controllocal.pe", "Admin2026",
            "Alejandro Téllez", "AT", "briefcase",
            "Administra brokers, asignaciones y la configuración general."),
    };

    public static DemoPerfil? PorRole(string role) =>
        Todos.FirstOrDefault(p => p.Role == role);
}

public class MockAuthService : IAuthService
{
    // Latencia simulada para que el estado de "Verificando…" sea perceptible,
    // igual que una llamada de red real al backend.
    private const int LatenciaMs = 550;

    public async Task<AuthResult> LoginAsync(string usuarioOEmail, string password, CancellationToken ct = default)
    {
        var usuario = (usuarioOEmail ?? string.Empty).Trim();
        var clave = password ?? string.Empty;

        if (string.IsNullOrWhiteSpace(usuario) || string.IsNullOrWhiteSpace(clave))
            return new AuthResult(false, "Ingresa tu usuario y tu contraseña.");

        await Task.Delay(LatenciaMs, ct);

        var perfil = DemoCredenciales.Todos.FirstOrDefault(p =>
            string.Equals(p.Usuario, usuario, StringComparison.OrdinalIgnoreCase) ||
            string.Equals(p.Email, usuario, StringComparison.OrdinalIgnoreCase));

        // Mensaje genérico a propósito: no revela si falló el usuario o la clave.
        if (perfil is null || !string.Equals(perfil.Password, clave, StringComparison.Ordinal))
            return new AuthResult(false, "Usuario o contraseña incorrectos. Verifica tus datos.");

        var user = new AuthUser(perfil.Usuario, perfil.Email, perfil.Nombre, perfil.Iniciales, perfil.Role);
        return new AuthResult(true, null, user);
    }
}
