package com.controllocal.web.dto;

import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ActividadPropiedad;
import com.controllocal.service.PropiedadUniversalService.AtributoFicha;
import com.controllocal.service.PropiedadUniversalService.AtributoQueFalta;
import com.controllocal.service.PropiedadUniversalService.HechoDeActividad;
import com.controllocal.service.PropiedadUniversalService.EncargoFicha;
import com.controllocal.service.PropiedadUniversalService.EpisodiosDeOperacion;
import com.controllocal.service.PropiedadUniversalService.GestionDePublicacion;
import com.controllocal.service.PropiedadUniversalService.HistoriaComercial;
import com.controllocal.service.PropiedadUniversalService.HitoDeLaHistoria;
import com.controllocal.service.PropiedadUniversalService.ImporteFechado;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService.HitoFicha;
import com.controllocal.service.PropiedadUniversalService.ResultadoRegistro;
import com.controllocal.service.PropiedadUniversalService.TitularFicha;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.dto.PublicacionDtos.PublicacionResponse;

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

    /**
     * <b>Un valor que se escribe.</b>
     *
     * <p>Llevaba solo {@code (clave, valor)} hasta V77, y con eso un IMPORTE
     * no podia decir su moneda ni un LISTA_MULTIPLE sus elementos: el servicio
     * ya los modelaba y era este DTO el que iba estrecho, asi que dos
     * condiciones del encargo —el precio de la cochera adicional y el
     * equipamiento incluido— habrian quedado sembradas y mudas.
     *
     * <h2>Y la procedencia del DATO, no del acto (4.P)</h2>
     * Los cuatro ultimos campos son <b>opcionales</b> y describen de donde sale
     * <b>este</b> valor. Ningun cliente actual los manda —el SPA no estrena
     * superficie de captura en 4.P— y por eso el cable de siempre sigue
     * funcionando sin tocar una linea de Angular.
     *
     * <p><b>El canal, el agente y el actor NO viajan aqui.</b> Los sabe el Core:
     * el canal sale de {@code X-Origen} y el actor de la sesion. Lo que viaja
     * por valor es lo unico que el Core no puede saber — la diferencia entre
     * «me lo dijo el propietario» y «lo vi yo».
     *
     * <p>El vocabulario del enum viaja por el cable y <b>no se le ensena al
     * usuario</b>: cuando llegue su UX, la interfaz dira «lo observe» o «me lo
     * dijeron».
     *
     * @param moneda       obligatoria cuando la clave es un IMPORTE; el trigger
     *                     la exige y el mensaje lo dice
     * @param valores      los elementos de un multivalor. <b>Sustituyen</b> a
     *                     los que hubiera: una lista es un conjunto, no un delta
     * @param naturaleza   {@code DECLARADO}, {@code OBSERVADO} o {@code INFERIDO}.
     *                     Omitida cuando no consta, que es lo normal — y omitirla
     *                     no es una cuarta clase de evidencia, es que no se sabe
     * @param confianza    de 0 a 1, <b>obligatoria con {@code INFERIDO}</b> junto
     *                     con el agente, el modelo y su version, que ya viajan en
     *                     las cabeceras de procedencia
     * @param observadoEn  cuando se observo, si se sabe: una visita del martes
     *                     anotada el viernes vale por el martes
     * @param evidenciaRef el puntero a la prueba
     */
    public record AtributoRequest(String clave, String valor, String moneda,
                                  List<String> valores, String naturaleza, BigDecimal confianza,
                                  LocalDate observadoEn, String evidenciaRef) {

        /** El valor sin declarar procedencia: el cable de todos los clientes de hoy. */
        public AtributoRequest(String clave, String valor, String moneda, List<String> valores) {
            this(clave, valor, moneda, valores, null, null, null, null);
        }

        /** El valor de un solo hueco, que es el caso corriente. */
        public AtributoRequest(String clave, String valor) {
            this(clave, valor, null, null);
        }

        PropiedadUniversalService.ValorAtributo aDatos() {
            return new PropiedadUniversalService.ValorAtributo(clave, valor, moneda, valores,
                    naturaleza, confianza, observadoEn, evidenciaRef);
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
                                  List<OperacionRequest> operaciones, Long idBorrador,
                                  String origen) {

        /**
         * El alta sin declarar procedencia. No hay defecto silencioso: el Core la
         * lee de lo que el comando esta HACIENDO —un alta que abre encargos es
         * trabajo operativo; una que no abre ninguno es conocimiento de mercado—
         * y el cliente puede corregirla declarandola (V76).
         */
        public RegistroRequest(String codigo, String tipoPropiedad, String uso,
                               String descripcion, UbicacionRequest ubicacion,
                               List<TitularRequest> titulares, List<AtributoRequest> atributos,
                               List<OperacionRequest> operaciones, Long idBorrador) {
            this(codigo, tipoPropiedad, uso, descripcion, ubicacion, titulares, atributos,
                    operaciones, idBorrador, null);
        }

        public PropiedadUniversalService.ComandoRegistro aDatos(String claveIdempotencia,
                                                                Procedencia procedencia) {
            return new PropiedadUniversalService.ComandoRegistro(claveIdempotencia, procedencia, codigo,
                    tipoPropiedad, uso, descripcion,
                    ubicacion == null ? null : ubicacion.aDatos(),
                    titulares == null ? null : titulares.stream().map(TitularRequest::aDatos).toList(),
                    atributos == null ? null : atributos.stream().map(AtributoRequest::aDatos).toList(),
                    operaciones == null ? null : operaciones.stream().map(OperacionRequest::aDatos).toList(),
                    idBorrador, origen);
        }
    }

    /**
     * La edicion.
     *
     * <pre>
     *   ausente o null             = no tocar
     *   con valor                  = cambiar
     *   clave en atributosABorrar  = retirar
     * </pre>
     *
     * <p>{@code null} <b>no</b> significa borrar, y {@code ""} tampoco: los dos
     * se rechazan o se ignoran segun el campo, nunca se adivinan. Borrar es una
     * intencion que se declara con su nombre.
     *
     * @param atributosABorrar claves <b>logicas</b> — {@code "piso"},
     *                         {@code "interiorUnidad"} — sin decir donde viven.
     *                         El cliente no sabe si una clave se guarda como
     *                         atributo gobernado o como campo del agregado, ni
     *                         le hace falta: el Core enruta el borrado por la
     *                         misma autoridad por la que enruta leer y escribir
     */
    public record EdicionRequest(String descripcion, UbicacionRequest ubicacion,
                                 List<TitularRequest> titulares, List<AtributoRequest> atributos,
                                 List<OperacionRequest> operaciones,
                                 List<String> atributosABorrar,
                                 List<CondicionesEncargoRequest> condiciones) {

        public PropiedadUniversalService.ComandoEdicion aDatos(String claveIdempotencia,
                                                               Procedencia procedencia) {
            return new PropiedadUniversalService.ComandoEdicion(claveIdempotencia, procedencia, descripcion,
                    ubicacion == null ? null : ubicacion.aDatos(),
                    titulares == null ? null : titulares.stream().map(TitularRequest::aDatos).toList(),
                    atributos == null ? null : atributos.stream().map(AtributoRequest::aDatos).toList(),
                    operaciones == null ? null : operaciones.stream().map(OperacionRequest::aDatos).toList(),
                    atributosABorrar,
                    condiciones == null ? null
                            : condiciones.stream().map(CondicionesEncargoRequest::aDatos).toList());
        }
    }

    /**
     * <b>Las condiciones pactadas en UN encargo.</b>
     *
     * <p>Un bloque por {@code idEncargo}, nunca una lista comun. Una propiedad
     * con la venta y el alquiler abiertos a la vez manda dos bloques, y el que
     * no manda queda intacto: la regla de bloques dice lo mismo aqui dentro que
     * fuera.
     *
     * <p>El cliente manda el <b>id del encargo</b> y no su operacion. No es
     * pedanteria: dos alquileres sucesivos de la misma propiedad son dos
     * episodios, y decir «bloque ALQUILER» haria que editar el de este ano
     * pisara el del anterior.
     */
    public record CondicionesEncargoRequest(Long idEncargo, List<AtributoRequest> atributos,
                                            List<String> atributosABorrar) {

        PropiedadUniversalService.CondicionesDeEncargo aDatos() {
            return new PropiedadUniversalService.CondicionesDeEncargo(idEncargo,
                    atributos == null ? null
                            : atributos.stream().map(AtributoRequest::aDatos).toList(),
                    atributosABorrar);
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

    /**
     * {@code valor} es el texto ya compuesto, para leer. {@code moneda} y
     * {@code valores} van crudos al lado, para poder <b>corregir</b>: partir
     * «PEN 350» o «COCINA, LAVADORA» de vuelta seria inferir, y un elemento
     * con una coma dentro lo haria imposible (V77).
     */
    public record AtributoResponse(String clave, String rotulo, String tipoDato, String unidad,
                                   String valor, String moneda, List<String> valores) {
        static AtributoResponse desde(AtributoFicha f) {
            return new AtributoResponse(f.clave(), f.rotulo(), f.tipoDato(), f.unidad(),
                    f.valor(), f.moneda(), f.valores());
        }
    }

    /** Un movimiento de la serie economica de un encargo, con su nombre. */
    public record HitoResponse(String hito, String hitoRotulo, BigDecimal monto, String moneda,
                               LocalDate fecha) {
        static HitoResponse desde(HitoFicha f) {
            return new HitoResponse(f.hito(), f.hitoRotulo(), f.monto(), f.moneda(), f.fecha());
        }
    }

    /**
     * <b>Un encargo con SU historico.</b> Dos encargos no comparten serie.
     *
     * <p>La identidad es {@code idEncargo}, no {@code operacion}: una propiedad
     * puede acumular varios encargos de ALQUILER a lo largo del tiempo, y
     * agruparlos por operacion fundiria series economicas que no tienen nada
     * que ver. Lo que la base prohibe es dos <b>vivos</b> de la misma
     * operacion, no que hayan existido varios.
     *
     * @param vivo           si sigue en juego. Los cerrados tambien viajan:
     *                       esconderlos borraria su historico de la vista
     * @param importeRotulo  «precio de venta» o «renta mensual». El nombre del
     *                       importe lo decide la operacion, y decidirlo en el
     *                       cliente pondria semantica inmobiliaria en cada
     *                       interfaz (D-A-1 §5)
     */
    /**
     * Si se puede gestionar la publicacion de este encargo, y si no, por que no.
     *
     * <p>Viaja como capacidad y no como estado en crudo para que la pantalla no
     * escriba {@code estado === 'A'} ni tenga que saber que un encargo cerrado
     * no se publica: es una regla de negocio (D-A-1 §5). El backend la vuelve a
     * imponer al escribir.
     */
    public record GestionPublicacionResponse(boolean permitida, String motivo) {
        static GestionPublicacionResponse desde(GestionDePublicacion f) {
            return f == null ? null : new GestionPublicacionResponse(f.permitida(), f.motivo());
        }
    }

    public record EncargoResponse(Long idEncargo, String codigo, String operacion,
                                  String operacionRotulo, String estado, String estadoRotulo,
                                  boolean vivo, BigDecimal importe, String moneda,
                                  String importeRotulo, Boolean exclusividad,
                                  Long idAgente, String agenteNombre,
                                  LocalDate inicio, LocalDate fin,
                                  List<HitoResponse> historico,
                                  List<AtributoResponse> condiciones,
                                  List<AtributoQueFaltaResponse> faltanParaPublicar,
                                  List<PublicacionResponse> publicaciones,
                                  GestionPublicacionResponse publicacionGestionable) {
        static EncargoResponse desde(EncargoFicha f) {
            return new EncargoResponse(f.idEncargo(), f.codigo(), f.operacion(),
                    f.operacionRotulo(), f.estado(), f.estadoRotulo(), f.vivo(),
                    f.importe(), f.moneda(), f.importeRotulo(), f.exclusividad(),
                    f.idAgente(), f.agenteNombre(), f.inicio(), f.fin(),
                    f.historico().stream().map(HitoResponse::desde).toList(),
                    // Las condiciones viajan DENTRO del encargo, no en la ficha:
                    // es donde se pactaron y es donde se editan (Corte 0C).
                    f.condiciones().stream().map(AtributoResponse::desde).toList(),
                    f.faltanParaPublicar().stream().map(AtributoQueFaltaResponse::desde).toList(),
                    f.publicaciones().stream().map(PublicacionResponse::desde).toList(),
                    GestionPublicacionResponse.desde(f.publicacionGestionable()));
        }
    }

    /** Una clave obligatoria que todavia no tiene valor, con su nombre legible. */
    public record AtributoQueFaltaResponse(String clave, String rotulo) {
        static AtributoQueFaltaResponse desde(AtributoQueFalta f) {
            return new AtributoQueFaltaResponse(f.clave(), f.rotulo());
        }
    }

    /**
     * Un hecho comercial <b>con la constancia de donde viene</b>.
     *
     * <p>{@code idEncargo} es lo que impide que la actividad vuelva a mezclar
     * lo que el modelo universal separo: una visita de quien quiere comprar y
     * otra de quien quiere alquilar la misma propiedad son hechos de dos
     * relaciones comerciales distintas, y en una lista plana se leen igual.
     *
     * <p>La procedencia la pone el productor. El cliente no puede deducirla sin
     * recorrer visita -> oportunidad -> captacion, que es topologia del modelo
     * y no le corresponde conocer (D-E4-3).
     */
    public record HechoResponse(String proceso, Long id, String codigo, String titulo,
                                String detalle, String estado, String estadoRotulo,
                                LocalDate fecha, BigDecimal monto, String moneda,
                                Long idEncargo, String operacion,
                                String operacionRotulo, String ruta) {
        static HechoResponse desde(HechoDeActividad f) {
            return new HechoResponse(f.proceso(), f.id(), f.codigo(), f.titulo(), f.detalle(),
                    f.estado(), f.estadoRotulo(), f.fecha(), f.monto(), f.moneda(),
                    f.idEncargo(), f.operacion(), f.operacionRotulo(), f.ruta());
        }
    }

    /** Un importe con su fecha y el encargo del que sale. */
    public record ImporteFechadoResponse(BigDecimal monto, String moneda, LocalDate fecha,
                                         Long idEncargo, String codigoEncargo) {
        static ImporteFechadoResponse desde(ImporteFechado f) {
            return f == null ? null : new ImporteFechadoResponse(f.monto(), f.moneda(), f.fecha(),
                    f.idEncargo(), f.codigoEncargo());
        }
    }

    /**
     * Que ha pasado con esta propiedad en UNA operacion a lo largo del tiempo.
     *
     * <p><b>{@code ultimoPedido} y {@code ultimoCierre} no son el mismo dato</b>:
     * uno es lo que se pidio y otro lo que se cerro. Cuando no hubo cierre llega
     * {@code null}, nunca relleno con el precio pedido -- ese respaldo convierte
     * "lo que pediamos" en "lo que vale" sin que nadie lo note.
     */
    public record EpisodiosResponse(String operacion, String operacionRotulo, int veces,
                                    LocalDate desde, LocalDate hasta, boolean vivoAhora,
                                    ImporteFechadoResponse ultimoPedido,
                                    ImporteFechadoResponse ultimoCierre) {
        static EpisodiosResponse desde(EpisodiosDeOperacion f) {
            return new EpisodiosResponse(f.operacion(), f.operacionRotulo(), f.veces(),
                    f.desde(), f.hasta(), f.vivoAhora(),
                    ImporteFechadoResponse.desde(f.ultimoPedido()),
                    ImporteFechadoResponse.desde(f.ultimoCierre()));
        }
    }

    /** Un movimiento economico del inmueble, con su procedencia intacta. */
    public record HitoHistoriaResponse(LocalDate fecha, String hito, String hitoRotulo,
                                       BigDecimal monto, String moneda, Long idEncargo,
                                       String codigoEncargo, String operacion,
                                       String operacionRotulo) {
        static HitoHistoriaResponse desde(HitoDeLaHistoria f) {
            return new HitoHistoriaResponse(f.fecha(), f.hito(), f.hitoRotulo(), f.monto(),
                    f.moneda(), f.idEncargo(), f.codigoEncargo(), f.operacion(),
                    f.operacionRotulo());
        }
    }

    /**
     * <b>La memoria del inmueble.</b> Un nivel distinto del encargo:
     *
     * <pre>
     *   idEncargo    la identidad tecnica de UN episodio comercial
     *   idPropiedad  la continuidad historica del inmueble
     * </pre>
     *
     * <p>Los bloques de encargo sirven para auditar y negociar. Esto contesta
     * otra pregunta: «¿a cuanto se alquilo la ultima vez?», «¿cuantas veces
     * estuvo en venta?». <b>No fusiona historicos: los agrega para leerlos</b>,
     * y cada cifra sigue apuntando a su {@code idEncargo}.
     */
    public record HistoriaResponse(List<EpisodiosResponse> porOperacion,
                                   List<HitoHistoriaResponse> linea) {
        static HistoriaResponse desde(HistoriaComercial f) {
            return new HistoriaResponse(
                    f.porOperacion().stream().map(EpisodiosResponse::desde).toList(),
                    f.linea().stream().map(HitoHistoriaResponse::desde).toList());
        }
    }

    /** Lo que ha pasado con la propiedad, repartido por proceso. */
    public record ActividadResponse(List<HechoResponse> oportunidades,
                                    List<HechoResponse> visitas,
                                    List<HechoResponse> interacciones,
                                    List<HechoResponse> expedientes,
                                    List<HechoResponse> contratos) {
        static ActividadResponse desde(ActividadPropiedad f) {
            return new ActividadResponse(hechos(f.oportunidades()), hechos(f.visitas()),
                    hechos(f.interacciones()), hechos(f.expedientes()), hechos(f.contratos()));
        }

        private static List<HechoResponse> hechos(List<HechoDeActividad> origen) {
            return origen.stream().map(HechoResponse::desde).toList();
        }
    }

    /**
     * <b>La propiedad leida por el modelo universal, lista para ser leida.</b>
     *
     * <p>Cada codigo viaja con su rotulo al lado. No es cortesia: la
     * alternativa es que cada consumidor --BROX Web y KAIROS-- monte su propia
     * tabla de traduccion, y dos tablas del mismo vocabulario se separan
     * siempre (D-A-1 §6).
     *
     * <p><b>El metraje aparece una sola vez</b>, entre {@code atributos}, con
     * su clave logica {@code metraje_total}. Aqui no hay campo {@code metraje}
     * suelto a proposito: su autoridad fisica es un campo canonico del agregado
     * desde D-E4-3, pero el contrato logico no se movio, y publicarlo ademas
     * por separado obligaria a la ficha a excluirlo de la lista para no
     * ensenarlo dos veces.
     *
     * <p><b>Dos listas de faltantes, dos preguntas.</b> {@code atributosQueFaltan}
     * responde «que impide el ALTA» y lleva las {@code ALT};
     * {@code faltanParaPublicar} responde «que impide PUBLICAR» y lleva las
     * {@code ALT} <b>y</b> las {@code PUB}. Una clave {@code ALT} ausente sale en
     * las dos, porque bloquea las dos cosas -- no es una duplicacion.
     *
     * <p>El nombre es el mismo que en {@link EncargoResponse} a proposito:
     * <b>cada sujeto reporta su propia deuda bajo el mismo nombre</b>, y la ruta
     * desambigua sin necesidad de repetir el sujeto en el campo --
     * {@code propiedad.faltanParaPublicar} frente a
     * {@code propiedad.encargos[i].faltanParaPublicar}.
     *
     * <p>{@code atributosQueFaltan} no es un error: es lo que permite a la
     * ficha avisar de que no se puede publicar todavia --y decirlo con la
     * palabra del catalogo, no con la clave-- y al motor de captura saber que
     * preguntar.
     */
    public record PropiedadResponse(Long id, String codigo, String tipoPropiedad, String tipoRotulo,
                                    String uso, String usoRotulo, String descripcion,
                                    String estadoRegistro, String estadoRegistroRotulo,
                                    String disponibilidadComercial, String disponibilidadRotulo,
                                    UbicacionResponse ubicacion,
                                    List<TitularResponse> titulares,
                                    List<AtributoResponse> atributos,
                                    List<EncargoResponse> encargos,
                                    List<AtributoQueFaltaResponse> atributosQueFaltan,
                                    List<AtributoQueFaltaResponse> faltanParaPublicar,
                                    HistoriaResponse historia,
                                    ActividadResponse actividad,
                                    LocalDateTime fechaRegistro) {

        public static PropiedadResponse desde(FichaPropiedadUniversal f) {
            return new PropiedadResponse(f.id(), f.codigo(), f.tipoPropiedad(), f.tipoRotulo(),
                    f.uso(), f.usoRotulo(), f.descripcion(),
                    f.estadoRegistro(), f.estadoRegistroRotulo(),
                    f.disponibilidadComercial(), f.disponibilidadRotulo(),
                    UbicacionResponse.desde(f.ubicacion()),
                    f.titulares().stream().map(TitularResponse::desde).toList(),
                    f.atributos().stream().map(AtributoResponse::desde).toList(),
                    f.encargos().stream().map(EncargoResponse::desde).toList(),
                    f.atributosQueFaltan().stream().map(AtributoQueFaltaResponse::desde).toList(),
                    f.faltanParaPublicar().stream().map(AtributoQueFaltaResponse::desde).toList(),
                    HistoriaResponse.desde(f.historia()),
                    ActividadResponse.desde(f.actividad()),
                    f.fechaRegistro());
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

    // ------------------------------------------------------------------
    // El listado
    // ------------------------------------------------------------------

    /** Un encargo tal como se ve en una lista: su operacion y lo que se pide. */
    public record EncargoEnListaResponse(String operacion, String estado, BigDecimal importe,
                                         String moneda) {
    }

    /**
     * Una fila del listado universal.
     *
     * <p><b>{@code encargos} es una lista, y ahi esta todo.</b> El listado
     * heredado manda un {@code precioReferencial} suelto que no dice de que
     * operacion es; este manda los encargos vivos, y una propiedad en venta y en
     * alquiler trae dos con su importe cada uno. La etiqueta «Venta + alquiler»
     * la <b>compone el cliente</b> mirando la lista: no viaja, porque no existe.
     */
    public record FilaPropiedadResponse(Long id, String codigo, String tipoPropiedad,
                                        String tipoRotulo, String uso, String direccion,
                                        String distrito, BigDecimal metraje, String estado,
                                        Long idPropietario, String propietarioNombre,
                                        long titulares, List<EncargoEnListaResponse> encargos,
                                        LocalDateTime fechaRegistro) {

        public static FilaPropiedadResponse desde(PropiedadUniversalService.FilaPropiedad fila) {
            return new FilaPropiedadResponse(fila.id(), fila.codigo(), fila.tipoPropiedad(),
                    fila.tipoRotulo(), fila.uso(), fila.direccion(), fila.distrito(),
                    fila.metraje(), fila.estado(),
                    fila.idPropietario(), fila.propietarioNombre(), fila.titulares(),
                    fila.encargos().stream()
                            .map(encargo -> new EncargoEnListaResponse(encargo.operacion(),
                                    encargo.estado(), encargo.importe(), encargo.moneda()))
                            .toList(),
                    fila.fechaRegistro());
        }
    }
}
