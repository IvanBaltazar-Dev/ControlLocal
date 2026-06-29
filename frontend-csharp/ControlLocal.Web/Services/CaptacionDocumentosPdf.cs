using ControlLocal.Web.Models.Captaciones;
using ControlLocal.Web.Models.Locales;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace ControlLocal.Web.Services;

public static class CaptacionDocumentosPdf
{
    private const string Navy = "#061A4D";
    private const string Primary = "#005BFF";
    private const string Border = "#E2E6EC";
    private const string Soft = "#F4F7FB";
    private const string Muted = "#6B7384";

    public static byte[] FichaCaptacion(CaptacionDto cap, LocalComercialDto? local) =>
        Document.Create(container =>
        {
            container.Page(page =>
            {
                page.Size(PageSizes.A4);
                page.Margin(34);
                page.DefaultTextStyle(x => x.FontSize(10).FontFamily(Fonts.Calibri).FontColor("#1B2435"));
                Header(page, "Ficha de captacion", cap.CodigoCaptacion);
                page.Content().Column(col =>
                {
                    Titulo(col, cap.DireccionLocal, cap.DistritoLocal);
                    DatosClave(col, cap, local);
                    Seccion(col, "Condiciones comerciales", new[]
                    {
                        ("Comision pactada", Texto(cap.ComisionPactadaTexto, "%")),
                        ("Vigencia", Texto(cap.VigenciaTexto)),
                        ("Urgencia", cap.Urgencia is int u ? $"{u} / 5" : "-"),
                        ("Exclusividad", cap.Exclusividad == true ? "Encargo exclusivo" : cap.Exclusividad == false ? "No exclusivo" : "-"),
                        ("Estado", Texto(cap.Estado)),
                        ("Agente responsable", Texto(cap.NombreAgenteResponsable)),
                    });
                    Seccion(col, "Propiedad", new[]
                    {
                        ("Propietario", Texto(cap.PropietarioNombre)),
                        ("Direccion", Texto(cap.DireccionLocal)),
                        ("Distrito", Texto(cap.DistritoLocal)),
                        ("Area", cap.AreaM2 > 0 ? $"{cap.AreaM2} m2" : "-"),
                        ("Rubro", Texto(cap.Rubro)),
                        ("Precio referencial", local is null ? "-" : $"USD {local.PrecioReferencialTexto}"),
                    });
                    Observaciones(col, cap.Observaciones);
                });
                Footer(page);
            });
        }).GeneratePdf();

    public static byte[] ContratoExclusividad(CaptacionDto cap, LocalComercialDto? local) =>
        Document.Create(container =>
        {
            container.Page(page =>
            {
                page.Size(PageSizes.A4);
                page.Margin(34);
                page.DefaultTextStyle(x => x.FontSize(10).FontFamily(Fonts.Calibri).FontColor("#1B2435"));
                Header(page, "Contrato de encargo comercial", cap.CodigoCaptacion);
                page.Content().Column(col =>
                {
                    Titulo(col, "Encargo de intermediacion para alquiler comercial", cap.DireccionLocal);
                    Parrafo(col, $"Propietario: {Texto(cap.PropietarioNombre)}.");
                    Parrafo(col, $"Agente responsable: {Texto(cap.NombreAgenteResponsable)}.");
                    Parrafo(col, $"Propiedad: {Texto(cap.DireccionLocal)}, {Texto(cap.DistritoLocal)}, area aproximada {Area(cap, local)}.");
                    Parrafo(col, $"Comision pactada: {Texto(cap.ComisionPactadaTexto, "%")} sobre las condiciones comerciales aprobadas.");
                    Parrafo(col, $"Vigencia del encargo: {Texto(cap.VigenciaTexto)}.");
                    Parrafo(col, $"Modalidad de exclusividad: {(cap.Exclusividad == true ? "Encargo exclusivo" : cap.Exclusividad == false ? "No exclusivo" : "No registrado")}.");
                    Parrafo(col, "El agente queda autorizado a presentar la propiedad a clientes interesados, coordinar visitas, registrar oportunidades comerciales y elevar solicitudes de alquiler dentro del sistema ControlLocal.");
                    Parrafo(col, "Las condiciones finales del alquiler se sujetan a la aprobacion documentaria y comercial de las partes.");
                    Firmas(col);
                });
                Footer(page);
            });
        }).GeneratePdf();

