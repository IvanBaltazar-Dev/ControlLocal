using System.Text;
using Microsoft.JSInterop;

namespace ControlLocal.Web.Services;

public static class Csv
{
    // Genera CSV UTF-8 con BOM (compatible con Excel). Cada celda se escapa y se
    // neutraliza contra inyección de fórmulas (=, +, -, @) al abrirse en hojas de cálculo.
    public static byte[] Generar(IReadOnlyList<string> cabeceras, IEnumerable<IReadOnlyList<string?>> filas)
    {
        var sb = new StringBuilder();
        sb.AppendLine(string.Join(",", cabeceras.Select(Celda)));
        foreach (var fila in filas)
            sb.AppendLine(string.Join(",", fila.Select(Celda)));

        var utf8Bom = new UTF8Encoding(encoderShouldEmitUTF8Identifier: true);
        return utf8Bom.GetBytes(sb.ToString());
    }

    private static string Celda(string? valor)
    {
        var v = valor ?? "";
        if (v.Length > 0 && v[0] is '=' or '+' or '-' or '@')
            v = "'" + v;
        if (v.Contains('"') || v.Contains(',') || v.Contains('\n') || v.Contains('\r'))
            v = "\"" + v.Replace("\"", "\"\"") + "\"";
        return v;
    }
}

// Dispara la descarga de archivos generados en servidor hacia el navegador.
public class ExportacionService(IJSRuntime js)
{
    public Task DescargarCsvAsync(string nombreBase, IReadOnlyList<string> cabeceras,
        IEnumerable<IReadOnlyList<string?>> filas) =>
        DescargarAsync($"{NombreSeguro(nombreBase)}_{DateTime.Now:yyyyMMdd_HHmm}.csv",
            "text/csv;charset=utf-8", Csv.Generar(cabeceras, filas));

    public Task DescargarPdfAsync(string nombreBase, byte[] contenido) =>
        DescargarAsync($"{NombreSeguro(nombreBase)}_{DateTime.Now:yyyyMMdd_HHmm}.pdf",
            "application/pdf", contenido);

    private async Task DescargarAsync(string nombre, string tipoContenido, byte[] contenido) =>
        await js.InvokeVoidAsync("controlLocal.descargar", nombre, tipoContenido, Convert.ToBase64String(contenido));

    // Solo letras, números, guiones y guion bajo en el nombre del archivo.
    private static string NombreSeguro(string nombre)
    {
        var limpio = new string(nombre.Select(c => char.IsLetterOrDigit(c) || c is '-' or '_' ? c : '_').ToArray());
        return string.IsNullOrWhiteSpace(limpio) ? "export" : limpio;
    }
}
