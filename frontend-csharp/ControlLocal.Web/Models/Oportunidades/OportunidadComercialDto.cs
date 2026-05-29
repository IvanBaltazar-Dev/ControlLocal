namespace ControlLocal.Web.Models.Oportunidades;

// DTO de la oportunidad comercial. Alineado al backend Java (OportunidadComercial:
// codigoOportunidad, clienteInteresado, captacion, agenteResponsable, estado,
// fechaRegistro, motivoCierre, fechaCierre).
public class OportunidadComercialDto
{
    public long Id { get; set; }

    public string CodigoOportunidad { get; set; } = string.Empty;

    public string ClienteNombre { get; set; } = string.Empty;

    public long CaptacionId { get; set; }

    public string CaptacionCodigo { get; set; } = string.Empty;

    public string DireccionLocal { get; set; } = string.Empty;

    public string NombreAgenteResponsable { get; set; } = string.Empty;

    public string Estado { get; set; } = "Abierta";

    // Fecha de registro lista para mostrar.
    public string FechaRegistroTexto { get; set; } = string.Empty;

    public string MotivoCierre { get; set; } = string.Empty;

    public string Observaciones { get; set; } = string.Empty;

    // Fecha de cierre lista para mostrar (vacía si sigue abierta).
    public string FechaCierreTexto { get; set; } = string.Empty;
}
