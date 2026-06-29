using ControlLocal.Web.Models.Agentes;
using ControlLocal.Web.Models.Asignaciones;
using ControlLocal.Web.Models.Brokers;
using ControlLocal.Web.Models.Captaciones;
using ControlLocal.Web.Models.Cartera;
using ControlLocal.Web.Models.Clientes;
using ControlLocal.Web.Models.Contratos;
using ControlLocal.Web.Models.Fichas;
using ControlLocal.Web.Models.Locales;
using ControlLocal.Web.Models.Oportunidades;
using ControlLocal.Web.Models.Propietarios;
using ControlLocal.Web.Models.Reportes;
using ControlLocal.Web.Models.Shared;
using ControlLocal.Web.Models.Solicitudes;
using ControlLocal.Web.Models.Tareas;
using ControlLocal.Web.Models.Visitas;
using ControlLocal.Web.Services;

namespace ControlLocal.Web.Data;

public sealed record PageResult<T>(IReadOnlyList<T> Items, long TotalRecords, int Page, int PageSize);

// Contratos de servicios de dominio. Las pantallas dependen solo de estas
// interfaces; la implementación activa (mock en memoria o cliente REST de
// Services/Api) se decide por configuración en Program.cs.

public interface IBrokerService
{
    Task<IReadOnlyList<BrokerDto>> AllAsync(CancellationToken ct = default);
    Task<BrokerDto?> ByCodigoAsync(string codigoBroker, CancellationToken ct = default);
    Task<IReadOnlyList<BrokerDto>> RefrescarAsync(CancellationToken ct = default);
    Task<BrokerDto> AgregarAsync(BrokerDto broker, CancellationToken ct = default);
    Task<BrokerDto> ActualizarAsync(BrokerDto broker, CancellationToken ct = default);
}

public interface IAgenteService
{
    Task<IReadOnlyList<AgenteDto>> AllAsync(CancellationToken ct = default);
    Task<IReadOnlyList<AgenteDto>> RefrescarAsync(CancellationToken ct = default);
    Task<AgenteDto?> ByIdAsync(long id, CancellationToken ct = default);
    Task<AgenteDto> AgregarAsync(AgenteDto agente, CancellationToken ct = default);
    Task<AgenteDto> ActualizarAsync(AgenteDto agente, CancellationToken ct = default);
    Task<AgenteDto> DesactivarAsync(long id, CancellationToken ct = default);
}

public interface IPropietarioService
{
    Task<IReadOnlyList<PropietarioDto>> AllAsync(CancellationToken ct = default);
    Task<PropietarioDto?> ByIdAsync(long id, CancellationToken ct = default);
    // Recarga fresca desde el backend e invalida la cache del circuito.
    Task<IReadOnlyList<PropietarioDto>> RefrescarAsync(CancellationToken ct = default);
    Task<PropietarioDto> AgregarAsync(PropietarioDto propietario, CancellationToken ct = default);
    Task<PropietarioDto> ActualizarAsync(PropietarioDto propietario, CancellationToken ct = default);
}

public interface IClienteService
{
    Task<IReadOnlyList<ClienteInteresadoDto>> AllAsync(CancellationToken ct = default);
    Task<ClienteInteresadoDto?> ByIdAsync(long id, CancellationToken ct = default);
    // GET fresco + mapeo (no cache-first), para detalle/edición que necesitan el dato del backend.
    Task<ClienteInteresadoDto?> ObtenerAsync(long id, CancellationToken ct = default);
    // Recarga fresca desde el backend e invalida la cache del circuito.
    Task<IReadOnlyList<ClienteInteresadoDto>> RefrescarAsync(CancellationToken ct = default);
    Task<ClienteInteresadoDto> AgregarAsync(ClienteInteresadoDto cliente, CancellationToken ct = default);
    Task<ClienteInteresadoDto> ActualizarAsync(ClienteInteresadoDto cliente, CancellationToken ct = default);
}

