namespace ControlLocal.Web.Models.Oportunidades;

// DTO de una interacción comercial con el cliente interesado.
// Alineado al backend Java (InteraccionComercial: fechaHora, canalContacto,
// resultado, observaciones, oportunidad, cliente, captacion, agente).
public class InteraccionComercialDto
{
    public long Id { get; set; }

    public long OportunidadId { get; set; }

    // Fecha y hora listas para mostrar.
    public string FechaHoraTexto { get; set; } = string.Empty;

    public string CanalContacto { get; set; } = string.Empty;

    public string Resultado { get; set; } = string.Empty;

    public string Observaciones { get; set; } = string.Empty;

    public string TranscripcionNota { get; set; } = string.Empty;

    public string ClienteNombre { get; set; } = string.Empty;

    public string CaptacionCodigo { get; set; } = string.Empty;

    public string NombreAgenteResponsable { get; set; } = string.Empty;
}
