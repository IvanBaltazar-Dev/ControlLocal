namespace ControlLocal.Web.Models.Contratos;

// DTO del contrato de alquiler (cierre del trato). Espeja ContratoResponse del
// backend Java. La renta/plazo/comision se derivan de la solicitud aprobada.
public class ContratoAlquilerDto
{
    public long Id { get; set; }

    public long OportunidadId { get; set; }

    public string CodigoOportunidad { get; set; } = string.Empty;

    public string CodigoSolicitud { get; set; } = string.Empty;

    public string ClienteNombre { get; set; } = string.Empty;

    public string DireccionLocal { get; set; } = string.Empty;

    public string DistritoLocal { get; set; } = string.Empty;

    public string CodigoCaptacion { get; set; } = string.Empty;

    public string AgenteNombre { get; set; } = string.Empty;

    public long AgenteId { get; set; }

    public long PropietarioId { get; set; }

    public string PropietarioNombre { get; set; } = string.Empty;

    public decimal RentaMensual { get; set; }

    // Renta lista para mostrar (ej. "1 850").
    public string RentaMensualTexto { get; set; } = string.Empty;

    public string Moneda { get; set; } = "USD";

    public int PlazoMeses { get; set; }

    public decimal ComisionGenerada { get; set; }

    // Comisión lista para mostrar (ej. "1 110").
    public string ComisionGeneradaTexto { get; set; } = string.Empty;

    public string FechaInicioTexto { get; set; } = string.Empty;

    public string FechaFinTexto { get; set; } = string.Empty;

    public DateOnly? FechaCierre { get; set; }

    public string FechaCierreTexto { get; set; } = string.Empty;

    public string Estado { get; set; } = string.Empty;

    // Estado de la liquidación de comisión (PENDIENTE al cerrar el alquiler).
    public string ComisionEstado { get; set; } = string.Empty;

    // Incidencias registradas al formalizar el cierre (texto libre, opcional).
    public string Incidencias { get; set; } = string.Empty;

    // --- Liquidación de comisión (Etapa 2) ---
    // Id de la liquidación, para asignar monto del agente / registrar cobro.
    public long IdComision { get; set; }

    // Monto neto del agente y de la empresa: solo llegan poblados para broker/admin;
    // para el agente vienen null (no ve el reparto, solo el bruto).
    public decimal? MontoAgente { get; set; }

    public decimal? MontoEmpresa { get; set; }

    public string MontoAgenteTexto { get; set; } = string.Empty;

    public string MontoEmpresaTexto { get; set; } = string.Empty;

    // Forma de pago y fecha de cobro de la liquidación (visibles para todos los roles).
    public string FormaPago { get; set; } = string.Empty;

    // Fecha real del cobro de la comisión (para ordenar "por cobro"); null si aún no se cobró.
    public DateOnly? FechaCobro { get; set; }

    public string FechaCobroTexto { get; set; } = string.Empty;

    // True cuando el broker supervisor ya definió el monto del agente.
    public bool ComisionAsignada => MontoAgente.HasValue;
}

// Las condiciones del trato ya viven en la solicitud y la comisión se deriva en el backend;
// el agente solo captura la formalización del cierre: fecha de cierre, estado del contrato
// (FIRMADO o VIGENTE) e incidencias.
public class ContratoFormRequest
{
    public long SolicitudId { get; set; }

    // Por defecto el día de hoy; el backend la valida (no futura).
    public DateOnly? FechaCierre { get; set; }

    // Código del estado: "V" (Vigente) o "D" (Firmado). Por defecto Vigente.
    public string EstadoContrato { get; set; } = "V";

    // Observaciones/incidencias de la formalización (opcional).
    public string? Incidencias { get; set; }
}
