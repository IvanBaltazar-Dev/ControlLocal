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

    public string FechaCierreTexto { get; set; } = string.Empty;

    public string Estado { get; set; } = string.Empty;

    // Estado de la liquidación de comisión (PENDIENTE al cerrar el alquiler).
    public string ComisionEstado { get; set; } = string.Empty;
}

// El cierre solo necesita la solicitud aprobada: las condiciones ya viven en la solicitud
// y la comisión se deriva en el backend.
public class ContratoFormRequest
{
    public long SolicitudId { get; set; }
}
