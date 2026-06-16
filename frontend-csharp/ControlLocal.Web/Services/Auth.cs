namespace ControlLocal.Web.Services;

// Identidad devuelta por el endpoint REST de autenticacion.
public record AuthUser(
    string Usuario,
    string Email,
    string Nombre,
    string Iniciales,
    string Role);

public record AuthResult(bool Success, string? Error = null, AuthUser? User = null);

public interface IAuthService
{
    Task<AuthResult> LoginAsync(
        string usuarioOEmail,
        string password,
        CancellationToken ct = default);
}
