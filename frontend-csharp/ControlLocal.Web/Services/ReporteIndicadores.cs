using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace ControlLocal.Web.Services;

public sealed record IndicadorReporte(string Icono, string Etiqueta, string Valor, string Delta, string Tono);
public sealed record SerieMensual(string[] Etiquetas, double[] Valores);
public sealed record EtapaReporte(string Color, string Nombre, string Valor);
public sealed record DesempenoFila(string Nombre, string Captaciones, string Cierres, string Conversion, double[] Tendencia);

public sealed record DatosReporte(
    string Ambito,
    IndicadorReporte[] Indicadores,
    SerieMensual CierresPorMes,
    EtapaReporte[] Etapas,
    string[][] Embudo,
    DesempenoFila[] Desempeno);

// Única fuente de los indicadores de reportes: la pantalla y el PDF exportado
// leen de aquí, de modo que ambos muestran exactamente lo mismo por período.
public static class ReporteIndicadores
{
    public static readonly IReadOnlyList<EnumOptionPeriodo> Periodos =
    [
        new("6m", "Últimos 6 meses"),
        new("anio", "Este año"),
        new("trimestre", "Último trimestre"),
    ];

    public sealed record EnumOptionPeriodo(string Codigo, string Etiqueta);

    private static readonly string[] EtiquetasBase = { "Dic", "Ene", "Feb", "Mar", "Abr", "May" };
    private static readonly double[] CierresGlobal = { 6, 8, 7, 11, 9, 14 };
    private static readonly double[] CierresEquipo = { 3, 4, 4, 6, 5, 8 };

    public static DatosReporte Para(bool esAdmin, string periodo)
    {
        var (etiquetas, valores) = Recortar(periodo, esAdmin ? CierresGlobal : CierresEquipo);
        var cierres = (int)valores.Sum();

        var indicadores = esAdmin
            ? new[]
            {
                new IndicadorReporte("pin", "Captaciones totales", (cierres * 3).ToString(), "+12%", "navy"),
                new IndicadorReporte("check", "Operaciones cerradas", cierres.ToString(), "+8%", "green"),
                new IndicadorReporte("target", "Tasa de conversión", "22%", "+2 pts", "blue"),
                new IndicadorReporte("briefcase", "Comisión generada", $"USD {cierres * 2.3m:0} K", "+15%", "blue"),
            }
            : new[]
            {
                new IndicadorReporte("pin", "Captaciones del equipo", (cierres * 2).ToString(), "+6 este período", "navy"),
                new IndicadorReporte("check", "Cierres del equipo", cierres.ToString(), "+2 vs período ant.", "green"),
                new IndicadorReporte("target", "Conversión", "22%", "+3 pts", "blue"),
                new IndicadorReporte("calendar", "Visitas realizadas", (cierres * 3).ToString(), "+11", "blue"),
            };

        var etapas = new[]
        {
            new EtapaReporte("#005BFF", "Captada", "22"),
            new EtapaReporte("#00AEEF", "Con interacciones", "18"),
            new EtapaReporte("#2F7D52", "Alquilada", "14"),
            new EtapaReporte("#D64550", "Rechazada", "6"),
            new EtapaReporte("#7E8794", "No continúa", "4"),
        };

        var embudo = new[]
        {
            new[] { "Captaciones activas", "64", "100%", "#005BFF" },
            new[] { "Con visita realizada", "41", "64%", "#00AEEF" },
            new[] { "Con solicitud creada", "28", "44%", "#00AEEF" },
            new[] { "Cerradas exitosas", "14", "22%", "#2F7D52" },
        };

        var desempeno = esAdmin
            ? new[]
            {
                new DesempenoFila("Ricardo Salas", "38", "9", "24%", new double[] { 4, 6, 5, 8, 7, 9 }),
                new DesempenoFila("Mariana Quintero", "31", "8", "26%", new double[] { 3, 5, 6, 6, 8, 9 }),
                new DesempenoFila("Felipe Andrade", "27", "5", "19%", new double[] { 4, 4, 5, 5, 6, 5 }),
                new DesempenoFila("Sandra Ríos", "22", "4", "18%", new double[] { 2, 3, 4, 4, 5, 4 }),
            }
            : new[]
            {
                new DesempenoFila("Valentina Mora", "14", "5", "26%", new double[] { 2, 3, 4, 4, 5, 6 }),
                new DesempenoFila("Carolina Vega", "9", "3", "22%", new double[] { 1, 2, 3, 3, 4, 4 }),
                new DesempenoFila("Andrea Torres", "6", "2", "20%", new double[] { 1, 1, 2, 2, 3, 3 }),
                new DesempenoFila("Paola Reyes", "7", "2", "18%", new double[] { 1, 2, 2, 2, 2, 3 }),
            };

        return new DatosReporte(
            esAdmin ? "Reportes globales" : "Reportes de equipo",
            indicadores, new SerieMensual(etiquetas, valores), etapas, embudo, desempeno);
    }

    private static (string[] Etiquetas, double[] Valores) Recortar(string periodo, double[] serie)
    {
        // "anio": desde enero; "trimestre": últimos 3 meses; "6m": serie completa.
        var desde = periodo switch
        {
            "anio" => 1,
            "trimestre" => serie.Length - 3,
            _ => 0,
        };
        return (EtiquetasBase[desde..], serie[desde..]);
    }
}

