using System.Globalization;

namespace ControlLocal.Web.Services;

// Comparaciones de texto para filtros y búsquedas. Ignoran mayúsculas/minúsculas Y
// acentos, de modo que un filtro como "Jurídica" case con el dato "Persona juridica"
// (la causa de que el filtro de persona jurídica no devolviera resultados).
public static class TextoFiltro
{
    private static readonly CompareInfo Comparador = CultureInfo.InvariantCulture.CompareInfo;
    private const CompareOptions Opciones = CompareOptions.IgnoreNonSpace | CompareOptions.IgnoreCase;

    // ¿"fuente" contiene "valor"? Un valor vacío no filtra (devuelve true).
    public static bool Contiene(string? fuente, string? valor) =>
        string.IsNullOrEmpty(valor)
        || (fuente is not null && Comparador.IndexOf(fuente, valor, Opciones) >= 0);

    // Igualdad insensible a mayúsculas y acentos.
    public static bool Igual(string? a, string? b) =>
        Comparador.Compare(a ?? string.Empty, b ?? string.Empty, Opciones) == 0;
}
