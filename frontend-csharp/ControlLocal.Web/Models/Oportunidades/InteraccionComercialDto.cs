namespace ControlLocal.Web.Models.Oportunidades;

// DTO de una interacción comercial con el cliente interesado.
// Alineado al backend Java (InteraccionComercial: fechaHora, canalContacto,
// resultado, observaciones, oportunidad, cliente, captacion, agente).
public class InteraccionComercialDto
{
    public long Id { get; set; }

    public string Contexto { get; set; } = "OPORTUNIDAD";

    public long OportunidadId { get; set; }

    public long ProspeccionId { get; set; }

    public long CaptacionId { get; set; }

    public long ClienteId { get; set; }

    public long PropietarioId { get; set; }

    public string CodigoProspeccion { get; set; } = string.Empty;

    // Fecha y hora listas para mostrar.
    public string FechaHoraTexto { get; set; } = string.Empty;

    public string CanalContacto { get; set; } = string.Empty;

    public string Resultado { get; set; } = string.Empty;

    public string Observaciones { get; set; } = string.Empty;

    public string TranscripcionNota { get; set; } = string.Empty;

    public string ClienteNombre { get; set; } = string.Empty;

    public string PropietarioNombre { get; set; } = string.Empty;

    public string PersonaTipo { get; set; } = string.Empty;

    public string PersonaNombre { get; set; } = string.Empty;

    public string CaptacionCodigo { get; set; } = string.Empty;

    public string NombreAgenteResponsable { get; set; } = string.Empty;

    // Nombre por contexto (Etapa 6): el propietario se sigue en prospección (antes de captar)
    // y en captación (ya captado); el cliente, en oportunidad y en seguimiento de relación.
    public string ContextoTexto => Contexto switch
    {
        "PROSPECCION" => "Seguimiento del propietario",
        "CAPTACION" => "Seguimiento del propietario",
        "CLIENTE" => "Seguimiento de relación",
        _ => "Interacción con cliente",
    };

    // Lado de la relación, para separar la pantalla central sin mezclar cliente con propietario.
    public string GrupoContexto => Contexto switch
    {
        "PROSPECCION" or "CAPTACION" => "propietario",
        _ => "cliente",
    };

    public string PersonaTipoMostrar =>
        !string.IsNullOrWhiteSpace(PersonaTipo) ? PersonaTipo : GrupoContexto == "propietario" ? "Propietario" : "Cliente";

    public string PersonaNombreMostrar =>
        !string.IsNullOrWhiteSpace(PersonaNombre) ? PersonaNombre
        : GrupoContexto == "propietario" ? PropietarioNombre : ClienteNombre;
}
