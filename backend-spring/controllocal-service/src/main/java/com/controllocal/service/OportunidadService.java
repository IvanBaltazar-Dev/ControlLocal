package com.controllocal.service;

import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.soporte.Procedencia;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** CAT-E21, RF040/042/050 y AC101/104/115. La oportunidad no confirma un cierre contractual. */
public interface OportunidadService {
    record EvidenciaInteres(String descripcion, String referencia) { }
    /** Cliente es opcional; RF003 conserva el contacto legítimo sin inventar un rol. */
    record DatosOportunidad(String codigoOportunidad, long idEncargo, Long versionEncargo,
            InteresadosDeDemanda.Referencia interesado, String observaciones, Long idPublicacionOrigen,
            Long idDemanda, Long versionDemanda, String origen, String referenciaConsulta,
            String referenciaCoincidencia, EvidenciaInteres evidenciaInteres) { }
    /** Referencias del vínculo real, no datos del expediente de la persona. */
    record InteresadoReferencia(long idPersona, Long idCliente) { }
    record NoContinuidad(long idOportunidad, Long versionEsperada, String razon,
            String observaciones, LocalDate fechaHecho) { }
    record FichaOportunidad(Long id, String codigoOportunidad, Long idCliente, String clienteNombre,
            Long idCaptacion, String codigoCaptacion, String direccionLocal, String distritoLocal,
            Long idAgente, String agenteNombre, String estado, LocalDateTime fechaRegistro,
            String motivoCierre, String observaciones, LocalDateTime fechaCierre,
            LocalDateTime fechaActualizacion, Long idPublicacionOrigen,
            List<OfertaService.FichaOferta> ofertas, OfertaService.CapacidadesDeLaMesa capacidades,
            long version, InteresadoReferencia interesado, Long idDemanda, Long versionDemanda,
            String origen, String referenciaConsulta, String referenciaCoincidencia,
            LocalDate fechaNoContinuidad, Procedencia procedencia) { }
    record ResumenOportunidades(long total, long abiertas, long conSolicitud, long noContinuan,
            long exitosas, long noFavorables) { }
    enum Seccion { RESUMEN, ACTIVIDAD, VISITAS, NEGOCIACION, HISTORIA }
    record Antecedente(long version, Instant registradoEn, String operacion, String motivo,
            FichaOportunidad datos) { }
    record ConsultaOportunidad(FichaOportunidad oportunidad, Seccion seccion, Instant corte,
            List<Antecedente> antecedentes, List<InteraccionService.FichaInteraccion> interacciones,
            List<VisitaService.FichaVisita> visitas,OfertaService.AcuerdoAceptado acuerdo) {
        public ConsultaOportunidad {
            antecedentes=antecedentes==null?null:List.copyOf(antecedentes);
            interacciones=interacciones==null?null:List.copyOf(interacciones);
            visitas=visitas==null?null:List.copyOf(visitas);
        }
    }

    Pagina<FichaOportunidad> listar(int pagina,int tamano,Long idCaptacion,Long idCliente,
            String estado,String query,Actor actor);
    ResumenOportunidades resumen(Long idCaptacion,Long idCliente,String query,Actor actor);
    FichaOportunidad obtener(long id,Actor actor);
    ConsultaOportunidad consultar(long id,Seccion seccion,Instant corte,Actor actor);

    DatosOportunidad validarApertura(DatosOportunidad datos,Actor actor);
    /** Propuesta privada: autoriza Encargo e interesado originales; no persiste ni exige CAS de escritura. */
    void exigirLecturaApertura(DatosOportunidad datos,Actor actor);
    /** Efector de la máquina de revisión exacta; se incorpora a su transacción. */
    FichaOportunidad registrar(DatosOportunidad datos,Actor actor,ContextoSesion sesion,
            String clave,Procedencia procedencia);
    NoContinuidad validarNoContinuidad(NoContinuidad datos,Actor actor);
    FichaOportunidad noContinuidad(NoContinuidad datos,Actor actor,ContextoSesion sesion,
            String clave,Procedencia procedencia);
    Optional<FichaOportunidad> consultarResultadoApertura(String clave,Actor actor);
    Optional<FichaOportunidad> consultarResultadoNoContinuidad(String clave,Actor actor);
}