public interface IFichaComercialService
{
    Task<FichaComercialDto?> ClienteAsync(long id, CancellationToken ct = default);

    Task<FichaSectionDto> ClienteSectionAsync(long id, string section, int page, int pageSize = 8,
        CancellationToken ct = default);

    Task<FichaComercialDto?> PropietarioAsync(long id, CancellationToken ct = default);

    Task<FichaSectionDto> PropietarioSectionAsync(long id, string section, int page, int pageSize = 8,
        CancellationToken ct = default);
}

// Recomendacion de cartera (Etapa 8): cruza demanda (requerimientos) y oferta (captaciones) en ambos sentidos.
public interface ICoincidenciaCarteraService
{
    Task<CoincidenciasDto> PropiedadesParaClienteAsync(long idCliente, int page = 1, int pageSize = 6,
        CancellationToken ct = default);

    Task<CoincidenciasDto> ClientesParaCaptacionAsync(string idOrCodigo, int page = 1, int pageSize = 6,
        CancellationToken ct = default);

    Task<CoincidenciasDto> ClientesParaProspeccionAsync(long idProspeccion, int page = 1, int pageSize = 6,
        CancellationToken ct = default);
}

// Reporte periódico al propietario (Etapa 8). Vive en el expediente de la captación; el agente
// dueño lo registra y reinicia su tarea automática; broker/admin lo consultan en su alcance.
public interface IReportePropietarioService
{
    Task<IReadOnlyList<ReportePropietarioDto>> ListarPorCaptacionAsync(long idCaptacion, CancellationToken ct = default);

    Task<ReportePropietarioDto> CrearAsync(long idCaptacion, ReportePropietarioDto reporte, CancellationToken ct = default);

    // Valores derivados del periodo (consultas/visitas/objeciones) para previsualizar antes de registrar.
    Task<ReportePreviewDto> PreviewAsync(long idCaptacion, DateTime? desde, DateTime? hasta, CancellationToken ct = default);
}

// Requerimientos de cliente (perfil de busqueda). Crear/actualizar vincula al cliente y entra al matching.
public interface IRequerimientoService
{
    Task<IReadOnlyList<RequerimientoDto>> ListarPorClienteAsync(long idCliente, CancellationToken ct = default);

    Task<RequerimientoDto> CrearAsync(RequerimientoDto requerimiento, CancellationToken ct = default);

    Task<RequerimientoDto> ActualizarAsync(RequerimientoDto requerimiento, CancellationToken ct = default);

    Task<RequerimientoDto> CambiarEstadoAsync(long id, string estado, CancellationToken ct = default);
}

public interface ILocalService
{
    Task<IReadOnlyList<LocalComercialDto>> AllAsync(CancellationToken ct = default);
    Task<LocalComercialDto?> ByIdAsync(long id, CancellationToken ct = default);
    // Igual que ByIdAsync (cache-first + GET de respaldo); se mantiene por compatibilidad de llamadas.
    Task<LocalComercialDto?> ObtenerAsync(long id, CancellationToken ct = default);
    Task<IReadOnlyList<LocalComercialDto>> RefrescarAsync(CancellationToken ct = default);
    Task<LocalComercialDto> AgregarAsync(LocalComercialDto local, CancellationToken ct = default);
    Task<LocalComercialDto> ActualizarAsync(LocalComercialDto local, CancellationToken ct = default);
}

// Historico de precios de un local (tabla precio_local). Solo lectura + alta de hito.
public interface IPrecioLocalService
{
    Task<IReadOnlyList<PrecioLocalDto>> ListarPorLocalAsync(long idLocal, CancellationToken ct = default);
    Task<PrecioLocalDto> RegistrarAsync(long idLocal, string hito, string moneda, decimal monto,
        DateOnly fecha, CancellationToken ct = default);
}

