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
import java.util.Set;

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

    public static final String TEXTO = "TEXTO";
    public static final String ENTERO = "ENTERO";
    public static final String DECIMAL = "DECIMAL";
    public static final String BOOLEANO = "BOOLEANO";
    public static final String LISTA = "LISTA";
    public static final Set<String> TIPOS_DATO = Set.of(TEXTO, ENTERO, DECIMAL, BOOLEANO, LISTA);

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

    @Column(name = "tipo_dato", nullable = false, length = 10)
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

    /** ¿Este atributo tiene sentido para una propiedad de este tipo? */
    @Transient
    public boolean aplicaA(String tipoPropiedad) {
        if (aplicaTodos) {
            return true;
        }
        return aplicaciones.stream().anyMatch(a -> a.getTipoPropiedad().equals(tipoPropiedad));
    }

    /** ¿Y es obligatorio para ese tipo? Es lo que decide si el alta puede cerrarse. */
    @Transient
    public boolean esRequeridoPara(String tipoPropiedad) {
        return aplicaciones.stream()
                .filter(a -> a.getTipoPropiedad().equals(tipoPropiedad))
                .anyMatch(AplicacionAtributo::isRequerido);
    }

    @Transient
    public boolean esNumerico() {
        return ENTERO.equals(tipoDato) || DECIMAL.equals(tipoDato);
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

    public Set<AplicacionAtributo> getAplicaciones() {
        return aplicaciones;
    }

    public void setAplicaciones(Set<AplicacionAtributo> aplicaciones) {
        this.aplicaciones = aplicaciones;
    }
}
