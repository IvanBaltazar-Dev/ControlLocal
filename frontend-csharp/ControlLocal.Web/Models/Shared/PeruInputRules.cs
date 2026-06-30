namespace ControlLocal.Web.Models.Shared;

public static class PeruInputRules
{
    public const string TelefonoMensaje = "El teléfono debe tener 9 dígitos.";
    public const string DniMensaje = "El DNI debe tener 8 dígitos.";
    public const string DniInvalidoMensaje = "El DNI ingresado no es válido.";
    public const string RucMensaje = "El RUC debe tener 11 dígitos.";
    public const string TelefonoCelularMensaje = "El celular peruano debe tener 9 dígitos y empezar con 9.";
    public const string CorreoMensaje = "Ingresa un correo válido.";
    public const string RucPrefijoMensaje = "El RUC debe iniciar con 10, 15, 17 o 20.";
    public const string RucDigitoVerificadorMensaje = "El RUC ingresado no es válido (dígito verificador incorrecto).";

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
            "D" => ValidarDni(digitos),
            "R" => ValidarRuc(digitos),
            _ => null,
        };
    }

    // DNI peruano: exactamente 8 dígitos, no todos iguales (00000000, 11111111, ...).
    // SUNAT/RENIEC no expone un dígito verificador público, así que la validación
    // estructural se limita a la longitud y a descartar el patrón trivial.
    private static string? ValidarDni(string digitos)
    {
        if (digitos.Length != 8) return DniMensaje;
        if (digitos.Distinct().Count() == 1) return DniInvalidoMensaje;
        return null;
    }

    // RUC peruano (SUNAT): 11 dígitos, inicia con 10/15/17/20 y el último dígito es
    // el verificador calculado por módulo 11 sobre los 10 primeros con los pesos
    // 5,4,3,2,7,6,5,4,3,2.
    private static string? ValidarRuc(string digitos)
    {
        if (digitos.Length != 11) return RucMensaje;

        var prefijo = digitos[..2];
        if (prefijo is not ("10" or "15" or "17" or "20"))
            return RucPrefijoMensaje;

        int[] pesos = { 5, 4, 3, 2, 7, 6, 5, 4, 3, 2 };
        var suma = 0;
        for (var i = 0; i < 10; i++)
            suma += (digitos[i] - '0') * pesos[i];

        var resto = suma % 11;
        var esperado = 11 - resto;
        if (esperado == 10) esperado = 0;
        else if (esperado == 11) esperado = 1;

        return (digitos[10] - '0') == esperado ? null : RucDigitoVerificadorMensaje;
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