// Publicaciones de un local (tabla publicacion). Solo lectura.
public interface IPublicacionService
{
    Task<IReadOnlyList<PublicacionDto>> ListarPorLocalAsync(long idLocal, CancellationToken ct = default);

    // Etapa 7: gestion de publicacion desde el detalle.
    Task<PublicacionDto> CrearAsync(long idLocal, PublicacionDto publicacion, CancellationToken ct = default);

    Task<PublicacionDto> ActualizarAsync(long idLocal, PublicacionDto publicacion, CancellationToken ct = default);

    Task<PublicacionDto> CambiarEstadoAsync(long idLocal, long idPublicacion, string estado, CancellationToken ct = default);
}

// Prospección (pre-captación): el agente persigue al propietario hasta captar el
// local. Espejo, del lado de la oferta, de la oportunidad. El recontacto es automático:
// cada acción reinicia el reloj a 7 días; desde el día 8 sin acción la prospección queda vencida.
public interface IProspeccionService
{
    Task<IReadOnlyList<ProspeccionDto>> AllAsync(CancellationToken ct = default);
    Task<ProspeccionDto?> ByIdAsync(long id, CancellationToken ct = default);
    // GET fresco + mapeo (no cache-first), para detalle/edicion que necesitan el dato del backend.
    Task<ProspeccionDto?> ObtenerAsync(long id, CancellationToken ct = default);
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<ProspeccionDto>> RefrescarAsync(CancellationToken ct = default);
    Task<PageResult<ProspeccionDto>> ListarPaginaAsync(
        int pagina, int tamano = 8, string? estado = null, string? distrito = null,
        string? query = null, CancellationToken ct = default, long? idCaptacion = null, long? idLocal = null,
        long? idAgente = null, long? idBrokerSupervisor = null);
    Task<PageResult<ProspeccionDto>> ListarRecontactarPaginaAsync(
        int pagina, int tamano = 8, int diasAviso = 7, CancellationToken ct = default);
    Task<long> ContarAsync(string? estado = null, string? distrito = null, string? query = null, CancellationToken ct = default);
    Task<long> ContarRecontactarAsync(int diasAviso = 7, CancellationToken ct = default);
    Task<ProspeccionDto> ContactarAsync(long id, CancellationToken ct = default);
    Task<ProspeccionDto> RegistrarReunionAsync(long id, CancellationToken ct = default);
    Task<ProspeccionDto> EntregarPropuestaAsync(long id, CancellationToken ct = default);
    // Acción de seguimiento del propietario (un clic): reinicia el reloj de recontacto.
    Task<ProspeccionDto> RegistrarSeguimientoAsync(long id, CancellationToken ct = default);
    Task<ProspeccionDto> RechazarAsync(long id, string motivo, CancellationToken ct = default);
    Task<ProspeccionDto> DescartarAsync(long id, string motivo, CancellationToken ct = default);
    // El propietario acepta: marca Captado y simula la captación creada.
    Task<ProspeccionDto> CaptarAsync(long id, decimal comisionPactada, CancellationToken ct = default);
    Task<ProspeccionDto> MarcarCaptadoAsync(long id, string codigoCaptacion, CancellationToken ct = default);
}

// Bandeja "Acciones Pendientes" del agente (Etapa 5). Sin alta manual: las tareas se
// derivan/reconcilian en backend; el agente solo resuelve (en su pantalla) o cancela.
public interface ITareaService
{
    Task<IReadOnlyList<TareaDto>> BandejaAsync(CancellationToken ct = default);
    Task<PageResult<TareaDto>> BandejaPaginaAsync(int pagina = 1, int tamano = 5, CancellationToken ct = default);
    Task CancelarAsync(long idTarea, CancellationToken ct = default);
}

