package com.controllocal.service;

import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.CatalogoInteracciones;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.soporte.ValorLogico;
import java.time.*;
import java.util.*;

/** RF041/AC102–103: comunicación real declarada, vínculos y proyecciones autorizadas. */
public interface InteraccionService {
    enum TipoContexto { PROPIEDAD,CONTACTO,PROSPECCION,ENCARGO,OPORTUNIDAD,CLIENTE }
    enum TipoContacto { PERSONA,CLIENTE,PROPIETARIO,DEMANDA_ORIGEN,CONTACTO_NUEVO,DESTINATARIO,INTERLOCUTOR }
    enum OrigenActuacion { ACUERDO_EXTERNO,INTENCION_PROFESIONAL,SUGERENCIA_SISTEMA }
    enum FinalidadLectura { FICHA_PROPIEDAD,FICHA_CONTACTO,ACTIVIDAD_OPORTUNIDAD,INDICADORES,REPORTE_PROPIETARIO,HISTORIA }
    enum FinalidadContacto { PROSPECCION_COMERCIAL,COORDINACION_ACCESO,INFORMACION_CONTACTO,OTRA_GESTION }
    enum FuncionContacto { GESTION_COMERCIAL,COORDINACION_ACCESO,INFORMACION_CONTACTO,POR_PRECISAR }
    /** Función de esta conversación, no titularidad, permiso de firma ni rol interno. */
    record GestionContacto(Set<FinalidadContacto> finalidades,Set<FuncionContacto> funcionesInterlocutor,
                           String fuenteFunciones,boolean continuarConInterlocutor,ReferenciaVersion respondeAIntento) {
        public GestionContacto {
            finalidades=finalidades==null?Set.of():Set.copyOf(finalidades);
            funcionesInterlocutor=funcionesInterlocutor==null?Set.of():Set.copyOf(funcionesInterlocutor);
        }
    }
    record DatoContactoProspeccion(String estado,String causa,boolean intentoSaliente,ReferenciaVersion respondeAIntento,Long versionHecho) {
        public DatoContactoProspeccion(String estado,String causa,boolean intentoSaliente,ReferenciaVersion respondeAIntento){this(estado,causa,intentoSaliente,respondeAIntento,null);}
    }
    record ReferenciaContactoContextual(TipoContacto tipo,Long referencia,Long versionEsperada,InteresadosDeDemanda.DatosContacto datos) { }
    record VinculoActividad(TipoContexto tipo,Long referencia,Long versionEsperada,ReferenciaContactoContextual contacto) { }
    record ParticipanteDeclarado(ReferenciaContactoContextual contacto,String tipoDeclarado,String nombre,String telefono,String referenciaDeclarada) { }
    record MomentoHecho(LocalDate fecha,LocalTime hora,ZoneOffset offsetConocido) { }
    record EventoDeclarado(String referencia,EventoComunicacion evento,MomentoHecho momento) { }
    record ConfirmacionContextual(String estado,String referenciaDato,Long versionDato,String evidencia) { }
    record Declaracion(String texto,String conceptoDeclarado,ValorLogico valorDeclarado,String unidad,String moneda,
                       MomentoHecho momento,String referenciaInterlocutor,String fuente,ConfirmacionContextual confirmacion) {
        public Declaracion {
            if(valorDeclarado!=null)valorDeclarado=new ValorLogico(valorDeclarado.texto(),valorDeclarado.numero(),valorDeclarado.booleano(),
                    valorDeclarado.fecha(),valorDeclarado.moneda(),valorDeclarado.valores()==null?null:List.copyOf(valorDeclarado.valores()));
        }
    }
    record ProximaActuacion(OrigenActuacion origen,LocalDate fecha,String detalle) { }
    record RestriccionContacto(String declaracion,ReferenciaContactoContextual destinatario,String finalidad,String fuente) { }
    /** Declaración explícita de consulta; una entrada o una llamada por sí sola no la acredita. */
    record ConsultaRecibida(String evento,MomentoHecho momento) { }
    /** Referencia nula sólo para responder la consulta de esta misma comunicación. */
    record RespuestaComercial(ReferenciaVersion consulta,String evento,MomentoHecho momento,String constancia) { }
    record AtencionConsultas(ConsultaRecibida recibida,List<RespuestaComercial> respuestas) {
        public AtencionConsultas {respuestas=respuestas==null?List.of():List.copyOf(respuestas);}
    }
    /** Proyección de medición sin contenido, participantes, contactos o notas. */
    record DatoRespuestaConsulta(ReferenciaVersion consulta,MomentoHecho momento) { }
    record DatoAtencionConsultas(boolean consultaRecibida,MomentoHecho momentoConsulta,List<DatoRespuestaConsulta> respuestas,long versionHecho) {
        public DatoAtencionConsultas {respuestas=List.copyOf(respuestas);}
    }
    record DatosInteraccion(List<VinculoActividad> vinculos,ParticipanteDeclarado destinatario,ParticipanteDeclarado interlocutor,
                            TipoActividad tipo,CanalComunicacion canal,String medioReal,List<EventoDeclarado> eventos,
                            ResultadoInteraccion resultado,MomentoHecho momento,String resumen,List<Declaracion> declaraciones,
                            ProximaActuacion proxima,Long interaccionAnterior,String fuente,RestriccionContacto restriccion,
                            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) GestionContacto gestionContacto,
                            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) AtencionConsultas atencionConsultas) {
        public DatosInteraccion(List<VinculoActividad> vinculos,ParticipanteDeclarado destinatario,ParticipanteDeclarado interlocutor,
                                TipoActividad tipo,CanalComunicacion canal,String medioReal,List<EventoDeclarado> eventos,
                                ResultadoInteraccion resultado,MomentoHecho momento,String resumen,List<Declaracion> declaraciones,
                                ProximaActuacion proxima,Long interaccionAnterior,String fuente,RestriccionContacto restriccion,GestionContacto gestionContacto) {
            this(vinculos,destinatario,interlocutor,tipo,canal,medioReal,eventos,resultado,momento,resumen,declaraciones,
                    proxima,interaccionAnterior,fuente,restriccion,gestionContacto,null);
        }
        /** Los hechos anteriores sin finalidad/función conservan su ausencia, sin equivalencias automáticas. */
        public DatosInteraccion(List<VinculoActividad> vinculos,ParticipanteDeclarado destinatario,ParticipanteDeclarado interlocutor,
                                TipoActividad tipo,CanalComunicacion canal,String medioReal,List<EventoDeclarado> eventos,
                                ResultadoInteraccion resultado,MomentoHecho momento,String resumen,List<Declaracion> declaraciones,
                                ProximaActuacion proxima,Long interaccionAnterior,String fuente,RestriccionContacto restriccion) {
            this(vinculos,destinatario,interlocutor,tipo,canal,medioReal,eventos,resultado,momento,resumen,declaraciones,
                    proxima,interaccionAnterior,fuente,restriccion,null);
        }
        public DatosInteraccion {
            vinculos=vinculos==null?null:List.copyOf(vinculos);
            eventos=eventos==null?null:List.copyOf(eventos);
            declaraciones=declaraciones==null?null:List.copyOf(declaraciones);
        }
    }
    record VinculoFicha(String tipo,long referencia,Long versionReferencia) { }
    record DatosLegados(String contexto,String transcripcionNota,OffsetDateTime fechaHora) { }
    record FichaInteraccion(long id,long version,long idAgente,LocalDate fechaHecho,Instant registradoEn,
                            DatosInteraccion datos,List<VinculoFicha> vinculos,String versionDiccionario,
                            Procedencia procedencia,String resultadoHistorico,String canalHistorico,
                            String agenteNombre,List<HechoPosterior> hechosPosteriores,String rotuloResultado,DatosLegados datosLegados,
                            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) DatoContactoProspeccion contactoProspeccion) {
        public FichaInteraccion(long id,long version,long idAgente,LocalDate fechaHecho,Instant registradoEn,
                                 DatosInteraccion datos,List<VinculoFicha> vinculos,String versionDiccionario,Procedencia procedencia,
                                 String resultadoHistorico,String canalHistorico,String agenteNombre,List<HechoPosterior> hechosPosteriores,String rotuloResultado,DatosLegados datosLegados){
            this(id,version,idAgente,fechaHecho,registradoEn,datos,vinculos,versionDiccionario,procedencia,resultadoHistorico,canalHistorico,agenteNombre,hechosPosteriores,rotuloResultado,datosLegados,null);
        }
        public FichaInteraccion {
            vinculos=vinculos==null?List.of():List.copyOf(vinculos);
            hechosPosteriores=hechosPosteriores==null?List.of():List.copyOf(hechosPosteriores);
        }
    }
    record Rectificacion(long idInteraccion,Long versionEsperada,DatosInteraccion correccion,String motivo) { }
    record AdicionVinculo(Long versionEsperada,VinculoActividad vinculo,String motivo) { }
    record ObservacionPosterior(Long versionEsperada,MomentoHecho momento,String detalle,String fuente,
                               ResultadoInteraccion resultadoObservado) { }
    record HechoPosterior(long version,Instant registradoEn,ObservacionPosterior datos,Procedencia procedencia) { }
    record FiltrosInteraccion(TipoContexto contexto,Long referencia,TipoActividad tipo,ResultadoInteraccion resultado,
                              CanalComunicacion canal,String q,int pagina,int tamano) { }
    record Antecedente(long version,Instant registradoEn,String operacion,String motivo,FichaInteraccion datos) { }
    record ReferenciaVersion(@com.fasterxml.jackson.annotation.JsonProperty(value="id",required=true) long id,
                             @com.fasterxml.jackson.annotation.JsonProperty(value="version",required=true) long version) { }
    record DatoProximaActuacion(OrigenActuacion origen,LocalDate fecha) { }
    record MedicionInteraccion(long id,long version,long idAgente,LocalDate fechaHecho,Instant registradoEn,
                               TipoActividad tipo,ResultadoInteraccion resultado,List<EventoDeclarado> eventos,List<VinculoFicha> vinculos,
                               DatoContactoProspeccion contactoProspeccion,DatoAtencionConsultas atencionConsultas,DatoProximaActuacion proximaActuacion) {
        public MedicionInteraccion(long id,long version,long idAgente,LocalDate fechaHecho,Instant registradoEn,TipoActividad tipo,ResultadoInteraccion resultado,List<EventoDeclarado> eventos,List<VinculoFicha> vinculos,DatoContactoProspeccion contactoProspeccion,DatoAtencionConsultas atencionConsultas){this(id,version,idAgente,fechaHecho,registradoEn,tipo,resultado,eventos,vinculos,contactoProspeccion,atencionConsultas,null);}
        public MedicionInteraccion(long id,long version,long idAgente,LocalDate fechaHecho,Instant registradoEn,
                                   TipoActividad tipo,ResultadoInteraccion resultado,List<EventoDeclarado> eventos,List<VinculoFicha> vinculos,
                                   DatoContactoProspeccion contactoProspeccion) {
            this(id,version,idAgente,fechaHecho,registradoEn,tipo,resultado,eventos,vinculos,contactoProspeccion,null);
        }
        public MedicionInteraccion(long id,long version,long idAgente,LocalDate fechaHecho,Instant registradoEn,
                                   TipoActividad tipo,ResultadoInteraccion resultado,List<EventoDeclarado> eventos,List<VinculoFicha> vinculos) {
            this(id,version,idAgente,fechaHecho,registradoEn,tipo,resultado,eventos,vinculos,null);
        }
        public MedicionInteraccion {eventos=List.copyOf(eventos);vinculos=List.copyOf(vinculos);}
    }
    record ConteoReporte(long total,List<ReferenciaVersion> referencias) {
        public ConteoReporte {referencias=List.copyOf(referencias);}
    }
    /** Sin divulgar fuentes denegadas: la cobertura sólo impide certificar una medición incompleta. */
    record LecturaIndicadores(List<MedicionInteraccion> registros,
                             @com.fasterxml.jackson.annotation.JsonIgnore Set<Long> consultasConFuentesNoVerificables) {
        public LecturaIndicadores {registros=List.copyOf(registros);consultasConFuentesNoVerificables=Set.copyOf(consultasConFuentesNoVerificables);}
    }

    CatalogoInteracciones.Definicion definicion(Actor actor);
    void verificarContextoRegistro(String contexto,long referencia,Actor actor);
    DatosInteraccion validarRegistro(DatosInteraccion datos,Actor actor);
    /** Registrar declara comunicación real; no interpreta una nota preparada como contacto realizado. */
    FichaInteraccion registrar(DatosInteraccion datos,Actor actor,ContextoSesion sesion,Procedencia procedencia);
    Rectificacion validarRectificacion(Rectificacion datos,Actor actor);
    void exigirLecturaRectificacion(Rectificacion datos,Actor actor);
    FichaInteraccion rectificar(Rectificacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Optional<FichaInteraccion> consultarResultadoRectificacion(String clave,Actor actor);
    FichaInteraccion vincular(long id,AdicionVinculo datos,Actor actor,ContextoSesion sesion,Procedencia procedencia);
    FichaInteraccion observar(long id,ObservacionPosterior datos,Actor actor,ContextoSesion sesion,Procedencia procedencia);
    Pagina<FichaInteraccion> listar(FiltrosInteraccion filtros,Actor actor);
    FichaInteraccion obtener(long id,Actor actor);
    FichaInteraccion obtenerVersion(long id,long version,Actor actor);
    List<Antecedente> historia(long id,Actor actor);
    List<FichaInteraccion> porOportunidad(long id,Instant corte,Actor actor);
    List<FichaInteraccion> porPropiedad(long id,Actor actor,FinalidadLectura finalidad);
    List<FichaInteraccion> porCliente(long id,Actor actor,FinalidadLectura finalidad);
    LecturaIndicadores indicadores(Actor actor);
    ConteoReporte contarParaReporte(long idEncargo,LocalDate desde,LocalDate hasta,Actor actor);
    void exigirVersiones(List<ReferenciaVersion> referencias,Actor actor,FinalidadLectura finalidad);
}
