package com.controllocal.web.dto;

import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.AtributoFicha;
import com.controllocal.service.PropiedadUniversalService.EncargoFicha;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService.HitoFicha;
import com.controllocal.service.PropiedadUniversalService.ResultadoRegistro;
import com.controllocal.service.PropiedadUniversalService.TitularFicha;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.soporte.Procedencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * El cable de la propiedad universal (D-E4-1).
 *
 * <p>Van juntos en un fichero porque son <b>un solo contrato</b>: el cuerpo del
 * alta, el de la edicion y la ficha que devuelven las tres operaciones.
 * Repartirlos en once ficheros de veinte lineas obliga a abrir once para
 * entender uno.
 *
 * <p><b>Aqui la operacion viaja con palabras</b> —{@code "VENTA"},
 * {@code "ALQUILER"}— y no con el codigo de una letra que guarda la base. El
 * cable lo lee una persona cuando depura y lo consume KAIROS, y {@code "V"}
 * frente a {@code "A"} es exactamente el tipo de detalle que se confunde con
 * {@code "A"} de ALMACEN en la columna de al lado.
 */
public final class PropiedadUniversalDtos {

    private PropiedadUniversalDtos() {
    }

    // ------------------------------------------------------------------
    // Entrada
    // ------------------------------------------------------------------

    public record TitularRequest(Long idPropietario, BigDecimal cuota, Boolean representante) {
        PropiedadUniversalService.Titular aDatos() {
            return new PropiedadUniversalService.Titular(idPropietario, cuota, representante);
        }
    }

    public record AtributoRequest(String clave, String valor) {
        PropiedadUniversalService.ValorAtributo aDatos() {
            return new PropiedadUniversalService.ValorAtributo(clave, valor);
        }
    }

    public record UbicacionRequest(String direccion, String distrito, String zonaUrbanizacion,
                                   BigDecimal latitud, BigDecimal longitud, String interiorUnidad,
                                   String piso, String referenciaInterna,
                                   String nombreEdificioGaleria) {
        Ubicacion aDatos() {
            return new Ubicacion(direccion, distrito, zonaUrbanizacion, latitud, longitud,
                    interiorUnidad, piso, referenciaInterna, nombreEdificioGaleria);
        }
    }

    /**
     * Una relacion comercial. {@code operacion} es VENTA o ALQUILER y no tiene
     * valor por defecto: sin ella no se sabe si {@code importe} es un precio de
     * venta o una renta mensual.
     */
    public record OperacionRequest(String operacion, BigDecimal importe, String moneda,
                                   String tipoComision, String baseCalculo, BigDecimal valorComision,
                                   String tratamientoIgv, Boolean exclusividad,
                                   LocalDate inicioEncargo, LocalDate finEncargo) {
        PropiedadUniversalService.OperacionSolicitada aDatos() {
            return new PropiedadUniversalService.OperacionSolicitada(operacion, importe, moneda,
                    tipoComision, baseCalculo, valorComision, tratamientoIgv, exclusividad,
                    inicioEncargo, finEncargo);
        }
    }

    /**
     * El alta. Para una propiedad en venta <b>y</b> en alquiler se mandan dos
     * elementos en {@code operaciones}; nunca un valor combinado.
     */
    public record RegistroRequest(String codigo, String tipoPropiedad, String uso,
                                  String descripcion, UbicacionRequest ubicacion,
                                  List<TitularRequest> titulares, List<AtributoRequest> atributos,
                                  List<OperacionRequest> operaciones, Long idBorrador) {

        public PropiedadUniversalService.ComandoRegistro aDatos(String claveIdempotencia,
                                                                Procedencia procedencia) {
            return new PropiedadUniversalService.ComandoRegistro(claveIdempotencia, procedencia, codigo,
                    tipoPropiedad, uso, descripcion,
                    ubicacion == null ? null : ubicacion.aDatos(),
                    titulares == null ? null : titulares.stream().map(TitularRequest::aDatos).toList(),
                    atributos == null ? null : atributos.stream().map(AtributoRequest::aDatos).toList(),
                    operaciones == null ? null : operaciones.stream().map(OperacionRequest::aDatos).toList(),
                    idBorrador);
        }
    }

