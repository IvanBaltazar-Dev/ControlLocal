using ControlLocal.Web.Data;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace ControlLocal.Web.Services;

// Modelo de presentación de la ficha de propiedad. Une datos de la captación,
// el local prospectado y el propietario en una sola vista lista para mostrar
// (pantalla) y exportar (PDF). Se construye con FichaBuilder.
public class FichaModel
{
    public string Codigo { get; set; } = "";
    public string Direccion { get; set; } = "";
    public string Distrito { get; set; } = "";
    public string Estado { get; set; } = "";
    public int AreaM2 { get; set; }
    public string Rubro { get; set; } = "";
    public string Ambientes { get; set; } = "";
    public string Antiguedad { get; set; } = "";
    public string Referencia { get; set; } = "";
    public string PrecioTexto { get; set; } = "";       // ej. "2 100"
    public string ComisionTexto { get; set; } = "";      // ej. "5.0"
    public string VigenciaTexto { get; set; } = "";
    public string DiasRestantesTexto { get; set; } = "";
    public string PropietarioNombre { get; set; } = "";
    public string PropietarioTipo { get; set; } = "";
    public string PropietarioDocumento { get; set; } = "";
    public string PropietarioTelefono { get; set; } = "";
    public string PropietarioCorreo { get; set; } = "";
    public string AgenteNombre { get; set; } = "";
    public string Descripcion { get; set; } = "";
    public string GeneradoTexto { get; set; } = "";
}

// Centraliza la derivación del modelo de ficha para que la pantalla y el PDF
// muestren exactamente lo mismo. Cruza captación → local → propietario con
// emparejamiento laxo y usa valores de respaldo razonables cuando no hay match.
public static class FichaBuilder
{
    public static FichaModel Build(string? codigo, ICaptacionService capSvc, ILocalService localSvc, IPropietarioService propSvc)
    {
        var cap = (string.IsNullOrEmpty(codigo) ? null : capSvc.ByCodigo(codigo)) ?? capSvc.All().First();

        var local = localSvc.All().FirstOrDefault(l => Loose(l.Direccion, cap.DireccionLocal));
        var prop = propSvc.All().FirstOrDefault(p => Loose(p.Nombre, cap.PropietarioNombre));

        var area = cap.AreaM2 > 0 ? cap.AreaM2 : (local?.AreaM2 ?? 0);
        var rubro = local?.Rubro is { Length: > 0 } r ? r : "Restaurante / Café";
        var precio = local?.PrecioReferencialTexto is { Length: > 0 } pr ? pr : "2 100";
        var comision = cap.ComisionPactadaTexto is { Length: > 0 } c && c != "—" ? c : "5.0";

        return new FichaModel
        {
            Codigo = cap.CodigoCaptacion,
            Direccion = cap.DireccionLocal,
            Distrito = cap.DistritoLocal,
            Estado = cap.Estado,
            AreaM2 = area,
            Rubro = rubro,
            Ambientes = "3 ambientes",
            Antiguedad = "Buen estado de conservación",
            Referencia = "A media cuadra de la avenida principal",
            PrecioTexto = precio,
            ComisionTexto = comision,
            VigenciaTexto = string.IsNullOrEmpty(cap.VigenciaTexto) ? "Por definir" : cap.VigenciaTexto,
            DiasRestantesTexto = cap.DiasRestantesTexto,
            PropietarioNombre = prop?.Nombre ?? cap.PropietarioNombre,
            PropietarioTipo = prop?.TipoPersona ?? "Persona jurídica · RUC",
            PropietarioDocumento = prop?.NumeroDocumento ?? "—",
            PropietarioTelefono = prop?.Telefono ?? "—",
            PropietarioCorreo = prop?.Correo ?? "—",
            AgenteNombre = string.IsNullOrEmpty(cap.NombreAgenteResponsable) ? "Valentina Mora" : cap.NombreAgenteResponsable,
            Descripcion = $"Local comercial ubicado en {cap.DireccionLocal}, {cap.DistritoLocal}. " +
                          $"Espacio de {area} m² ideal para el rubro {rubro.ToLowerInvariant()}. " +
                          "Cuenta con frontis a la calle, instalaciones eléctricas y sanitarias operativas, " +
                          "y excelente afluencia peatonal en la zona.",
            GeneradoTexto = $"Generado el {DateTime.Now:dd/MM/yyyy} a las {DateTime.Now:HH:mm}",
        };
    }