// Reporte de indicadores exportable a PDF (A4, mismo lenguaje visual que la ficha).
public class ReporteIndicadoresDocument : IDocument
{
    private const string Navy = "#061A4D";
    private const string Ink = "#1B2435";
    private const string Body = "#3B4453";
    private const string Muted = "#6B7384";
    private const string Line = "#E2E6EC";
    private const string Soft = "#F4F7FB";

    private readonly DatosReporte _d;
    private readonly string _periodoEtiqueta;

    public ReporteIndicadoresDocument(DatosReporte datos, string periodoEtiqueta)
    {
        _d = datos;
        _periodoEtiqueta = periodoEtiqueta;
    }

    public DocumentMetadata GetMetadata() => new()
    {
        Title = $"{_d.Ambito} · ControlLocal",
        Author = "ControlLocal",
    };

    public void Compose(IDocumentContainer container)
    {
        container.Page(page =>
        {
            page.Size(PageSizes.A4);
            page.Margin(36);
            page.DefaultTextStyle(t => t.FontSize(10).FontColor(Body));

            page.Header().Column(col =>
            {
                col.Item().Row(row =>
                {
                    row.RelativeItem().Column(c =>
                    {
                        c.Item().Text("ControlLocal").FontSize(16).Bold().FontColor(Navy);
                        c.Item().Text(_d.Ambito).FontSize(12).FontColor(Ink);
                    });
                    row.ConstantItem(180).AlignRight().Column(c =>
                    {
                        c.Item().Text(_periodoEtiqueta).FontSize(10).FontColor(Muted);
                        c.Item().Text($"Generado el {DateTime.Now:dd/MM/yyyy HH:mm}").FontSize(8).FontColor(Muted);
                    });
                });
                col.Item().PaddingTop(10).LineHorizontal(1).LineColor(Line);
            });

            page.Content().PaddingVertical(14).Column(col =>
            {
                col.Spacing(14);

                col.Item().Row(row =>
                {
                    foreach (var i in _d.Indicadores)
                    {
                        row.RelativeItem().Padding(4).Background(Soft).Padding(8).Column(c =>
                        {
                            c.Item().Text(i.Etiqueta).FontSize(8).FontColor(Muted);
                            c.Item().Text(i.Valor).FontSize(15).Bold().FontColor(Ink);
                            c.Item().Text(i.Delta).FontSize(8).FontColor(Muted);
                        });
                    }
                });

                col.Item().Element(e => Seccion(e, "Cierres por mes", t =>
                {
                    t.ColumnsDefinition(c =>
                    {
                        foreach (var _ in _d.CierresPorMes.Etiquetas) c.RelativeColumn();
                    });
                    foreach (var etiqueta in _d.CierresPorMes.Etiquetas)
                        t.Cell().Element(CeldaCabecera).Text(etiqueta);
                    foreach (var valor in _d.CierresPorMes.Valores)
                        t.Cell().Element(Celda).Text(valor.ToString("0"));
                }));

                col.Item().Element(e => Seccion(e, "Embudo de conversión", t =>
                {
                    t.ColumnsDefinition(c =>
                    {
                        c.RelativeColumn(3);
                        c.RelativeColumn();
                        c.RelativeColumn();
                    });
                    t.Cell().Element(CeldaCabecera).Text("Etapa");
                    t.Cell().Element(CeldaCabecera).Text("Operaciones");
                    t.Cell().Element(CeldaCabecera).Text("Porcentaje");
                    foreach (var fila in _d.Embudo)
                    {
                        t.Cell().Element(Celda).Text(fila[0]);
                        t.Cell().Element(Celda).Text(fila[1]);
                        t.Cell().Element(Celda).Text(fila[2]);
                    }
                }));

                col.Item().Element(e => Seccion(e, "Desempeño", t =>
                {
                    t.ColumnsDefinition(c =>
                    {
                        c.RelativeColumn(3);
                        c.RelativeColumn();
                        c.RelativeColumn();
                        c.RelativeColumn();
                    });
                    t.Cell().Element(CeldaCabecera).Text("Responsable");
                    t.Cell().Element(CeldaCabecera).Text("Captaciones");
                    t.Cell().Element(CeldaCabecera).Text("Cierres");
                    t.Cell().Element(CeldaCabecera).Text("Conversión");
                    foreach (var fila in _d.Desempeno)
                    {
                        t.Cell().Element(Celda).Text(fila.Nombre);
                        t.Cell().Element(Celda).Text(fila.Captaciones);
                        t.Cell().Element(Celda).Text(fila.Cierres);
                        t.Cell().Element(Celda).Text(fila.Conversion);
                    }
                }));
            });

            page.Footer().AlignCenter().Text(t =>
            {
                t.Span("ControlLocal · Reporte de indicadores · Página ").FontSize(8).FontColor(Muted);
                t.CurrentPageNumber().FontSize(8).FontColor(Muted);
            });
        });
    }

    private static void Seccion(IContainer contenedor, string titulo, Action<TableDescriptor> tabla)
    {
        contenedor.Column(c =>
        {
            c.Item().Text(titulo).FontSize(11).Bold().FontColor(Ink);
            c.Item().PaddingTop(6).Table(tabla);
        });
    }

    private static IContainer CeldaCabecera(IContainer c) =>
        c.Background(Soft).BorderBottom(1).BorderColor(Line).Padding(6);

    private static IContainer Celda(IContainer c) =>
        c.BorderBottom(1).BorderColor(Line).Padding(6);
}