public interface ICaptacionService
{
    Task<IReadOnlyList<CaptacionDto>> AllAsync(CancellationToken ct = default);
    Task<CaptacionDto?> ByCodigoAsync(string codigo, CancellationToken ct = default);
    Task<CaptacionDto> AgregarAsync(CaptacionDto captacion, CancellationToken ct = default);
    Task<CaptacionDto> ActualizarAsync(CaptacionDto captacion, CancellationToken ct = default);
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<CaptacionDto>> RefrescarAsync(CancellationToken ct = default);
    Task<PageResult<CaptacionDto>> ListarReasignablesAsync(
        int pagina = 1, int tamano = 8, string? query = null, CancellationToken ct = default);
    Task<IReadOnlyList<BandejaCaptacionDto>> RefrescarBandejaAsync(CancellationToken ct = default);
    Task<CaptacionDto?> ObtenerPorCodigoAsync(string codigo, CancellationToken ct = default);
    Task<CaptacionDto?> ObtenerPorIdAsync(long id, CancellationToken ct = default);
    Task ResolverBandejaAsync(string codigo, string decision, string? observacion, CancellationToken ct = default);
    Task ReasignarBandejaAsync(string codigo, long idNuevoAgente, string motivo, CancellationToken ct = default);
    Task CerrarAsync(long id, string motivo, CancellationToken ct = default);
}

public interface ISolicitudService
{
    Task<IReadOnlyList<SolicitudAlquilerDto>> AllAsync(CancellationToken ct = default);
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<SolicitudAlquilerDto>> RefrescarAsync(CancellationToken ct = default);
    Task<SolicitudAlquilerDto?> ObtenerAsync(string idOCodigo, CancellationToken ct = default);
    Task<PageResult<SolicitudAlquilerDto>> ListarPaginaAsync(int pagina = 1, int tamano = 20, CancellationToken ct = default);
    Task<IReadOnlyList<SolicitudAlquilerDto>> ListarPorCaptacionAsync(long captacionId, CancellationToken ct = default);
    Task<IReadOnlyList<SolicitudAlquilerDto>> ListarPorOportunidadAsync(long oportunidadId, CancellationToken ct = default);
    Task<SolicitudAlquilerDto?> ByCodigoAsync(string codigo, CancellationToken ct = default);
    Task<SolicitudAlquilerDto> AgregarAsync(SolicitudFormRequest request, CancellationToken ct = default);
    // Reenvio asincrono real (no bloquea el circuito de Blazor Server).
    Task<SolicitudAlquilerDto> ReenviarAEvaluacionAsync(string codigoSolicitud, CancellationToken ct = default);
    Task<EvaluacionSolicitudDto> EvaluarAsync(
        string codigoSolicitud,
        EvaluacionSolicitudDto evaluacion,
        CancellationToken ct = default);
    // Historial de evaluaciones de una solicitud (trazabilidad), accesible al agente dueño y al broker.
    Task<IReadOnlyList<EvaluacionSolicitudDto>> ListarEvaluacionesAsync(long idSolicitud, CancellationToken ct = default);
}

// Contrato de alquiler: el cierre del trato registrado como un "hilo" dentro de la
// solicitud aprobada (espeja /contratos del backend Java). Listar alimenta la
// seccion de propiedades alquiladas; registrar crea el contrato y cierra la operacion.
public interface IContratoService
{
    Task<IReadOnlyList<ContratoAlquilerDto>> AllAsync(CancellationToken ct = default);
    Task<IReadOnlyList<ContratoAlquilerDto>> RefrescarAsync(CancellationToken ct = default);
    // Contrato ya registrado para una oportunidad, desde la cache del circuito (null si aun no se ha alquilado).
    Task<ContratoAlquilerDto?> ByOportunidadAsync(long oportunidadId, CancellationToken ct = default);
    // Igual que ByOportunidadAsync pero consultado fresco al backend (sin cache-first).
    Task<ContratoAlquilerDto?> ObtenerPorOportunidadAsync(long oportunidadId, CancellationToken ct = default);
    // Crea el contrato a partir de la solicitud aprobada y cierra el trato.
    Task<ContratoAlquilerDto> RegistrarAsync(ContratoFormRequest request, CancellationToken ct = default);
    // Etapa 2: el broker supervisor define el monto real del agente (la empresa se calcula sola).
    Task<ContratoAlquilerDto> AsignarComisionAsync(long idContrato, decimal montoAgente, CancellationToken ct = default);
    // Etapa 2: el broker administrador registra el cobro (estado COBRADA/ANULADA, fecha y forma de pago).
    Task<ContratoAlquilerDto> RegistrarCobroAsync(long idContrato, string estado, DateOnly? fechaCobro, string? formaPago, CancellationToken ct = default);
}

