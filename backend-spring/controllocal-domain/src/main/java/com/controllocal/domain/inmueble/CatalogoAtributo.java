package com.controllocal.domain.inmueble;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Que caracteristicas puede tener un inmueble, con su tipo de dato, su unidad y
 * a que tipos de propiedad aplica (D-E4-1 M2, V48).
 *
 * <p><b>Es lo que separa un modelo dinamico de un saco de claves sueltas.</b>
 * Sin catalogo, cada quien inventa su clave y ni la busqueda ni el matcher
 * pueden comparar dos propiedades porque una dice {@code dormitorios} y otra
 * {@code habitaciones}. Aqui la clave existe antes que el valor.
 *
 * <p>De este catalogo DERIVA el motor de registro las preguntas de cada tipo
 * (D-E4-2): anadir "Almacen" no anade un formulario, anade filas aqui.
 *
 * <p><b>Por que NO hereda de EntidadDeOrganizacion.</b> Es un catalogo hibrido:
 * las filas del sistema ({@code organizacion_id} NULL) las comparten todos los
 * tenants y una organizacion no puede borrarlas ni redefinir su tipo; encima de
 * ellas, cada organizacion puede anadir las suyas. El discriminador es por tanto
 * ANULABLE a proposito, igual que en {@code IntentoAcceso}, y esta entidad esta
 * declarada en la lista de globales de {@code ArquitecturaTenancyTest} con esa
 * razon.
 */
@Entity
@Table(name = "catalogo_atributo")
public class CatalogoAtributo {

    /**
     * Los codigos de tipo de dato, derivados del enum y no escritos otra vez.
     *
     * <p>Hasta el Corte 0B esto era una lista de cinco constantes de texto
     * mantenida a mano, y ninguno de los tres {@code switch} que la consumian
     * era exhaustivo. Ahora la verdad esta en {@link TipoDato} y esto es su
     * proyeccion: anadir un noveno tipo no puede dejar esta lista corta, porque
     * ya no es una lista.
     */
    public static final Set<String> TIPOS_DATO = Arrays.stream(TipoDato.values())
            .map(TipoDato::codigo)
            .collect(Collectors.toUnmodifiableSet());

    /** El valor vive en {@code atributo_propiedad}. Es el caso normal. */
    public static final String ATRIBUTO = "ATRIBUTO";
    /** El valor vive en su campo canonico del agregado. */
    public static final String ESTRUCTURAL = "ESTRUCTURAL";

    /** Los conceptos estructurales del dominio. Crece con la clasificacion (D-E4-3). */
    public static final String CAMPO_METRAJE = "METRAJE";
    /**
     * En que piso esta la unidad. Estructural desde V67.
     *
     * <p>Lo tenia dos veces: el catalogo publicaba {@code piso} como atributo
     * gobernado —lo creo V48 al llevarse las columnas de subtipo— y el guion de
     * captura publicaba {@code pisoUnidad}, que escribia {@code propiedad.piso}.
     * Dos claves, un concepto y dos sitios donde guardarlo. Nadie lo vio hasta
     * que el alta universal las pinto juntas y pregunto <b>«Piso» dos veces</b>:
     * la pantalla vieja solo dibujaba una de las dos.
     */
    public static final String CAMPO_PISO = "PISO";

    /**
     * <b>El numero de partida en el registro de predios</b>. Estructural desde
     * V79 (Corte 2).
     *
     * <p>Es identidad, no descripcion: no depende del tipo de inmueble --toda
     * propiedad inscribible tiene partida--, sobrevive a cualquier encargo y es
     * lo que permite afirmar que dos fichas hablan del mismo activo. Ese es el
     * criterio ESTRUCTURAL de D-E4-3, el mismo por el que {@code metraje} lo es
     * y {@code zonificacion} no.
     */
    public static final String CAMPO_PARTIDA_REGISTRAL = "PARTIDA_REGISTRAL";

    /**
     * <b>La oficina registral donde esta inscrita esa partida</b> (V79).
     *
     * <p>Viaja con la partida porque la numeracion se repite entre oficinas: el
     * numero solo no identifica nada. Es la primera clave ESTRUCTURAL de tipo
     * LISTA, y por eso V79 anadio la comprobacion de vocabulario del lado
     * estructural -- la de V72 vive dentro del trigger de
     * {@code atributo_propiedad}, por donde un valor estructural no pasa.
     */
    public static final String CAMPO_OFICINA_REGISTRAL = "OFICINA_REGISTRAL";

