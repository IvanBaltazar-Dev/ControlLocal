namespace ControlLocal.Web.Models.Shared;

public static class PeruInputRules
{
    public const string TelefonoMensaje = "El teléfono debe tener 9 dígitos.";
    public const string DniMensaje = "El DNI debe tener 8 dígitos.";
    public const string RucMensaje = "El RUC debe tener 11 dígitos.";
    public const string TelefonoCelularMensaje = "El celular peruano debe tener 9 dígitos y empezar con 9.";
    public const string CorreoMensaje = "Ingresa un correo válido.";

    public static string Digitos(string? valor, int maxLength)
    {
        var digitos = new string((valor ?? "").Where(char.IsDigit).ToArray());
        return digitos.Length <= maxLength ? digitos : digitos[..maxLength];
    }

    public static string Telefono(string? valor)
    {
        var digitos = new string((valor ?? "").Where(char.IsDigit).ToArray());
        if (digitos.Length == 11 && digitos.StartsWith("51", StringComparison.Ordinal))
            digitos = digitos[2..];
        return digitos.Length <= 9 ? digitos : digitos[^9..];
    }

    public static int DocumentoMaxLength(string? tipoDocumento) =>
        NormalizarTipoDocumento(tipoDocumento) switch
        {
            "R" => 11,
            "D" => 8,
            _ => 20,
        };

    public static string Documento(string? valor, string? tipoDocumento) =>
        Digitos(valor, DocumentoMaxLength(tipoDocumento));

    public static bool ContieneCaracteresNoNumericos(string? valor) =>
        !string.IsNullOrWhiteSpace(valor)
        && valor.Any(c => !char.IsDigit(c) && !char.IsWhiteSpace(c));

    public static string DocumentoSoloNumerosMensaje(string? tipoDocumento) =>
        NormalizarTipoDocumento(tipoDocumento) switch
        {
            "R" => "El RUC solo debe contener numeros.",
            "D" => "El DNI solo debe contener numeros.",
            _ => "El numero de documento solo debe contener numeros.",
        };

    public static string? ValidarTelefono(string? valor)
    {
        var telefono = Telefono(valor);
    
        if (telefono.Length != 9)
            return TelefonoMensaje;
    
        if (!telefono.StartsWith("9"))
            return TelefonoCelularMensaje;
    
        return null;
    }

    public static string? ValidarDocumento(string? tipoDocumento, string? valor)
    {
        var tipo = NormalizarTipoDocumento(tipoDocumento);
        var digitos = new string((valor ?? "").Where(char.IsDigit).ToArray());
        return tipo switch
        {
            "D" when digitos.Length != 8 => DniMensaje,
            "R" when digitos.Length != 11 => RucMensaje,
            _ => null,
        };
    }

    private static string NormalizarTipoDocumento(string? tipoDocumento) =>
        (tipoDocumento ?? "").Trim().ToUpperInvariant() switch
        {
            "DNI" => "D",
            "RUC" => "R",
            _ => (tipoDocumento ?? "").Trim().ToUpperInvariant(),
        };
    public static bool EsCorreoValido(string? correo)
    {
        if (string.IsNullOrWhiteSpace(correo))
            return false;
    
        return System.Text.RegularExpressions.Regex.IsMatch(
            correo.Trim(),
            @"^[^\s@]+@[^\s@]+\.[^\s@]+$"
            );
    }
}