    // Emparejamiento laxo de cadenas (ignora mayúsculas/espacios extra).
    private static bool Loose(string a, string b)
    {
        a = Norm(a); b = Norm(b);
        if (a.Length == 0 || b.Length == 0) return false;
        return a == b || a.Contains(b) || b.Contains(a);
    }

    private static string Norm(string s) => (s ?? "").Trim().ToLowerInvariant();
}

// Documento PDF de la ficha de propiedad — una página A4 elegante y exportable.
public class FichaPropiedadDocument : IDocument
{
    private const string Navy = "#061A4D";
    private const string Navy3 = "#0E2C66";
    private const string Primary = "#005BFF";
    private const string Ink = "#1B2435";
    private const string Body = "#3B4453";
    private const string Muted = "#6B7384";
    private const string Line = "#E2E6EC";
    private const string Soft = "#F4F7FB";
    private const string White = "#FFFFFF";
    private const string SoftBlue = "#9FB2D8";

    private readonly FichaModel _m;
    public FichaPropiedadDocument(FichaModel m) => _m = m;

    public DocumentMetadata GetMetadata() => new()
    {
        Title = $"Ficha de propiedad {_m.Codigo}",
        Author = "ControlLocal",
    };

    public void Compose(IDocumentContainer container)
    {
        container.Page(page =>
        {
            page.Size(PageSizes.A4);
            page.Margin(0);
            page.DefaultTextStyle(x => x.FontSize(10).FontColor(Ink).FontFamily(Fonts.Calibri));

            page.Content().Column(col =>
            {
                Header(col.Item());
                TitleBlock(col.Item().PaddingHorizontal(36).PaddingTop(22));
                Metrics(col.Item().PaddingHorizontal(36).PaddingTop(16));
                Caracteristicas(col.Item().PaddingHorizontal(36).PaddingTop(20));
                CondicionesYPropietario(col.Item().PaddingHorizontal(36).PaddingTop(18));
                Descripcion(col.Item().PaddingHorizontal(36).PaddingTop(18));
            });

            Footer(page.Footer());
        });
    }

    private void Header(IContainer c) => c.Column(col =>
    {
        col.Item().Background(Navy).PaddingVertical(22).PaddingHorizontal(36).Row(row =>
        {
            row.RelativeItem().Column(b =>
            {
                b.Item().Text("ControlLocal").FontSize(17).Bold().FontColor(White);
                b.Item().Text("Gestión de locales comerciales").FontSize(9).FontColor(SoftBlue);
            });
            row.ConstantItem(220).Column(b =>
            {
                b.Item().AlignRight().Text("FICHA DE PROPIEDAD").FontSize(11).Bold().FontColor(White).LetterSpacing(0.08f);
                b.Item().AlignRight().PaddingTop(3).Text(_m.Codigo).FontSize(12).FontColor(SoftBlue);
            });
        });
        col.Item().Background(Primary).Height(4);
    });

    private void TitleBlock(IContainer c) => c.Column(col =>
    {
        col.Item().Text(_m.Direccion).FontSize(20).Bold().FontColor(Navy);
        col.Item().PaddingTop(4).Row(row =>
        {
            row.AutoItem().AlignMiddle().Text(_m.Distrito).FontSize(11).FontColor(Muted);
            row.AutoItem().AlignMiddle().PaddingHorizontal(8).Text("•").FontColor(Line);
            row.AutoItem().AlignMiddle().Text(_m.Rubro).FontSize(11).FontColor(Muted);
            row.RelativeItem().AlignRight().Background(Soft).Border(1).BorderColor(Line)
               .PaddingVertical(4).PaddingHorizontal(11).Text(_m.Estado).FontSize(9).Bold().FontColor(Navy3);
        });
    });

    private void Metrics(IContainer c) => c.Row(row =>
    {
        MetricBox(row.RelativeItem(), "ÁREA", $"{_m.AreaM2} m²", null);
        row.ConstantItem(10);
        MetricBox(row.RelativeItem(), "PRECIO REFERENCIAL", $"USD {_m.PrecioTexto}", "/ mes");
        row.ConstantItem(10);
        MetricBox(row.RelativeItem(), "COMISIÓN", $"{_m.ComisionTexto}%", null);
        row.ConstantItem(10);
        MetricBox(row.RelativeItem(), "VIGENCIA", _m.VigenciaTexto, null);
    });

