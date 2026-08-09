package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Casos de uso del local comercial (F2-oferta). Los records espejan el
 * contrato REST CONGELADO (Dtos.LocalRequest/LocalResponse de la v1); la
 * capa web solo los traduce 1:1 a sus DTOs.
 *
 * Pendiente de los modulos siguientes de F2 (documentado en el README):
 * "mis-locales" y la regla de pertenencia agente->captacion llegan con
 * captacion; la prospeccion inicial del alta llega con prospeccion; la
 * alerta por cambio sensible llega con el modulo transversal de alertas.
 */
public interface LocalComercialService {

    /** Espejo de LocalRequest (cable congelado). */
    record DatosLocal(String codigoLocal, String direccion, String distrito, BigDecimal metraje,
                      BigDecimal precioReferencial, String monedaReferencial,
                      String rubroPermitido, String descripcion,
                      Long idPropietario, String estado, String tipoInmueble, String uso, Integer ambientes,
                      Integer antiguedadAnios, String zonaUrbanizacion, BigDecimal geoLat, BigDecimal geoLong,
                      String estadoPublicacion, BigDecimal frente, String zonificacion,
                      Boolean aptoLicenciaFuncionamiento, BigDecimal cargaElectricaKw,
                      Integer numeroEstacionamientos, BigDecimal cuotaMantenimiento,
                      String interiorUnidad, String piso, String referenciaInterna,
                      String nombreEdificioGaleria) {
        public DatosLocal(String codigoLocal, String direccion, String distrito, BigDecimal metraje,
                          BigDecimal precioReferencial, String monedaReferencial,
                          String rubroPermitido, String descripcion, Long idPropietario,
                          String estado, String tipoInmueble, String uso, Integer ambientes,
                          Integer antiguedadAnios, String zonaUrbanizacion, BigDecimal geoLat,
                          BigDecimal geoLong, String estadoPublicacion, BigDecimal frente,
                          String zonificacion, Boolean aptoLicenciaFuncionamiento,
                          BigDecimal cargaElectricaKw, Integer numeroEstacionamientos,
                          BigDecimal cuotaMantenimiento) {
            this(codigoLocal, direccion, distrito, metraje, precioReferencial, monedaReferencial,
                    rubroPermitido, descripcion, idPropietario, estado, tipoInmueble, uso,
                    ambientes, antiguedadAnios, zonaUrbanizacion, geoLat, geoLong,
                    estadoPublicacion, frente, zonificacion, aptoLicenciaFuncionamiento,
                    cargaElectricaKw, numeroEstacionamientos, cuotaMantenimiento,
                    null, null, null, null);
        }
    }

    /** Espejo de LocalResponse (cable congelado). idPropietario = persona_rol.id del rol PROPIETARIO. */
    record FichaLocal(Long id, String codigoLocal, String direccion, String distrito, BigDecimal metraje,
                      BigDecimal precioReferencial, String monedaReferencial,
                      String rubroPermitido, String descripcion, String estado,
                      Long idPropietario, String propietarioNombre, String tipoInmueble, String uso,
                      Integer ambientes, Integer antiguedadAnios, String zonaUrbanizacion, BigDecimal geoLat,
                      BigDecimal geoLong, String estadoPublicacion, BigDecimal frente, String zonificacion,
                      Boolean aptoLicenciaFuncionamiento, BigDecimal cargaElectricaKw,
                      Integer numeroEstacionamientos, BigDecimal cuotaMantenimiento,
                      Long idDistrito, LocalDateTime fechaRegistro, String fotoPortadaClave,
                      String estadoRegistro, String disponibilidadComercial,
                      String interiorUnidad, String piso, String referenciaInterna,
                      String nombreEdificioGaleria) {
    }

    record FotoLocal(Long idFoto, String clave, String nombreArchivo) {
    }

    /**
     * Filtros del listado. {@code texto} busca en codigo, direccion, distrito y
     * nombre del propietario; {@code estado} es el codigo de una letra (D/N/I).
     * Ambos opcionales: nulos o en blanco = sin filtro, que es como se comporta
     * el {@code GET /locales} sin parametros de siempre.
     */
    record FiltrosLocal(String texto, String estado, int pagina, int tamano) {
    }

    /**
     * Contadores del listado, calculados EN LA BASE con el mismo filtro de
     * texto que la lista. No dependen de las filas que el cliente descargo:
     * son de la cartera entera, por eso siguen siendo ciertos en la pagina 7.
     *
     * <p>{@code total} es la suma de los tres estados, no una cuarta consulta.
     */
    record ResumenLocales(long total, long disponibles, long noDisponibles, long inactivos) {
    }

    /** Advertencia informativa: el alta sigue siendo una decisión del agente. */
    record PosibleDuplicado(Long id, String codigoLocal, String direccion,
                            String interiorUnidad, String piso, BigDecimal metraje,
                            List<String> criteriosCoincidentes) {
    }

    /**
     * Pagina de la cartera con filtro, orden estable y conteo resueltos en SQL;
     * portada y estado de publicacion se resuelven en lote para la pagina (sin
     * N+1). El alcance es el TENANT: la cartera es de toda la organizacion, sin
     * filtro por rol (ver `docs/ai/matriz-operacion-rol.md`).
     */
    Pagina<FichaLocal> listar(FiltrosLocal filtros, Actor actor);

    /** KPI del listado, con el mismo {@code texto} para que cuadren con la lista. */
    ResumenLocales resumen(String texto, Actor actor);

    /**
     * Busca inmuebles técnicamente parecidos del mismo propietario y tenant.
     * No crea ni modifica datos y nunca sustituye la revisión humana.
     */
    List<PosibleDuplicado> posiblesDuplicados(DatosLocal datos, Long idExcluir, Actor actor);

    /** Deuda F2 cerrada: locales de las captaciones del agente (RF-004, GET mis-locales). */
    Pagina<FichaLocal> listarMisLocales(int limite, int desplazamiento, Actor actor);

    Optional<FichaLocal> buscarPorId(long id, Actor actor);

    /**
     * Alta del local con las reglas de la v1 (estado por defecto D, solo
     * tipo L/O y uso C, distrito resuelto contra el catalogo) + la
     * publicacion principal sincronizada si llega estadoPublicacion.
     */
    FichaLocal registrar(DatosLocal datos, Actor actor);

    /**
     * Edicion comercial (RF-004): si cambia el precio se registra el hito
     * 'U' en el historico; si cambia el estado, la transicion queda
     * auditada en historial_estado.
     */
    FichaLocal actualizar(long id, DatosLocal datos, Actor actor);

    /** Baja logica (estado I) con transicion auditada. */
    boolean desactivar(long id, Actor actor);

    List<FotoLocal> listarFotos(long idPropiedad);

    /** Registra la clave de una foto ya guardada en el almacen (maximo 6 por local). */
    FotoLocal agregarFoto(long idPropiedad, String clave, String nombreArchivo, Actor actor);

    /** Elimina el registro y devuelve la clave del binario para limpiar el almacen. */
    Optional<String> eliminarFoto(long idFoto, Actor actor);
}
