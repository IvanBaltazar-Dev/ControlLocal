namespace ControlLocal.Web.Services;

public sealed record ArchivoValidado(bool EsValido, string? Error = null)
{
    public static ArchivoValidado Ok() => new(true);
    public static ArchivoValidado Falla(string error) => new(false, error);
}

// Valida archivos recibidos por las bandejas de importación y carga de documentos:
// extensión y tipo de contenido en lista blanca, tamaño máximo y firma binaria
// (magic bytes) para impedir que un script renombrado pase como documento.
public static class ValidadorArchivos
{
    public const long TamanoMaximo = 5 * 1024 * 1024;

    private static readonly Dictionary<string, string[]> TiposPermitidos = new(StringComparer.OrdinalIgnoreCase)
    {
        [".csv"] = ["text/csv", "application/vnd.ms-excel", "text/plain"],
        [".pdf"] = ["application/pdf"],
        [".png"] = ["image/png"],
        [".jpg"] = ["image/jpeg"],
        [".jpeg"] = ["image/jpeg"],
    };

    private static readonly Dictionary<string, byte[][]> Firmas = new(StringComparer.OrdinalIgnoreCase)
    {
        [".pdf"] = [[0x25, 0x50, 0x44, 0x46]],
        [".png"] = [[0x89, 0x50, 0x4E, 0x47]],
        [".jpg"] = [[0xFF, 0xD8, 0xFF]],
        [".jpeg"] = [[0xFF, 0xD8, 0xFF]],
    };

    public static ArchivoValidado Validar(string nombre, string? tipoContenido, long tamano,
        ReadOnlySpan<byte> primerosBytes, params string[] extensionesPermitidas)
    {
        var extension = Path.GetExtension(nombre);
        if (string.IsNullOrEmpty(extension) || !TiposPermitidos.ContainsKey(extension))
            return ArchivoValidado.Falla($"Tipo de archivo no permitido ({extension}).");

        if (extensionesPermitidas.Length > 0 &&
            !extensionesPermitidas.Contains(extension, StringComparer.OrdinalIgnoreCase))
            return ArchivoValidado.Falla($"Aquí solo se aceptan archivos {string.Join(", ", extensionesPermitidas)}.");

        if (tamano <= 0)
            return ArchivoValidado.Falla("El archivo está vacío.");
        if (tamano > TamanoMaximo)
            return ArchivoValidado.Falla($"El archivo supera el máximo de {TamanoMaximo / 1024 / 1024} MB.");

        if (!string.IsNullOrEmpty(tipoContenido) &&
            !TiposPermitidos[extension].Contains(tipoContenido, StringComparer.OrdinalIgnoreCase))
            return ArchivoValidado.Falla("El tipo de contenido no coincide con la extensión del archivo.");

        if (Firmas.TryGetValue(extension, out var firmas))
        {
            var coincide = false;
            foreach (var firma in firmas)
                if (primerosBytes.Length >= firma.Length && primerosBytes[..firma.Length].SequenceEqual(firma))
                    coincide = true;
            if (!coincide)
                return ArchivoValidado.Falla("El contenido del archivo no corresponde a su extensión.");
        }

        return ArchivoValidado.Ok();
    }

    // Nombre de archivo seguro: sin rutas, sin caracteres de control ni especiales.
    public static string NombreSeguro(string nombre)
    {
        var soloNombre = Path.GetFileName(nombre);
        var extension = Path.GetExtension(soloNombre);
        var basePart = Path.GetFileNameWithoutExtension(soloNombre);
        var limpio = new string(basePart.Select(c => char.IsLetterOrDigit(c) || c is '-' or '_' ? c : '_').ToArray());
        if (string.IsNullOrWhiteSpace(limpio)) limpio = "documento";
        return limpio[..Math.Min(limpio.Length, 80)] + extension.ToLowerInvariant();
    }

    // Limpia texto importado: recorta, elimina caracteres de control y etiquetas HTML.
    public static string TextoSeguro(string? valor, int maxLargo = 200)
    {
        if (string.IsNullOrWhiteSpace(valor)) return "";
        var limpio = new string(valor.Trim().Where(c => !char.IsControl(c)).ToArray());
        limpio = limpio.Replace("<", "").Replace(">", "");
        return limpio[..Math.Min(limpio.Length, maxLargo)];
    }
}

// Estado de conectividad del almacén de documentos del backend (S3 o disco). La pantalla
// de documentos lo consulta (GET documentos/salud) para avisar de inmediato si el almacén
// no responde, en vez de fallar al subir o al abrir un documento sin explicar por qué.
// Espeja el record EstadoAlmacen del backend Java (proveedor, conectado, detalle).
public sealed record EstadoAlmacen(string Proveedor, bool Conectado, string Detalle)
{
    public static EstadoAlmacen Ok(string proveedor, string detalle) => new(proveedor, true, detalle);
    public static EstadoAlmacen Falla(string proveedor, string detalle) => new(proveedor, false, detalle);
}

// Resuelve el content-type a partir de la extensión, para servir el documento con la
// cabecera correcta y que el navegador lo muestre en línea (PDF/imagen) en vez de bajarlo.
public static class TiposContenido
{
    public static string Para(string nombreOClave)
    {
        var ext = Path.GetExtension(nombreOClave).ToLowerInvariant();
        return ext switch
        {
            ".pdf" => "application/pdf",
            ".png" => "image/png",
            ".jpg" or ".jpeg" => "image/jpeg",
            ".csv" => "text/csv",
            _ => "application/octet-stream",
        };
    }

    // Solo PDF e imágenes se pueden incrustar de forma segura en un visor en línea.
    public static bool SePuedeIncrustar(string nombreOClave) =>
        Para(nombreOClave) is "application/pdf" or "image/png" or "image/jpeg";

    public static bool EsImagen(string nombreOClave) =>
        Para(nombreOClave) is "image/png" or "image/jpeg";

    // URL del contenido real (foto/documento) servido por el proxy del frontend a partir de
    // su clave opaca. Único punto para que miniaturas y visores resuelvan igual la URL.
    public static string UrlContenido(string clave) =>
        $"/documento?clave={Uri.EscapeDataString(clave)}";

    // Icono coherente con el tipo real del archivo (imagen vs PDF vs otro).
    public static string IconoPara(string nombreOClave) => Para(nombreOClave) switch
    {
        "image/png" or "image/jpeg" => "image",
        "application/pdf" => "filePdf",
        _ => "fileText",
    };
}
