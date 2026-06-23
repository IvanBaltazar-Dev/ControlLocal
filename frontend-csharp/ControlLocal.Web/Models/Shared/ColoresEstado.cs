namespace ControlLocal.Web.Models.Shared;

// Color por etapa/estado para gráficos (donut, leyendas, PDF). Conserva la "temperatura"
// semántica — verde = positivo, amarillo/naranja = precaución, rojo = negativo, azul = en
// proceso — pero asigna un TONO DISTINTO a cada etapa para que se distingan en un mismo
// gráfico (ej. Activa vs Cerrada, Pendiente vs Observada).
public static class ColoresEstado
{
    // Tono exacto por etapa conocida (distinto dentro de su familia de color).
    private static readonly Dictionary<string, string> PorNombre = new(StringComparer.OrdinalIgnoreCase)
    {
        // Positivos — verdes en distintos tonos
        ["Activa"] = "#22C55E",
        ["Activo"] = "#22C55E",
        ["Aprobada"] = "#16A34A",
        ["Aprobado"] = "#16A34A",
        ["Realizada"] = "#16A34A",
        ["Alquilada"] = "#10B981",
        ["Alquilado"] = "#10B981",
        ["Captada"] = "#0D9488",
        ["Captado"] = "#0D9488",
        ["Cerrada"] = "#166534",
        ["Cerrada exitosa"] = "#166534",

        // Precaución — cálidos en distintos tonos
        ["Pendiente de revision"] = "#FACC15",
        ["Pendiente de revisión"] = "#FACC15",
        ["Observada"] = "#F97316",
        ["Observado"] = "#F97316",

        // En proceso — azules
        ["En evaluación"] = "#2563EB",
        ["En evaluacion"] = "#2563EB",
        ["En revisión"] = "#3B82F6",
        ["En revision"] = "#3B82F6",
        ["Abierta"] = "#2563EB",
        ["Registrada"] = "#6366F1",

        // Negativos — rojos en distintos tonos
        ["Rechazada"] = "#DC2626",
        ["Rechazado"] = "#DC2626",
        ["Vencida"] = "#9F1239",
        ["Descartada"] = "#B91C1C",
        ["Descartado"] = "#B91C1C",
        ["Cancelada"] = "#E11D48",
    };

    // Respaldo por familia para nombres no catalogados; rota por índice para no repetir
    // color con etapas vecinas del mismo "tipo".
    private static readonly string[] Verdes = { "#22C55E", "#166534", "#10B981", "#0D9488", "#65A30D" };
    private static readonly string[] Calidos = { "#FACC15", "#F97316", "#EAB308", "#FB923C" };
    private static readonly string[] Rojos = { "#DC2626", "#9F1239", "#E11D48", "#B91C1C" };
    private static readonly string[] Azules = { "#2563EB", "#3B82F6", "#6366F1", "#0EA5E9" };

    public static string PorEtapa(string? nombre, int indice = 0)
    {
        if (!string.IsNullOrWhiteSpace(nombre) && PorNombre.TryGetValue(nombre.Trim(), out var exacto))
            return exacto;

        var n = (nombre ?? "").Trim().ToLowerInvariant();
        if (n.Contains("rechaz") || n.Contains("descart") || n.Contains("cancel")
            || n.Contains("venc") || n.Contains("no contin") || n.Contains("no favor")
            || n.Contains("rescind") || n.Contains("anul"))
            return Rojos[indice % Rojos.Length];
        if (n.Contains("observ") || n.Contains("pend"))
            return Calidos[indice % Calidos.Length];
        if (n.Contains("activ") || n.Contains("cerrad") || n.Contains("alquil")
            || n.Contains("aprob") || n.Contains("captad") || n.Contains("exitos")
            || n.Contains("realiz") || n.Contains("vigente") || n.Contains("firmad"))
            return Verdes[indice % Verdes.Length];
        return Azules[indice % Azules.Length];
    }
}
