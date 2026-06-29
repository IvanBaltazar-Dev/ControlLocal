namespace ControlLocal.Web.Models.Shared;

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
        new("R", "Reunion"),
        new("T", "Portal"),
        new("O", "Otro")
    ];

    public static readonly IReadOnlyList<EnumOption> OperacionesRequerimiento =
    [
        new("A", "Alquiler")
    ];

    public static readonly IReadOnlyList<EnumOption> TiposInmueble =
    [
        new("L", "Local"),
        new("O", "Oficina")
    ];

    public static readonly IReadOnlyList<EnumOption> UsosInmueble =
    [
        new("C", "Comercial")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosPublicacion =
    [
        new("B", "Sin publicar"),
        new("P", "Publicado"),
        new("S", "Pausado"),
        new("C", "Cerrado")
    ];

    public static readonly IReadOnlyList<EnumOption> CanalesPublicacion =
    [
        new("URBANIA", "Urbania"),
        new("ADONDEVIVIR", "AdondeVivir"),
        new("PROPERATI", "Properati"),
        new("NEXO_INMOBILIARIO", "Nexo Inmobiliario"),
        new("FACEBOOK", "Facebook"),
        new("MARKETPLACE", "Marketplace"),
        new("INSTAGRAM", "Instagram"),
        new("WHATSAPP", "WhatsApp"),
        new("WEB_PROPIA", "Web propia"),
        new("REFERIDO", "Referido"),
        new("OTRO", "Otro")
    ];

    public static readonly IReadOnlyList<string> DistritosLima =
    [
        "Ancon",
        "Ate",
        "Barranco",
        "Breña",
        "Carabayllo",
        "Chaclacayo",
        "Chorrillos",
        "Cieneguilla",
        "Comas",
        "El Agustino",
        "Independencia",
        "Jesus Maria",
        "La Molina",
        "La Victoria",
        "Lima",
        "Lince",
        "Los Olivos",
        "Lurigancho-Chosica",
        "Lurin",
        "Magdalena del Mar",
        "Miraflores",
        "Pachacamac",
        "Pucusana",
        "Pueblo Libre",
        "Puente Piedra",
        "Punta Hermosa",
        "Punta Negra",
        "Rimac",
        "San Bartolo",
        "San Borja",
        "San Isidro",
        "San Juan de Lurigancho",
        "San Juan de Miraflores",
        "San Luis",
        "San Martin de Porres",
        "San Miguel",
        "Santa Anita",
        "Santa Maria del Mar",
        "Santa Rosa",
        "Santiago de Surco",
        "Surquillo",
        "Villa El Salvador",
        "Villa Maria del Triunfo"
    ];

    public static readonly IReadOnlyList<string> RubrosComerciales =
    [
        "Restaurante / Cafe",
        "Cafeteria / Postres",
        "Moda / Boutique",
        "Retail",
        "Minimarket / Bodega",
        "Panaderia / Pasteleria",
        "Farmacia / Botica",
        "Salud / Consultorio",
        "Belleza / Barberia",
        "Gimnasio / Fitness",
        "Educacion / Academia",
        "Oficina administrativa",
        "Servicios profesionales",
        "Ferreteria",
        "Veterinaria",
        "Tecnologia / Electronica",
        "Muebles / Decoracion",
        "Almacen / Deposito",
        "Logistica ligera",
        "Automotriz",
        "Mascotas",
        "Entretenimiento",
        "Comida rapida",
        "Dark kitchen",
        "Otro rubro comercial"
    ];

    public static readonly IReadOnlyList<EnumOption> Monedas =
    [
        new("PEN", "Soles"),
        new("USD", "Dolares")
    ];

    public static readonly IReadOnlyList<EnumOption> HitosPrecio =
    [
        new("E", "Esperado"),
        new("R", "Recomendado"),
        new("U", "Autorizado"),
        new("P", "Publicado"),
        new("O", "Ofertado"),
        new("A", "Aceptado"),
        new("C", "Cerrado")
    ];

    public static readonly IReadOnlyList<EnumOption> ObjecionesVisita =
    [
        new("P", "Precio"),
        new("U", "Ubicacion"),
        new("E", "Estado del inmueble"),
        new("C", "Condiciones"),
        new("O", "Otra")
    ];

    public static readonly IReadOnlyList<EnumOption> OpinionesPrecio =
    [
        new("A", "Alto"),
        new("J", "Justo"),
        new("B", "Bajo")
    ];

    public static readonly IReadOnlyList<EnumOption> ProximasAccionesVisita =
    [
        new("V", "Nueva visita"),
        new("O", "Oferta"),
        new("S", "Seguimiento"),
        new("D", "Descartado")
    ];

    public static readonly IReadOnlyList<EnumOption> DesenlacesOportunidad =
    [
        new("F", "Cerrada favorable"),
        new("X", "Caida")
    ];

    public static readonly IReadOnlyList<EnumOption> ResultadosVisita =
    [
        new("P", "Pendiente"),
        new("I", "Interesado"),
        new("N", "No interesado"),
        new("S", "Seguimiento"),
        new("D", "Descartado")
    ];

    public static readonly IReadOnlyList<EnumOption> ResultadosInteraccion = ResultadosVisita;

    public static readonly IReadOnlyList<EnumOption> ResultadosProspeccionInteraccion =
    [
        new("CONTACTADO", "Contactado"),
        new("REUNION_AGENDADA", "Reunion agendada"),
        new("PROPUESTA_ENVIADA", "Propuesta enviada"),
        new("ACEPTA_CAPTAR", "Acepta captar"),
        new("NO_ACEPTA", "No acepta"),
        new("RECONTACTAR", "Recontactar")
    ];

    public static readonly IReadOnlyList<EnumOption> ResultadosCaptacionInteraccion =
    [
        new("DOCS_SOLICITADOS", "Docs solicitados"),
        new("CONDICIONES_AJUSTADAS", "Condiciones ajustadas"),
        new("PUBLICACION_COORDINADA", "Publicacion coordinada"),
        new("PROPIETARIO_OBSERVA", "Propietario observa"),
        new("LISTO_PARA_PUBLICAR", "Listo para publicar"),
        new("PAUSAR_GESTION", "Pausar gestion")
    ];

    public static readonly IReadOnlyList<EnumOption> ResultadosOportunidadInteraccion =
    [
        new("INTERESADO", "Interesado"),
        new("VISITA_AGENDADA", "Visita agendada"),
        new("OFERTA_SOLICITADA", "Oferta solicitada"),
        new("NEGOCIANDO", "Negociando"),
        new("NO_INTERESADO", "No interesado"),
        new("DESCARTADO", "Descartado")
    ];

    public static readonly IReadOnlyList<EnumOption> ResultadosClienteInteraccion =
    [
        new("BUSQUEDA_LEVANTADA", "Busqueda levantada"),
        new("PROPUESTA_ENVIADA", "Propuesta enviada"),
        new("REQUIERE_OPCIONES", "Requiere opciones"),
        new("NO_RESPONDE", "No responde"),
        new("SEGUIMIENTO", "Seguimiento"),
        new("DESCARTADO", "Descartado")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosProspeccion =
    [
        new("P", "Prospecto"),
        new("C", "Contactado"),
        new("R", "Reunion"),
        new("E", "Propuesta entregada"),
        new("S", "En seguimiento"),
        new("T", "Captado"),
        new("D", "Descartado")
    ];

    public static readonly IReadOnlyList<EnumOption> ResultadosPropuesta =
    [
        new("P", "Pendiente"),
        new("A", "Aceptada"),
        new("R", "Rechazada"),
        new("S", "Recontactar")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosVisita =
    [
        new("P", "Programada"),
        new("G", "Reprogramada"),
        new("C", "Cancelada"),
        new("N", "No realizada"),
        new("R", "Realizada")
    ];

    public static readonly IReadOnlyList<EnumOption> EstadosSolicitudAlquiler =
    [
        new("G", "Registrada"),
        new("E", "En revision"),
        new("O", "Observada"),
        new("A", "Aprobada"),
        new("R", "Rechazada"),
        new("D", "Desistida"),
        new("C", "Cerrada")
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

    public static string LabelFor(IEnumerable<EnumOption> options, string? code) =>
        options.FirstOrDefault(x => x.Code == code)?.Label ?? code ?? "";

    public static IReadOnlyList<EnumOption> ResultadosInteraccionPorContexto(string? contexto) =>
        (contexto ?? "OPORTUNIDAD").ToUpperInvariant() switch
        {
            "PROSPECCION" => ResultadosProspeccionInteraccion,
            "CAPTACION" => ResultadosCaptacionInteraccion,
            "CLIENTE" => ResultadosClienteInteraccion,
            _ => ResultadosOportunidadInteraccion,
        };

    public static string LabelResultadoInteraccion(string? contexto, string? code)
    {
        var label = LabelFor(ResultadosInteraccionPorContexto(contexto), code);
        return label == code ? LabelFor(ResultadosVisita, code) : label;
    }
}