// Documentos de una solicitud (espeja /solicitudes/{id}/documentos del backend Java):
// listar los entregados, subir el archivo real al almacen (S3 o disco, lo gestiona el
// backend) y consultar el estado de conectividad del almacen.
public interface IDocumentoSolicitudService
{
    Task<IReadOnlyList<DocumentoSolicitudDto>> ListarAsync(long idSolicitud, CancellationToken ct = default);

    // Envía los bytes del archivo al backend, que lo guarda en el almacen y registra
    // sus metadatos. Devuelve el documento creado (con su clave en RutaArchivo).
    Task<DocumentoSolicitudDto> SubirAsync(
        long idSolicitud, string tipoDocumento, string nombreArchivo, byte[] contenido,
        CancellationToken ct = default);

    // Revision del broker sobre un documento concreto: "C" valida, "O" observa (con nota).
    Task<DocumentoSolicitudDto> RevisarAsync(
        long idSolicitud, long idDocumento, string resultado, string? observaciones,
        CancellationToken ct = default);

    // El broker deja conformes en bloque los documentos cargados aún pendientes de la solicitud
    // ("Validar todos los documentos" en la evaluación). Devuelve la lista de documentos resultante.
    Task<IReadOnlyList<DocumentoSolicitudDto>> ConformarTodosAsync(
        long idSolicitud, CancellationToken ct = default);

    Task<EstadoAlmacen> EstadoAlmacenAsync(CancellationToken ct = default);
}

public interface IInteraccionService
{
    Task<IReadOnlyList<InteraccionComercialDto>> AllAsync(CancellationToken ct = default);
    Task<InteraccionComercialDto?> ByIdAsync(long id, CancellationToken ct = default);
    Task<PageResult<InteraccionComercialDto>> ListarPaginaAsync(
        int pagina,
        int tamano = 8,
        string? grupo = null,
        string? resultado = null,
        string? canal = null,
        string? query = null,
        CancellationToken ct = default);
    // Variante async real (no bloquea el circuito de Blazor Server).
    Task<InteraccionComercialDto> AgregarAsync(InteraccionFormRequest request, CancellationToken ct = default);
    Task<IReadOnlyList<InteraccionComercialDto>> ListarPorOportunidadAsync(long oportunidadId, CancellationToken ct = default);
    Task<IReadOnlyList<InteraccionComercialDto>> ListarPorProspeccionAsync(long prospeccionId, CancellationToken ct = default);
    Task<IReadOnlyList<InteraccionComercialDto>> ListarPorCaptacionAsync(long captacionId, CancellationToken ct = default);
    Task<IReadOnlyList<InteraccionComercialDto>> ListarPorClienteAsync(long clienteId, CancellationToken ct = default);
    Task<InteraccionComercialDto> ActualizarAsync(long id, string? resultado = null, string? observaciones = null, CancellationToken ct = default);
}