    private static void Header(PageDescriptor page, string titulo, string codigo)
    {
        page.Header().Background(Navy).Padding(18).Row(row =>
        {
            row.RelativeItem().Column(col =>
            {
                col.Item().Text("ControlLocal").FontSize(18).Bold().FontColor(Colors.White);
                col.Item().Text(titulo).FontSize(10).FontColor("#D6E4FF");
            });
            row.ConstantItem(140).AlignRight().Text(codigo).FontSize(12).Bold().FontColor(Colors.White);
        });
    }

    private static void Footer(PageDescriptor page)
    {
        page.Footer().BorderTop(1).BorderColor(Border).PaddingTop(8).Row(row =>
        {
            row.RelativeItem().Text($"Generado el {DateTime.Now:dd/MM/yyyy HH:mm}").FontSize(8).FontColor(Muted);
            row.ConstantItem(140).AlignRight().Text("Expediente comercial").FontSize(8).FontColor(Muted);
        });
    }

    private static void Titulo(ColumnDescriptor col, string titulo, string subtitulo)
    {
        col.Item().PaddingTop(18).Text(titulo).FontSize(18).Bold().FontColor(Navy);
        col.Item().PaddingTop(4).Text(subtitulo).FontSize(10).FontColor(Muted);
    }

    private static void DatosClave(ColumnDescriptor col, CaptacionDto cap, LocalComercialDto? local)
    {
        col.Item().PaddingTop(18).Grid(grid =>
        {
            grid.Columns(3);
            Key(grid, "Codigo", cap.CodigoCaptacion);
            Key(grid, "Fecha captacion", cap.FechaCaptacion?.ToString("dd/MM/yyyy") ?? "-");
            Key(grid, "Etapa", cap.Estado);
            Key(grid, "Area", Area(cap, local));
            Key(grid, "Distrito", cap.DistritoLocal);
            Key(grid, "Rubro", cap.Rubro);
        });
    }

    private static void Seccion(ColumnDescriptor col, string titulo, IEnumerable<(string K, string V)> filas)
    {
        col.Item().PaddingTop(18).Text(titulo).FontSize(12).Bold().FontColor(Navy);
        col.Item().PaddingTop(8).Table(table =>
        {
            table.ColumnsDefinition(columns =>
            {
                columns.ConstantColumn(145);
                columns.RelativeColumn();
            });
            foreach (var (k, v) in filas)
            {
                table.Cell().BorderBottom(1).BorderColor(Border).PaddingVertical(6).Text(k).FontColor(Muted);
                table.Cell().BorderBottom(1).BorderColor(Border).PaddingVertical(6).Text(Texto(v)).SemiBold();
            }
        });
    }

    private static void Observaciones(ColumnDescriptor col, string? observaciones)
    {
        if (string.IsNullOrWhiteSpace(observaciones))
            return;
        col.Item().PaddingTop(18).Background(Soft).Border(1).BorderColor(Border).Padding(12).Column(block =>
        {
            block.Item().Text("Observaciones").FontSize(11).Bold().FontColor(Navy);
            block.Item().PaddingTop(5).Text(observaciones).FontSize(10);
        });
    }

    private static void Parrafo(ColumnDescriptor col, string texto) =>
        col.Item().PaddingTop(11).Text(texto).FontSize(10).LineHeight(1.35f);

    private static void Firmas(ColumnDescriptor col)
    {
        col.Item().PaddingTop(52).Row(row =>
        {
            row.RelativeItem().BorderTop(1).BorderColor(Border).PaddingTop(8).AlignCenter().Text("Propietario").FontSize(9).FontColor(Muted);
            row.ConstantItem(40);
            row.RelativeItem().BorderTop(1).BorderColor(Border).PaddingTop(8).AlignCenter().Text("Agente responsable").FontSize(9).FontColor(Muted);
        });
    }

    private static void Key(GridDescriptor grid, string k, string v)
    {
        grid.Item().Background(Soft).Border(1).BorderColor(Border).Padding(10).Column(col =>
        {
            col.Item().Text(k).FontSize(8).FontColor(Muted);
            col.Item().PaddingTop(3).Text(Texto(v)).FontSize(11).Bold().FontColor(Primary);
        });
    }

    private static string Area(CaptacionDto cap, LocalComercialDto? local)
    {
        var area = cap.AreaM2 > 0 ? cap.AreaM2 : local?.AreaM2 ?? 0;
        return area > 0 ? $"{area} m2" : "-";
    }

    private static string Texto(string? valor, string? sufijo = null)
    {
        if (string.IsNullOrWhiteSpace(valor) || valor == "-")
            return "-";
        return sufijo is null ? valor : $"{valor}{sufijo}";
    }
}
