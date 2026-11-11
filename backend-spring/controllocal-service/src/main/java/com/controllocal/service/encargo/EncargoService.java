package com.controllocal.service.encargo;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** CAT-E12–15, RF015–019 y AC058–077. Preparar, presentar y habilitar son hechos distintos. */
public interface EncargoService {
    /** Valor P es fracción (0.03=3%); E es mensualidades y F importe en su moneda. Sin inferir por magnitud. */
    record Condiciones(BigDecimal precioPedido, String monedaPrecio, String periodoPrecio,
                       String tipoComision, String baseCalculo, BigDecimal valorComision,
                       String monedaComision, String tratamientoIgv, String motivoSinComision,
                       String hitoDevengo, String parteObligada, String beneficiario,
                       String exigibilidad, String procedencia, List<Tramo> tramos,
                       @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                       ComposicionMensual composicionMensual,
                       @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                       PactosDeEncargo.EjecucionPacto ejecucion) {
        public Condiciones { tramos=tramos==null?null:java.util.Collections.unmodifiableList(new java.util.ArrayList<>(tramos)); }
    }
    /** D1-09/D3: declaración del alcance mensual, no inventario ni otro mínimo del Encargo. */
    record ComposicionMensual(Boolean completa,String fuente,List<ImporteMensual> componentes) { }
    record ImporteMensual(String referencia,String concepto,BigDecimal importe,BigDecimal importeHasta,
                         boolean intervalo,String moneda,String periodo,
                         com.controllocal.service.matching.ContratoMatching.TratamientoMensual tratamiento,
                         String incluidoEn,String fuente,LocalDate vigenteDesde,LocalDate vigenteHasta) { }
    record Tramo(String hito, BigDecimal importe, BigDecimal porcentaje, String moneda,
                 String exigibilidad,
                 @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                 PactosDeEncargo.EjecucionPacto ejecucion) { }
    enum TipoOrigenProspeccion { DIRECTO, PROSPECCION, DESCONOCIDO }
    record OrigenProspeccion(TipoOrigenProspeccion tipo,Long prospeccion) { }
    record DatosApertura(Long idPropiedad, Long idResponsable, String codigo,
                         OperacionInmobiliaria operacion, LocalDate desde, LocalDate hasta,
                         Boolean exclusividad, String observaciones, Condiciones condiciones,
                         Procedencia procedencia,
                         List<com.controllocal.service.PropiedadUniversalService.ValorAtributo> condicionesDinamicas,
                         @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) OrigenProspeccion origen) {
        public DatosApertura(Long propiedad,Long responsable,String codigo,OperacionInmobiliaria operacion,LocalDate desde,LocalDate hasta,Boolean exclusiva,String observaciones,Condiciones condiciones,Procedencia procedencia,List<com.controllocal.service.PropiedadUniversalService.ValorAtributo> dinamicas){this(propiedad,responsable,codigo,operacion,desde,hasta,exclusiva,observaciones,condiciones,procedencia,dinamicas,null);}
    }
    record Apertura(long id, String codigo, String estado, long version) { }
    /** Un único productor del episodio; se incorpora a la transacción del caso autenticado llamante. */
    Apertura abrir(DatosApertura datos, Actor actor);
    /** Productor interno de Prospección: true=origen; false=antecedente, sin cerrar otra gestión. */
    boolean vincularOrigenProspeccion(long encargo,long prospeccion,Actor actor);

    record Vigencia(boolean dentroDelPlazo, boolean vencido, boolean finEfectivo,
                     boolean habilitado, List<String> faltantes, LocalDate corte) { }
    record Facultades(boolean editar,boolean prepararDocumento,boolean presentar,boolean decidir,
                       boolean solicitarFin,boolean finalizar,boolean reasignar) { }
    record Ficha(long id, String codigo, String estado, long version, Long responsable,
                 LocalDate desde, LocalDate hasta, Condiciones condiciones, Vigencia vigencia,
                 Long peticionActual, Long documentoActual, Long composicionActual,Facultades facultades,
                 Long idPropiedad,String operacion,List<com.controllocal.service.PropiedadUniversalService.AtributoFicha> condicionesDinamicas,
                 @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) OrigenProspeccion origen) {
        public Ficha(long id,String codigo,String estado,long version,Long responsable,LocalDate desde,LocalDate hasta,Condiciones condiciones,Vigencia vigencia,Long peticion,Long documento,Long composicion,Facultades facultades,Long propiedad,String operacion,List<com.controllocal.service.PropiedadUniversalService.AtributoFicha> dinamicas){this(id,codigo,estado,version,responsable,desde,hasta,condiciones,vigencia,peticion,documento,composicion,facultades,propiedad,operacion,dinamicas,null);}
    }
    Ficha consultar(long id, Actor actor);
    /** RF046–049: identidad de partes ya declaradas, sin PDF, nombres, contactos ni nueva certificación jurídica. */
    record ParteNegociacion(Long titularidad,Long persona,Long representante,
                            UUID evidenciaTitularidad,UUID evidenciaRepresentacion,
                            String alcanceRepresentacion,LocalDate desdeRepresentacion,LocalDate hastaRepresentacion) { }
    record PartesNegociacion(long encargo,long versionEncargo,long versionContenido,
                             Long documento,List<ParteNegociacion> partes) { }
    PartesNegociacion consultarPartesNegociacion(long id,long versionEncargo,LocalDate fechaHecho,Actor actor);
    Vigencia consultarVigencia(long id, LocalDate corte, Actor actor);
    /** Sólo permisos internos construidos desde una invitación real; no es un selector de expedientes ajenos. */
    record OfertaAutorizada(long organizacion,long encargo,long idPropiedad,long version,long versionFisica,String operacion,Long composicionActual,
                           BigDecimal precioPedido,String monedaPrecio,String periodoPrecio,boolean habilitado) { }
    OfertaAutorizada ofertaDeInvitacion(com.controllocal.service.colaboracion.AccesoFuenteDeInvitacion.Permiso permiso);
    void exigirHabilitado(long id, Actor actor);
    /** Predicado interno L1; el coordinador autoriza primero el origen. No entrega expediente ni unidades. */
    record AptitudRecomendacion(long id,long version,long versionContenido,long propiedad,long versionFisica,
        long responsable,String operacion,Long composicion,boolean habilitado,List<String> causas) { }
    AptitudRecomendacion habilitacionParaRecomendacionInterna(long id,LocalDate corte,Actor actor);

    record Unidad(long idPropiedad, BigDecimal importeDeclarado, String moneda,
                  String procedencia) { }
    record DatosComposicion(List<Unidad> unidades, BigDecimal totalDeclarado, String moneda,
                            String procedencia, @JsonProperty(value="versionEsperada",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionEsperada) { }
    record Composicion(long id, long encargo, long version, List<Unidad> unidades,
                       BigDecimal totalDeclarado, String moneda, String procedencia) { }
    Composicion consultarComposicion(long id,Long versionComposicion,Actor actor);
    Composicion declararComposicion(long id, DatosComposicion datos, Actor actor,
                                    ContextoSesion sesion, String clave, Procedencia procedencia);

    record Parte(Long titularidad, Long persona, BigDecimal cuota, UUID evidenciaTitularidad, Long representante,
                  UUID evidenciaRepresentacion, String alcanceRepresentacion,
                  LocalDate desdeRepresentacion, LocalDate hastaRepresentacion,
                  String nombreTitularDeclarado,String nombreRepresentanteDeclarado) { }
    record DatosFisicos(SuficienciaInmueble.Contexto contexto,
                         Map<String,SuficienciaInmueble.Dato> datos) {
        public DatosFisicos { datos=datos==null?null:java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(datos)); }
    }
    record Preparacion(Long encargo, Long versionEncargo, Long idPropiedad,
                        DatosFisicos fisicos, List<Parte> partes,
                        OperacionInmobiliaria operacion, List<Unidad> unidades,
                        Condiciones condiciones, LocalDate desde, LocalDate hasta,
                        Boolean exclusividad,String plantillaVersion,
                        List<com.controllocal.service.PropiedadUniversalService.ValorAtributo> condicionesDinamicas) {
        public Preparacion {
            partes=partes==null?null:java.util.Collections.unmodifiableList(new java.util.ArrayList<>(partes));
            unidades=unidades==null?null:java.util.Collections.unmodifiableList(new java.util.ArrayList<>(unidades));
            condicionesDinamicas=condicionesDinamicas==null?null:java.util.Collections.unmodifiableList(new java.util.ArrayList<>(condicionesDinamicas));
        }
    }
    record Documento(long id, UUID referencia, Long encargo, long versionEncargo,
                      String huella, String plantillaVersion, List<String> faltantes,
                      OffsetDateTime preparadoEn,long version) { }
    Documento prepararDocumento(Preparacion datos, Actor actor, ContextoSesion sesion,
                                String clave, Procedencia procedencia);
    Documento regenerarDocumento(long id, Preparacion datos, Actor actor, ContextoSesion sesion,
                                  String clave, Procedencia procedencia);
    Documento consultarDocumento(long documento, Actor actor);
    record ArchivoLectura(UUID referencia, long idArchivo, Long encargo, Long documento,
                           long autor, String finalidad, String claveProveedor,
                           String nombre, String contentType, String huella,long version) { }
    /** Entrada exclusiva de la superficie segura; nunca se vuelcan bytes a una herramienta del modelo. */
    record CargaArchivo(Long encargo,Long documento,String finalidad,String nombre,String contentType,
                        byte[] contenido,@JsonProperty(value="versionDestino",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionDestino) { }
    record CargaTemporal(UUID referencia,Long encargoPrevisto,Long documentoPrevisto,String finalidad,
                         long versionDestino,String nombre,String contentType,String huella,long version) { }
    record VinculoArchivo(UUID referenciaOrigen,Long encargo,Long documento,String finalidad,
                         @JsonProperty(value="versionDestino",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionDestino) { }
    record ArchivoRegistrado(UUID referencia, Long encargo, Long documento, String finalidad,
                              String nombre, String contentType, String huella,Long version) { }
    CargaTemporal cargarArchivo(CargaArchivo datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    ArchivoRegistrado adjuntarOReutilizar(VinculoArchivo datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);

    /** El firmante real pertenece a una parte documentada; no es el destinatario de un envío. */
    record Firmante(long titularidad,long persona) { }
    record FirmaManuscrita(Firmante firmante,LocalDate fechaFirma,UUID documentoFirmado,String descripcion) { }
    record ArchivoFirma(UUID referencia,long version,String huella) { }
    record FirmaPresentada(long revisionPresentacion,String modalidad,long documento,long versionContenido,
                           String huellaMaterial,List<FirmaManuscrita> firmas,List<ArchivoFirma> archivos,
                           String huellaManifestacion) {
        public FirmaPresentada { firmas=List.copyOf(firmas);archivos=List.copyOf(archivos); }
    }
    record RevisionManuscrita(long revisionPresentacion,String huellaManifestacion,
                              Boolean documentoYFirmantesRevisados) { }
    record Presentacion(long documento,@JsonProperty(value="versionEsperada",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionEsperada,List<Parte> partes,
                         List<FirmaManuscrita> firmas, String respuestaObservaciones,
                         @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                         ReferenciaAdenda adenda) {
        public Presentacion {
            partes=partes==null?null:java.util.Collections.unmodifiableList(new java.util.ArrayList<>(partes));
            firmas=firmas==null?null:java.util.Collections.unmodifiableList(new java.util.ArrayList<>(firmas));
        }
    }
    record FacultadesPeticion(boolean reenviar,boolean decidir) { }
    record Peticion(long id, long encargo, long versionPresentada, long version,
                     String estado, long documento, List<UUID> evidencias, Long ultimaRevision,
                     FacultadesPeticion facultades,String observaciones,List<String> faltantes,FirmaPresentada firma,
                     @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                     ReferenciaAdenda adenda) {
        public Peticion {
            evidencias=evidencias==null?null:List.copyOf(evidencias);
            faltantes=faltantes==null?null:List.copyOf(faltantes);
        }
    }
    Peticion consultarPeticion(long peticion, Actor actor);
    Peticion presentar(long encargo, Presentacion datos, Actor actor, ContextoSesion sesion,
                        String clave, Procedencia procedencia);
    Peticion corregirYReenviar(long peticion, long versionPeticion, Presentacion datos,
                               Actor actor, ContextoSesion sesion, String clave, Procedencia procedencia);
    record Decision(String actuacion,@JsonProperty(value="versionPeticion",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionPeticion,
                    @JsonProperty(value="versionEncargo",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionEncargo,
                     String observaciones,RevisionManuscrita revisionFirma) { }
    Decision validarDecision(long peticion,Decision datos,Actor actor);
    void exigirLecturaDecision(long peticion,Decision datos,Actor actor);
    Optional<Peticion> consultarResultadoDecision(String clave,Actor actor);
    Peticion decidir(long peticion, Decision datos, Actor actor, ContextoSesion sesion,
                      String clave, Procedencia procedencia);
    record ReferenciaAdenda(long encargo,long propuesta) { }
    record DatosAdenda(PactosDeEncargo.ReferenciaPacto anterior,Long versionEncargoEsperada,
                       Preparacion propuesta,String motivo,String alcance,
                       LocalDate vigenteDesde,LocalDate vigenteHasta,ReferenciaAdenda antecedentePropuesta) { }
    record DecisionBorradorAdenda(String actuacion,Long ultimaRevisionEsperada,
                                  Long versionEncargoEsperada,String huellaPropuesta,String observaciones) { }
    record Adenda(ReferenciaAdenda referencia,PactosDeEncargo.ReferenciaPacto anterior,
                  long ultimaRevision,long versionContenidoAnterior,long versionContenidoPropuesta,
                  String huellaPropuesta,Preparacion propuesta,String motivo,String alcance,
                  LocalDate vigenteDesde,LocalDate vigenteHasta,Long revisionAutorizacionBorrador,
                  Long documento,Long peticion,Long revisionActivacion,List<String> faltantes) {
        public Adenda { faltantes=List.copyOf(faltantes); }
    }
    Adenda proponerAdenda(long encargo,DatosAdenda datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Adenda consultarAdenda(ReferenciaAdenda referencia,Actor actor);
    DecisionBorradorAdenda validarDecisionBorradorAdenda(ReferenciaAdenda referencia,DecisionBorradorAdenda datos,Actor actor);
    void exigirLecturaDecisionBorradorAdenda(ReferenciaAdenda referencia,DecisionBorradorAdenda datos,Actor actor);
    Optional<Adenda> consultarResultadoDecisionBorradorAdenda(String clave,Actor actor);
    Adenda autorizarBorradorAdenda(ReferenciaAdenda referencia,DecisionBorradorAdenda datos,Actor actor,
                                 ContextoSesion sesion,String clave,Procedencia procedencia);
    record Edicion(@JsonProperty(value="versionEsperada",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionEsperada,LocalDate desde,LocalDate hasta,Boolean exclusividad,
                    String observaciones,Condiciones condiciones,String motivo,
                    List<com.controllocal.service.PropiedadUniversalService.ValorAtributo> atributos,List<String> atributosABorrar) { }
    Ficha editar(long encargo, Edicion datos, Actor actor, ContextoSesion sesion,
                  String clave, Procedencia procedencia);

    record DatosFin(String tipoFin,LocalDate fechaEfectiva,String motivo,
                     @JsonProperty(value="versionEsperada",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionEsperada,
                     List<UUID> evidencias) { }
    record FacultadesSolicitudFin(boolean cancelar,boolean decidir,boolean reenviar) { }
    record SolicitudFin(long id, long encargo, long versionEncargo, long version, String estado,
                         String tipoFin, LocalDate fechaSolicitada, String motivo,
                         List<UUID> evidencias, Long ultimaRevision,FacultadesSolicitudFin facultades,String observaciones) { }
    SolicitudFin solicitarFin(long encargo, DatosFin datos, Long solicitudObservada,
                              Long versionSolicitud, Actor actor, ContextoSesion sesion,
                              String clave, Procedencia procedencia);
    SolicitudFin consultarSolicitudFin(long solicitud, Actor actor);
    SolicitudFin cancelarSolicitudFin(long solicitud, long version, String motivo, Actor actor,
                                      ContextoSesion sesion, String clave, Procedencia procedencia);
    SolicitudFin decidirSolicitudFin(long solicitud, Decision datos, Actor actor,
                                     ContextoSesion sesion, String clave, Procedencia procedencia);
    Ficha finalizar(long encargo, DatosFin datos, Actor actor, ContextoSesion sesion,
                    String clave, Procedencia procedencia);
}