// Espeja VisitaBusinessLogic del backend Java: listado + transiciones de estado
// de la cita (programar / reprogramar / cancelar / registrar resultado).
// Oportunidad comercial = cliente interesado + captación (puente N:M del modelo).
// Una misma captación puede tener varias oportunidades (varios clientes interesados).
public interface IOportunidadService
{
    Task<IReadOnlyList<OportunidadComercialDto>> AllAsync(CancellationToken ct = default);
    Task<OportunidadComercialDto?> ByIdAsync(long id, CancellationToken ct = default);
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<OportunidadComercialDto>> RefrescarAsync(CancellationToken ct = default);
    Task<IReadOnlyList<OportunidadComercialDto>> ListarPorCaptacionAsync(long captacionId, CancellationToken ct = default);
    Task<IReadOnlyList<OportunidadComercialDto>> ListarPorClienteAsync(long clienteId, CancellationToken ct = default);
    Task<OportunidadComercialDto> CrearAsync(OportunidadFormRequest request, CancellationToken ct = default);
    Task<OportunidadComercialDto> CerrarNoContinuaAsync(
        long id,
        string razon,
        string? observaciones,
        CancellationToken ct = default);
    Task<OportunidadComercialDto> CerrarExitosaAsync(long id, CancellationToken ct = default);
}

public interface IVisitaService
{
    Task<IReadOnlyList<VisitaDto>> AllAsync(CancellationToken ct = default);
    Task<VisitaDto?> ByIdAsync(long id, CancellationToken ct = default);
    // Recarga fresca desde el backend (no bloqueante) e invalida la cache de sesion.
    Task<IReadOnlyList<VisitaDto>> RefrescarAsync(CancellationToken ct = default);
    // POST /visitas — programa una nueva visita (estado inicial PROGRAMADA).
    Task<VisitaDto> ProgramarAsync(VisitaFormRequest request, CancellationToken ct = default);
    // PATCH /visitas/{id}/reprogramar — mueve fecha/hora (estado REPROGRAMADA).
    Task<VisitaDto> ReprogramarAsync(long id, string fechaTexto, string horaTexto, CancellationToken ct = default);
    // PATCH /visitas/{id}/cancelar — cancela con motivo (estado CANCELADA).
    Task<VisitaDto> CancelarAsync(long id, string motivo, CancellationToken ct = default);
    // PATCH /visitas/{id}/realizar — confirma que la visita ocurrio.
    Task<VisitaDto> MarcarRealizadaAsync(long id, CancellationToken ct = default);
    // PATCH /visitas/{id}/no-realizada — confirma que la cita no llego a ocurrir.
    Task<VisitaDto> MarcarNoRealizadaAsync(long id, string motivo, CancellationToken ct = default);
    // PATCH /visitas/{id}/resultado — registra el desenlace de una visita REALIZADA.
    // Si el resultado es de no continuidad (N/D), razonNoContinuidad es obligatorio:
    // registra el motivo ligado a la visita y cierra la oportunidad.
    Task<VisitaDto> RegistrarResultadoAsync(long id, VisitaResultadoRequest request, CancellationToken ct = default);
}

public interface IAssignmentService
{
    Task<IReadOnlyList<AssignAgentDto>> AgentsAsync(CancellationToken ct = default);
    Task<IReadOnlyList<AssignBrokerDto>> BrokersAsync(CancellationToken ct = default);
    // Historial de reasignaciones de agentes entre brokers (relación BrokerAgente).
    Task<IReadOnlyList<BrokerAgenteDto>> HistorialAsync(CancellationToken ct = default);
    // Asigna el agente al broker destino, cierra la supervisión anterior y registra el historial.
    Task<BrokerAgenteDto> ReasignarAgenteAsync(
        string agenteId, string brokerId, string motivo, CancellationToken ct = default);
}

// Reasignación de captaciones a otro agente del equipo. Espeja el flujo de
// negocio reasignarCaptacion(...) del backend Java (registra historial).
public interface IReasignacionCaptacionService
{
    // Historial de reasignaciones registradas, más reciente primero. La reasignación en sí la
    // ejecuta ReasignarCaptaciones vía ICaptacionService.ReasignarBandejaAsync.
    Task<IReadOnlyList<ReasignacionCaptacionDto>> HistorialAsync(CancellationToken ct = default);
}