    /** La edicion. Lo que llega {@code null} no se toca. */
    public record EdicionRequest(String descripcion, UbicacionRequest ubicacion,
                                 List<TitularRequest> titulares, List<AtributoRequest> atributos,
                                 List<OperacionRequest> operaciones) {

        public PropiedadUniversalService.ComandoEdicion aDatos(String claveIdempotencia,
                                                               Procedencia procedencia) {
            return new PropiedadUniversalService.ComandoEdicion(claveIdempotencia, procedencia, descripcion,
                    ubicacion == null ? null : ubicacion.aDatos(),
                    titulares == null ? null : titulares.stream().map(TitularRequest::aDatos).toList(),
                    atributos == null ? null : atributos.stream().map(AtributoRequest::aDatos).toList(),
                    operaciones == null ? null : operaciones.stream().map(OperacionRequest::aDatos).toList());
        }
    }

    // ------------------------------------------------------------------
    // Salida
    // ------------------------------------------------------------------

    public record TitularResponse(Long idPropietario, String nombre, BigDecimal cuota,
                                  boolean representante, LocalDate desde) {
        static TitularResponse desde(TitularFicha f) {
            return new TitularResponse(f.idRolPropietario(), f.nombre(), f.cuota(),
                    f.representante(), f.desde());
        }
    }

    public record AtributoResponse(String clave, String rotulo, String tipoDato, String unidad,
                                   String valor) {
        static AtributoResponse desde(AtributoFicha f) {
            return new AtributoResponse(f.clave(), f.rotulo(), f.tipoDato(), f.unidad(), f.valor());
        }
    }

    public record HitoResponse(String hito, BigDecimal monto, String moneda, LocalDate fecha) {
        static HitoResponse desde(HitoFicha f) {
            return new HitoResponse(f.hito(), f.monto(), f.moneda(), f.fecha());
        }
    }

    /** Un encargo con SU historico. Dos encargos no comparten serie. */
    public record EncargoResponse(Long idEncargo, String codigo, String operacion, String estado,
                                  BigDecimal importe, String moneda, LocalDate inicio, LocalDate fin,
                                  List<HitoResponse> historico) {
        static EncargoResponse desde(EncargoFicha f) {
            return new EncargoResponse(f.idEncargo(), f.codigo(), f.operacion(), f.estado(),
                    f.importe(), f.moneda(), f.inicio(), f.fin(),
                    f.historico().stream().map(HitoResponse::desde).toList());
        }
    }

    /**
     * La propiedad leida por el modelo universal.
     *
     * <p>{@code atributosQueFaltan} no es un error: es lo que permite a la
     * ficha avisar de que no se puede publicar todavia, y al motor de captura
     * saber que preguntar.
     */
    public record PropiedadResponse(Long id, String codigo, String tipoPropiedad, String uso,
                                    String descripcion, String estadoRegistro,
                                    String disponibilidadComercial, UbicacionResponse ubicacion,
                                    List<TitularResponse> titulares,
                                    List<AtributoResponse> atributos,
                                    List<EncargoResponse> encargos,
                                    List<String> atributosQueFaltan, LocalDateTime fechaRegistro) {

        public static PropiedadResponse desde(FichaPropiedadUniversal f) {
            return new PropiedadResponse(f.id(), f.codigo(), f.tipoPropiedad(), f.uso(),
                    f.descripcion(), f.estadoRegistro(), f.disponibilidadComercial(),
                    UbicacionResponse.desde(f.ubicacion()),
                    f.titulares().stream().map(TitularResponse::desde).toList(),
                    f.atributos().stream().map(AtributoResponse::desde).toList(),
                    f.encargos().stream().map(EncargoResponse::desde).toList(),
                    f.atributosQueFaltan(), f.fechaRegistro());
        }
    }

    public record UbicacionResponse(String direccion, String distrito, String zonaUrbanizacion,
                                    BigDecimal latitud, BigDecimal longitud, String interiorUnidad,
                                    String piso, String referenciaInterna,
                                    String nombreEdificioGaleria) {
        static UbicacionResponse desde(Ubicacion u) {
            return u == null ? null : new UbicacionResponse(u.direccion(), u.distrito(),
                    u.zonaUrbanizacion(), u.latitud(), u.longitud(), u.interiorUnidad(), u.piso(),
                    u.referenciaInterna(), u.nombreEdificioGaleria());
        }
    }

    /** {@code reintento = true} avisa de que esta clave ya habia producido esto. */
    public record RegistroResponse(Long idPropiedad, String codigo, List<Long> idsEncargos,
                                   boolean reintento) {
        public static RegistroResponse desde(ResultadoRegistro r) {
            return new RegistroResponse(r.idPropiedad(), r.codigo(), r.idsEncargos(), r.reintento());
        }
    }

    /** Lo que se pregunta para un tipo de propiedad. Lo deriva el catalogo. */
    public record PreguntaCatalogoResponse(String clave, String rotulo, String tipoDato,
                                           String unidad, boolean obligatoria, int orden) {
    }
}