    // ------------------------------------------------------------------
    // Las claves del sistema que D-E4-3 clasifico, con nombre.
    //
    // Nombrarlas NO es la matriz prohibida. Lo prohibido es decidir DONDE se
    // guarda una clave a partir de su nombre; un consumidor con un campo
    // llamado `ambientes` no tiene otra forma de pedirlo que por su nombre.
    // Estan aqui, y no sueltas en cada servicio, para que la que se renombre
    // manana se renombre una vez.
    // ------------------------------------------------------------------

    public static final String CLAVE_METRAJE_TOTAL = "metraje_total";
    public static final String CLAVE_AMBIENTES = "ambientes";
    public static final String CLAVE_FRENTE = "frente";
    public static final String CLAVE_ZONIFICACION = "zonificacion";
    public static final String CLAVE_CUOTA_MANTENIMIENTO = "cuota_mantenimiento";
    /** Ojo: la clave del catalogo es esta; la columna espejo se llamaba `numero_estacionamientos`. */
    public static final String CLAVE_ESTACIONAMIENTOS = "estacionamientos";
    public static final String CLAVE_ANTIGUEDAD_ANIOS = "antiguedad_anios";
    // Los tres que dejaron de tener tabla espejo en V71.
    public static final String CLAVE_RUBRO_PERMITIDO = "rubro_permitido";
    public static final String CLAVE_APTO_LICENCIA = "apto_licencia_funcionamiento";
    public static final String CLAVE_CARGA_ELECTRICA_KW = "carga_electrica_kw";
    // Las seis de la identidad registral (V79). Las dos primeras declaran su
    // autoridad en un campo canonico del agregado; las otras cuatro son
    // atributos gobernados, porque describen SITUACION y no identidad.
    public static final String CLAVE_PARTIDA_REGISTRAL = "partida_registral";
    public static final String CLAVE_OFICINA_REGISTRAL = "oficina_registral";
    public static final String CLAVE_INDEPENDIZADO = "independizado";
    public static final String CLAVE_CARGAS_GRAVAMENES = "cargas_gravamenes";
    public static final String CLAVE_AREA_SEGUN_PARTIDA = "area_segun_partida";
    public static final String CLAVE_DECLARATORIA_FABRICA = "declaratoria_fabrica";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_catalogo_atributo")
    private Long id;

    /** NULL = atributo del sistema, comun a todas las organizaciones. */
    @Column(name = "organizacion_id")
    private Long organizacionId;

    @Column(name = "clave", nullable = false, length = 60)
    private String clave;

    @Column(name = "rotulo", nullable = false, length = 120)
    private String rotulo;

    @Column(name = "tipo_dato", nullable = false, length = 20)
    private String tipoDato;

    /** m2, kW, anios, moneda... NULL cuando el numero no tiene unidad. */
    @Column(name = "unidad", length = 20)
    private String unidad;

    /** true = aplica a cualquier tipo; si no, manda {@link #aplicaciones}. */
    @Column(name = "aplica_todos", nullable = false)
    private boolean aplicaTodos;

    @Column(name = "del_sistema", nullable = false)
    private boolean delSistema;

    @Column(name = "orden", nullable = false)
    private int orden = 100;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    /**
     * <b>De quien es esta clave</b> (Corte 0C, V73). Es la pregunta anterior a
     * la autoridad: primero de quien, luego donde.
     *
     * <p>Existe porque el catalogo presuponia una sola respuesta --todo era de
     * la Propiedad-- y eso hace irrepresentable la condicion negociada. Con un
     * solo sujeto, `se_ofrece_amoblado` tiene un valor por inmueble; el segundo
     * alquiler pisa al primero y nadie se entera.
     *
     * <p>De el se deriva TODO lo demas: donde se declara la aplicabilidad, en
     * que tabla vive el valor y que trigger lo vigila.
     */
    @Column(name = "sujeto", nullable = false, length = 10)
    private String sujeto = Sujeto.PROPIEDAD.codigo();