    private void MetricBox(IContainer c, string k, string v, string? unit) =>
        c.Background(Soft).Border(1).BorderColor(Line).Padding(12).Column(col =>
        {
            col.Item().Text(k).FontSize(7.5f).Bold().FontColor(Muted).LetterSpacing(0.05f);
            col.Item().PaddingTop(5).Text(t =>
            {
                t.Span(v).FontSize(14).Bold().FontColor(Navy);
                if (!string.IsNullOrEmpty(unit)) t.Span($" {unit}").FontSize(8).FontColor(Muted);
            });
        });

    private void Caracteristicas(IContainer c) => c.Column(col =>
    {
        SectionHeader(col.Item(), "Características de la propiedad");
        col.Item().PaddingTop(10).Row(row =>
        {
            row.RelativeItem().Column(left =>
            {
                KeyValue(left, "Rubro sugerido", _m.Rubro);
                KeyValue(left, "Ambientes", _m.Ambientes);
                KeyValue(left, "Referencia", _m.Referencia);
            });
            row.ConstantItem(24);
            row.RelativeItem().Column(right =>
            {
                KeyValue(right, "Distrito", _m.Distrito);
                KeyValue(right, "Estado de conservación", _m.Antiguedad);
                KeyValue(right, "Días restantes", string.IsNullOrEmpty(_m.DiasRestantesTexto) ? "—" : _m.DiasRestantesTexto);
            });
        });
    });

    private void CondicionesYPropietario(IContainer c) => c.Row(row =>
    {
        row.RelativeItem().Border(1).BorderColor(Line).Padding(14).Column(col =>
        {
            SectionHeader(col.Item(), "Condiciones comerciales");
            col.Item().PaddingTop(8).Column(inner =>
            {
                KeyValue(inner, "Monto mensual", $"USD {_m.PrecioTexto}");
                KeyValue(inner, "Comisión pactada", $"{_m.ComisionTexto}%");
                KeyValue(inner, "Vigencia", _m.VigenciaTexto);
            });
        });
        row.ConstantItem(14);
        row.RelativeItem().Border(1).BorderColor(Line).Padding(14).Column(col =>
        {
            SectionHeader(col.Item(), "Propietario");
            col.Item().PaddingTop(8).Column(inner =>
            {
                KeyValue(inner, "Nombre / razón social", _m.PropietarioNombre);
                KeyValue(inner, "Identificación", $"{_m.PropietarioTipo} · {_m.PropietarioDocumento}");
                KeyValue(inner, "Teléfono", _m.PropietarioTelefono);
                KeyValue(inner, "Correo", _m.PropietarioCorreo);
                KeyValue(inner, "Agente responsable", _m.AgenteNombre);
            });
        });
    });

    private void Descripcion(IContainer c) => c.Column(col =>
    {
        SectionHeader(col.Item(), "Descripción");
        col.Item().PaddingTop(8).Text(_m.Descripcion).FontSize(10).LineHeight(1.5f).FontColor(Body);
    });

    private static void SectionHeader(IContainer c, string text) =>
        c.BorderBottom(2).BorderColor(Primary).PaddingBottom(4)
         .Text(text).FontSize(12).Bold().FontColor(Navy);

    private void KeyValue(ColumnDescriptor col, string k, string v) =>
        col.Item().PaddingBottom(7).Row(row =>
        {
            row.ConstantItem(150).Text(k).FontSize(9).FontColor(Muted);
            row.RelativeItem().Text(v).FontSize(10).SemiBold().FontColor(Ink);
        });

    private void Footer(IContainer c) => c.BorderTop(1).BorderColor(Line).PaddingHorizontal(36).PaddingVertical(12).Row(row =>
    {
        row.RelativeItem().Column(col =>
        {
            col.Item().Text(_m.GeneradoTexto).FontSize(8).FontColor(Muted);
            col.Item().Text("Documento confidencial · uso interno de ControlLocal").FontSize(8).FontColor(Muted);
        });
        row.ConstantItem(120).AlignRight().AlignBottom().Text(t =>
        {
            t.Span("Página ").FontSize(8).FontColor(Muted);
            t.CurrentPageNumber().FontSize(8).FontColor(Muted);
            t.Span(" de ").FontSize(8).FontColor(Muted);
            t.TotalPages().FontSize(8).FontColor(Muted);
        });
    });
}
