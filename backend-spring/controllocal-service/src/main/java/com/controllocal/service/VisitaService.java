package com.controllocal.service;

import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** CAT-E21/RF043–045. Programar, cancelar y declarar ocurrencia conservan hechos separados. */
public interface VisitaService {
    /** Snapshot declarado; no resuelve una Persona ajena ni acredita representación. */
    record ParticipanteConocido(String nombreDeclarado,String referenciaDeclarada) { }
    record DatosVisita(long idOportunidad,Long versionContexto,LocalDate fechaVisita,LocalTime horaVisita,
            String observaciones,List<ParticipanteConocido> participantes,String lugarDeclarado) {
        public DatosVisita { participantes=participantes==null?List.of():List.copyOf(participantes); }
    }
    record Reprogramacion(long idVisita,Long versionEsperada,LocalDate fechaVisita,
            LocalTime horaVisita,String motivo) { }
    record Cancelacion(long idVisita,Long versionEsperada,String motivo,LocalDate fechaHecho) { }
    enum Ocurrencia { REALIZADA, NO_REALIZADA }
    record DesenlaceVisita(Long versionEsperada,Ocurrencia ocurrencia,LocalDate fechaHecho,String resultado,
            String observaciones,Integer nivelInteres,String objecionPrincipal,String opinionPrecio,
            String proximaAccion,String motivoRectificacion) { }
    record FiltrosVisita(Long idOportunidad,String estado,String distrito,String query,int pagina,int tamano) { }
    record ConsultaAgenda(LocalDate desde,LocalDate hasta,Long idOportunidad,String estado,
            int pagina,int tamano) { }
    record FichaVisita(Long id,Long idOportunidad,String codigoOportunidad,LocalDate fechaVisita,
            LocalTime horaVisita,String observaciones,String estado,String resultado,Long idCliente,
            String clienteNombre,Long idCaptacion,String codigoCaptacion,String direccionLocal,
            String distritoLocal,Long idAgente,String agenteNombre,Integer nivelInteres,
            String objecionPrincipal,String opinionPrecio,String proximaAccion,long version,
            List<ParticipanteConocido> participantes,String lugarDeclarado,LocalDate fechaHecho,
            Instant registradoEn,Procedencia procedencia) {
        public FichaVisita { participantes=participantes==null?List.of():List.copyOf(participantes); }
    }
    record ResumenVisitas(long total,long programadas,long reprogramadas,long realizadas,
            long noRealizadas,long canceladas,List<String> distritos) { }
    record Antecedente(long version,Instant registradoEn,String operacion,String motivo,FichaVisita datos) { }

    Pagina<FichaVisita> listar(FiltrosVisita filtros,Actor actor);
    ResumenVisitas resumen(FiltrosVisita filtros,Actor actor);
    Pagina<FichaVisita> proximas(int tamano,Actor actor);
    Pagina<FichaVisita> mes(int anio,int mes,Actor actor);
    Pagina<FichaVisita> agenda(ConsultaAgenda consulta,Actor actor);
    FichaVisita obtener(long id,Actor actor);
    /** Reautoriza el snapshot histórico exacto usado por un recibo AUTO, nunca lo sustituye por el actual. */
    FichaVisita obtenerVersion(long id,long version,Actor actor);
    List<FichaVisita> porOportunidad(long id,Instant corte,Actor actor);
    List<Antecedente> historia(long id,Actor actor);

    DatosVisita validarProgramacion(DatosVisita datos,Actor actor);
    FichaVisita programar(DatosVisita datos,Actor actor,ContextoSesion sesion,Procedencia procedencia);
    Reprogramacion validarReprogramacion(Reprogramacion datos,Actor actor);
    FichaVisita reprogramar(Reprogramacion datos,Actor actor,ContextoSesion sesion,Procedencia procedencia);
    Cancelacion validarCancelacion(Cancelacion datos,Actor actor);
    FichaVisita cancelar(Cancelacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    DesenlaceVisita validarResultado(long id,DesenlaceVisita datos,Actor actor);
    FichaVisita registrarResultado(long id,DesenlaceVisita datos,Actor actor,ContextoSesion sesion,Procedencia procedencia);
    Optional<FichaVisita> consultarResultadoCancelacion(String clave,Actor actor);
}