    /**
     * <b>Donde vive el valor de esta clave</b> (D-E4-3, V60). Es la respuesta a
     * "¿quien es la autoridad?", y solo puede haber una.
     *
     * <p>Existe porque siete conceptos vivian a la vez como columna de
     * {@code propiedad} y como fila de {@code atributo_propiedad}, y solo uno
     * se mantenia sincronizado: los demas se guardaban en un sitio y se leian
     * de otro.
     */
    @Column(name = "destino", nullable = false, length = 12)
    private String destino = ATRIBUTO;

    /**
     * El CONCEPTO del dominio al que corresponde cuando {@link #destino} es
     * {@link #ESTRUCTURAL}: {@code METRAJE}. NULL en el caso normal.
     *
     * <p><b>No es el nombre de una columna.</b> Guardar aqui
     * {@code propiedad.metraje} pondria la topologia fisica de PostgreSQL
     * dentro de una fila de catalogo, y renombrar la columna obligaria a
     * migrar datos de configuracion. La persistencia sabe como guardar
     * {@code METRAJE}; el catalogo solo dice que es eso.
     *
     * <p>Y es lo que impide que el codigo acabe con un
     * {@code si clave == "metraje_total"}: quien escribe pregunta por el
     * concepto, no por la clave.
     */
    @Column(name = "campo_estructural", length = 40)
    private String campoEstructural;

    /**
     * El minimo admisible del valor numerico; NULL = sin minimo (V62).
     *
     * <p>Hereda los CHECK de rango que V4 tenia sobre las columnas espejo. Vive
     * en el CATALOGO y no en el codigo por la misma razon que {@link #tipoDato}:
     * la clave la puede anadir un tenant, y su rango es parte de lo que la
     * define. Un minimo escrito en un servicio seria una regla sin dueno, y en
     * un formulario, una segunda.
     */
    @Column(name = "valor_minimo", precision = 14, scale = 4)
    private java.math.BigDecimal valorMinimo;

    /** El maximo admisible del valor numerico; NULL = sin techo (V72). */
    @Column(name = "valor_maximo", precision = 14, scale = 4)
    private java.math.BigDecimal valorMaximo;

    /**
     * Cuanto mide como mucho un valor de texto; NULL = sin techo (V72).
     *
     * <p>Repone la garantia que se perdio al retirar {@code detalle_local_comercial}:
     * el rubro tenia {@code VARCHAR(120)} y {@code valor_texto} es TEXT.
     */
    @Column(name = "longitud_maxima")
    private Integer longitudMaxima;

    /** Para que sirve este dato, en palabras del corredor (V72). */
    @Column(name = "ayuda")
    private String ayuda;

    /**
     * La agrupacion TEMATICA que declara la clave: "edificio", "instalaciones"
     * (V72). Junto al tipo de control, es la <b>unica</b> ramificacion que la
     * frontera D-A-1 le permite a una interfaz.
     *
     * <p>No se confunde con la clasificacion estructural del motor de captura
     * --de que lista salio la pregunta--, que viaja por el cable como
     * {@code seccion}. Eran dos conceptos con el mismo nombre y el cliente
     * recibia los dos en el mismo objeto.
     */
    @Column(name = "familia", length = 30)
    private String familia;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    /**
     * A que tipos de propiedad aplica, y en cuales es obligatorio. Coleccion de
     * VALORES y no entidad propia: una aplicacion no tiene identidad ni vida
     * fuera de su atributo, y asi la clave compuesta de la tabla no obliga a
     * introducir la primera {@code @IdClass} del proyecto.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "catalogo_atributo_tipo",
            joinColumns = @JoinColumn(name = "id_catalogo_atributo"))
    private Set<AplicacionAtributo> aplicaciones = new LinkedHashSet<>();

    /**
     * A que <b>(tipo, operacion)</b> aplica cuando el sujeto es ENCARGO, y
     * cuanto hace falta ahi (V73). Vacia cuando el sujeto es PROPIEDAD.
     *
     * <p>Es la gemela de {@link #aplicaciones} y las dos son excluyentes: una
     * clave declara su aplicabilidad <b>donde manda su sujeto</b>, nunca en las
     * dos tablas. Una clave fisica con aplicabilidad por operacion diria que la
     * cosa cambia segun se venda o se alquile; una comercial con aplicabilidad
     * por tipo diria que la condicion es un hecho del inmueble. La invariante la
     * vigila {@code SujetoDelDatoIntegrationTest}.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "catalogo_atributo_operacion",
            joinColumns = @JoinColumn(name = "id_catalogo_atributo"))
    private Set<AplicacionPorOperacion> aplicacionesOperacion = new LinkedHashSet<>();

    /**
     * El vocabulario de una LISTA o una LISTA_MULTIPLE (V72). Vacio en los
     * demas tipos.
     *
     * <p>Misma forma que {@link #aplicaciones} y por la misma razon: una opcion
     * no tiene vida fuera de su atributo. Se hidrata con el catalogo, asi que
     * publicar las opciones no anade ninguna consulta por clave.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "catalogo_atributo_opcion",
            joinColumns = @JoinColumn(name = "id_catalogo_atributo"))
    private Set<OpcionDeAtributo> opciones = new LinkedHashSet<>();

    /** ¿Este atributo tiene sentido para una propiedad de este tipo? */
    @Transient
    public boolean aplicaA(String tipoPropiedad) {
        if (aplicaTodos) {
            return true;
        }
        return aplicaciones.stream().anyMatch(a -> a.getTipoPropiedad().equals(tipoPropiedad));
    }

