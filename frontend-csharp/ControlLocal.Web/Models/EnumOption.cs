namespace ControlLocal.Web.Models;

public sealed record EnumOption(string Code, string Label);

public static class EnumCatalog
{
    public static readonly IReadOnlyList<EnumOption> TiposDocumentoIdentidad =
    [
        new("D", "DNI"),
        new("R", "RUC"),
        new("C", "Carnet de extranjeria"),
        new("P", "Pasaporte")
    ];

    public static readonly IReadOnlyList<EnumOption> TiposPersona =
    [
        new("N", "Natural"),
        new("J", "Juridica")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosActivoInactivo =
    [
        new("A", "Activo"),
        new("I", "Inactivo")
    ];

    public static readonly IReadOnlyList<EnumOption> RolesUsuarioInterno =
    [
        new("B", "Broker"),
        new("A", "Agente")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosOperativoAgente =
    [
        new("D", "Disponible"),
        new("L", "Licencia"),
        new("N", "No disponible")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosLocalComercial =
    [
        new("D", "Disponible"),
        new("N", "No disponible"),
        new("I", "Inactivo")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosCaptacion =
    [
        new("P", "Pendiente de revision"),
        new("O", "Observada"),
        new("R", "Rechazada"),
        new("A", "Activa"),
        new("C", "Cerrada"),
        new("V", "Vencida")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosOportunidad =
    [
        new("A", "Abierta"),
        new("S", "Solicitud creada"),
        new("N", "No continua"),
        new("F", "Finalizada exitosa"),
        new("X", "Finalizada no favorable")
    ];

    public static readonly IReadOnlyList<EnumOption> CanalesContacto =
    [
        new("L", "Llamada"),
        new("W", "WhatsApp"),
        new("E", "Email"),
        new("P", "Presencial"),
        new("O", "Otro")
    ];

    public static readonly IReadOnlyList<EnumOption> ResultadosInteraccion =
    [
        new("P", "Pendiente"),
        new("I", "Interesado"),
        new("N", "No interesado"),
        new("S", "Seguimiento"),
        new("D", "Descartado")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosVisita =
    [
        new("P", "Programada"),
        new("G", "Reprogramada"),
        new("C", "Cancelada"),
        new("R", "Realizada")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosSolicitudAlquiler =
    [
        new("G", "Registrada"),
        new("E", "En revision"),
        new("O", "Observada"),
        new("A", "Aprobada"),
        new("R", "Rechazada"),
        new("D", "Desistida")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosDocumentoSolicitud =
    [
        new("R", "Registrado"),
        new("O", "Observado"),
        new("V", "Validado")
    ];

    public static readonly IReadOnlyList<EnumOption> ResultadosRevisionDocumento =
    [
        new("P", "Pendiente"),
        new("C", "Conforme"),
        new("O", "Observado")
    ];

    public static readonly IReadOnlyList<EnumOption> TiposEvaluacionSolicitud =
    [
        new("P", "Preliminar"),
        new("O", "Observacion"),
        new("F", "Final")
    ];

    public static readonly IReadOnlyList<EnumOption> ResultadosEvaluacionSolicitud =
    [
        new("A", "Aprobada"),
        new("R", "Rechazada"),
        new("O", "Observada")
    ];

    public static readonly IReadOnlyList<EnumOption> TiposDocumentoSolicitud =
    [
        new("I", "Documento de identidad"),
        new("R", "Ficha o constancia RUC"),
        new("V", "Vigencia de poder"),
        new("P", "Poder de representacion"),
        new("E", "Sustento economico"),
        new("G", "Documento de garantia"),
        new("D", "Declaracion jurada"),
        new("O", "Otro")
    ];

    public static readonly IReadOnlyList<EnumOption> MotivosNoContinuidad =
    [
        new("P", "Precio"),
        new("U", "Ubicacion"),
        new("C", "Condiciones del contrato"),
        new("L", "Local no adecuado"),
        new("N", "Cliente no responde"),
        new("E", "Encontro otra opcion"),
        new("O", "Otro")
    ];

    public static string CodeFor(IEnumerable<EnumOption> options, string label) =>
        options.FirstOrDefault(x => x.Label == label)?.Code ?? label;
}