    /**
     * ¿Esta clave tiene sentido para <b>esta comercializacion</b>? (V73)
     *
     * <p>Se pregunta con las dos dimensiones porque la aplicabilidad comercial
     * depende de las dos: `garantia_meses` aplica al alquiler de un
     * departamento y no a su venta; `partida_registral` es al reves.
     */
    @Transient
    public boolean aplicaA(String tipoPropiedad, String tipoOperacion) {
        if (aplicaTodos) {
            return true;
        }
        return aplicacionesOperacion.stream()
                .anyMatch(a -> a.getTipoPropiedad().equals(tipoPropiedad)
                        && a.getTipoOperacion().equals(tipoOperacion));
    }

    /**
     * Cuanto hace falta el dato para ese (tipo, operacion). OPC cuando no se
     * declaro nada: una exigencia sin declarar no puede bloquear nada.
     */
    @Transient
    public Exigencia exigenciaPara(String tipoPropiedad, String tipoOperacion) {
        return aplicacionesOperacion.stream()
                .filter(a -> a.getTipoPropiedad().equals(tipoPropiedad)
                        && a.getTipoOperacion().equals(tipoOperacion))
                .map(AplicacionPorOperacion::exigenciaTipada)
                .findFirst()
                .orElse(Exigencia.OPC);
    }

    /** ¿Impide publicar ESTE encargo? ALT y PUB, igual que en la propiedad. */
    @Transient
    public boolean bloqueaPublicacionPara(String tipoPropiedad, String tipoOperacion) {
        return exigenciaPara(tipoPropiedad, tipoOperacion).bloqueaPublicacion();
    }

    /**
     * <b>De quien es el dato.</b> Quien escribe, lee, borra o cuenta faltantes
     * pregunta esto ANTES que nada: el sujeto elige el mecanismo entero.
     */
    @Transient
    public Sujeto sujeto() {
        return Sujeto.desde(sujeto);
    }

    /** Atajo de {@code sujeto() == ENCARGO}, que es la bifurcacion real. */
    @Transient
    public boolean esDeEncargo() {
        return sujeto().esDeEncargo();
    }

    /**
     * <b>Cuanto hace falta para ese tipo.</b> Tres niveles desde V72, no un
     * booleano: ALT bloquea el alta, PUB bloquea publicar, OPC no bloquea.
     *
     * <p>Devuelve OPC cuando la clave no declara nada para ese tipo, que es lo
     * unico honesto: una exigencia que no se declaro no puede bloquear nada.
     */
    @Transient
    public Exigencia exigenciaPara(String tipoPropiedad) {
        return aplicaciones.stream()
                .filter(a -> a.getTipoPropiedad().equals(tipoPropiedad))
                .map(AplicacionAtributo::exigenciaTipada)
                .findFirst()
                .orElse(Exigencia.OPC);
    }

    /**
     * ¿Bloquea el alta para ese tipo? Solo ALT.
     *
     * <p>Se pregunta asi y NO comparando contra un nivel: basta que un consumidor
     * lea "lo que no sea OPC" para que el alta empiece a exigir de golpe todo lo
     * que solo debia exigir el anuncio.
     */
    @Transient
    public boolean esRequeridoPara(String tipoPropiedad) {
        return exigenciaPara(tipoPropiedad).bloqueaAlta();
    }

    /** ¿Impide publicar para ese tipo? ALT y PUB. */
    @Transient
    public boolean bloqueaPublicacionPara(String tipoPropiedad) {
        return exigenciaPara(tipoPropiedad).bloqueaPublicacion();
    }

    /**
     * <b>El tipo de dato, tipado.</b> Es por donde debe preguntarse a partir del
     * Corte 0B: devuelve un {@link TipoDato}, asi que un {@code switch} sobre el
     * lo comprueba el compilador y anadir un noveno tipo deja de poder colarse
     * por un {@code default} permisivo.
     */
    @Transient
    public TipoDato tipo() {
        return TipoDato.desde(tipoDato);
    }

    @Transient
    public boolean esNumerico() {
        return tipo().esNumerico();
    }

    /**
     * ¿Su valor se guarda como atributo, o en un campo canonico del agregado?
     * Quien escribe pregunta esto — nunca por la clave.
     */
    @Transient
    public boolean esEstructural() {
        return ESTRUCTURAL.equals(destino);
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino == null ? ATRIBUTO : destino;
    }

    public java.math.BigDecimal getValorMinimo() {
        return valorMinimo;
    }

    public java.math.BigDecimal getValorMaximo() {
        return valorMaximo;
    }

    public void setValorMaximo(java.math.BigDecimal valorMaximo) {
        this.valorMaximo = valorMaximo;
    }

    public Integer getLongitudMaxima() {
        return longitudMaxima;
    }

    public void setLongitudMaxima(Integer longitudMaxima) {
        this.longitudMaxima = longitudMaxima;
    }

    public String getAyuda() {
        return ayuda;
    }

    public void setAyuda(String ayuda) {
        this.ayuda = ayuda;
    }

    public String getFamilia() {
        return familia;
    }

    public void setFamilia(String familia) {
        this.familia = familia;
    }

    public String getCampoEstructural() {
        return campoEstructural;
    }

    public void setCampoEstructural(String campoEstructural) {
        this.campoEstructural = campoEstructural;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizacionId() {
        return organizacionId;
    }

    public void setOrganizacionId(Long organizacionId) {
        this.organizacionId = organizacionId;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public String getRotulo() {
        return rotulo;
    }

    public void setRotulo(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getTipoDato() {
        return tipoDato;
    }

    public void setTipoDato(String tipoDato) {
        this.tipoDato = tipoDato;
    }

    public String getUnidad() {
        return unidad;
    }

    public void setUnidad(String unidad) {
        this.unidad = unidad;
    }

    public boolean isAplicaTodos() {
        return aplicaTodos;
    }

    public void setAplicaTodos(boolean aplicaTodos) {
        this.aplicaTodos = aplicaTodos;
    }

    public boolean isDelSistema() {
        return delSistema;
    }

    public void setDelSistema(boolean delSistema) {
        this.delSistema = delSistema;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    /** El vocabulario declarado, ya ordenado y sin las opciones retiradas. */
    @Transient
    public java.util.List<OpcionDeAtributo> opcionesVigentes() {
        return opciones.stream()
                .filter(OpcionDeAtributo::isActivo)
                .sorted(java.util.Comparator.comparingInt(OpcionDeAtributo::getOrden)
                        .thenComparing(OpcionDeAtributo::getValor))
                .toList();
    }

    public Set<OpcionDeAtributo> getOpciones() {
        return opciones;
    }

    public void setOpciones(Set<OpcionDeAtributo> opciones) {
        this.opciones = opciones;
    }

    public String getSujeto() {
        return sujeto;
    }

    public void setSujeto(Sujeto sujeto) {
        this.sujeto = sujeto.codigo();
    }

    public Set<AplicacionPorOperacion> getAplicacionesOperacion() {
        return aplicacionesOperacion;
    }

    public void setAplicacionesOperacion(Set<AplicacionPorOperacion> aplicacionesOperacion) {
        this.aplicacionesOperacion = aplicacionesOperacion;
    }

    public Set<AplicacionAtributo> getAplicaciones() {
        return aplicaciones;
    }

    public void setAplicaciones(Set<AplicacionAtributo> aplicaciones) {
        this.aplicaciones = aplicaciones;
    }
}
