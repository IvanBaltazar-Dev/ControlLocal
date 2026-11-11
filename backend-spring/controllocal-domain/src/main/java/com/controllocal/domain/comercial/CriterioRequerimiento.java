package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <b>Lo que el cliente pide sobre una clave gobernada</b> (D-M-3).
 *
 * <h2>Por que el criterio usa el catalogo y no un campo propio</h2>
 * Si la propiedad dice {@code dormitorios} y el requerimiento dice
 * {@code habitaciones}, el matcher necesita una tabla de traduccion — y esa
 * tabla es donde mueren los matchers (decision del modelo universal, §3.4). La
 * clave de un criterio sale del <b>mismo</b> {@code catalogo_atributo} del que
 * salen los atributos de la propiedad, y el trigger
 * {@code tg_criterio_gobernado} lo garantiza tambien por la puerta del SQL.
 *
 * <h2>Que NO es</h2>
 * No es un filtro de busqueda. Un filtro quita filas; un criterio <b>puntua</b>
 * y explica: si es {@link PesoCriterio#INDISPENSABLE} descarta y dice cual, y
 * si es {@link PesoCriterio#DESEABLE} baja el puntaje y aparece en la lista de
 * peros.
 *
 * <h2>El valor vive donde le toca</h2>
 * Un escalar por columna tipada —igual que {@code atributo_propiedad}— y el
 * conjunto de un {@code INCLUYE} en su tabla hija. Guardar «2» en una columna
 * de texto haria que {@code MINIMO} comparara cadenas, y {@code "10" < "9"}.
 */
@Entity
@Table(name = "requerimiento_criterio")
public class CriterioRequerimiento extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_criterio")
    private Long id;

    @Column(name = "id_requerimiento", nullable = false)
    private Long idRequerimiento;

    @Column(name = "version_demanda", nullable = false)
    private long versionDemanda;

    /** Única representación de alternativas y metadatos de las versiones C3. */
    @Column(name = "criterio_json")
    private String criterioJson;

    @Column(name = "rotulo")
    private String rotulo;
    @Column(name = "unidad")
    private String unidad;
    @Column(name = "tipo_dato", length = 30)
    private String tipoDatoObservado;

    /** La clave del catalogo. Existe antes que el criterio que la pide. */
    @Column(name = "clave", nullable = false, length = 60)
    private String clave;

    @Column(name = "operador", length = 1)
    private String operador;

    @Column(name = "peso", length = 1)
    private String peso;

    @Column(name = "valor_texto")
    private String valorTexto;

    @Column(name = "valor_numero", precision = 14, scale = 4)
    private BigDecimal valorNumero;

    /** El techo de un {@code ENTRE}. NULL en los demas operadores. */
    @Column(name = "valor_numero_hasta", precision = 14, scale = 4)
    private BigDecimal valorNumeroHasta;

    /** Moneda del valor cuando la clave del catalogo es un IMPORTE. */
    @Column(name = "valor_moneda", length = 3)
    private String valorMoneda;

    @Column(name = "valor_booleano")
    private Boolean valorBooleano;

    @Column(name = "valor_fecha")
    private LocalDate valorFecha;

    @Column(name = "valor_fecha_hasta")
    private LocalDate valorFechaHasta;

    /**
     * Los valores de un {@code INCLUYE}. Coleccion de VALORES: un valor pedido
     * no tiene identidad fuera de su criterio.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "requerimiento_criterio_valor",
            joinColumns = @JoinColumn(name = "id_criterio"))
    private Set<ValorDeCriterio> valores = new LinkedHashSet<>();

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    public Long getId() {
        return id;
    }

    public long getVersionDemanda() { return versionDemanda; }
    public void setVersionDemanda(long valor) { versionDemanda = valor; }
    public String getCriterioJson() { return criterioJson; }
    public void setCriterioJson(String valor) { criterioJson = valor; }
    public String getRotulo() { return rotulo; }
    public void setRotulo(String valor) { rotulo = valor; }
    public String getUnidad() { return unidad; }
    public void setUnidad(String valor) { unidad = valor; }
    public String getTipoDatoObservado() { return tipoDatoObservado; }
    public void setTipoDatoObservado(String valor) { tipoDatoObservado = valor; }

    public Long getIdRequerimiento() {
        return idRequerimiento;
    }

    public void setIdRequerimiento(Long idRequerimiento) {
        this.idRequerimiento = idRequerimiento;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public String getOperador() {
        return operador;
    }

    /** Rechaza el nulo en vez de rellenarlo: no hay operador por defecto. */
    public void setOperador(OperadorCriterio operador) {
        if (operador == null) {
            throw new IllegalArgumentException(
                    "Un criterio sin operador no se puede evaluar: declara como se compara.");
        }
        this.operador = operador.codigo();
    }

    @Transient
    public OperadorCriterio operador() {
        return OperadorCriterio.desde(operador);
    }

    public String getPeso() {
        return peso;
    }

    public void setPeso(PesoCriterio peso) {
        if (peso == null) {
            throw new IllegalArgumentException(
                    "Un criterio sin peso no se puede evaluar: uno descarta la oferta y el otro "
                            + "solo baja el puntaje.");
        }
        this.peso = peso.codigo();
    }

    @Transient
    public PesoCriterio peso() {
        return PesoCriterio.desde(peso);
    }

    @Transient
    public boolean esIndispensable() {
        return peso().descarta();
    }

    public String getValorTexto() {
        return valorTexto;
    }

    public void setValorTexto(String valorTexto) {
        this.valorTexto = valorTexto;
    }

    public BigDecimal getValorNumero() {
        return valorNumero;
    }

    public void setValorNumero(BigDecimal valorNumero) {
        this.valorNumero = valorNumero;
    }

    public BigDecimal getValorNumeroHasta() {
        return valorNumeroHasta;
    }

    public void setValorNumeroHasta(BigDecimal valorNumeroHasta) {
        this.valorNumeroHasta = valorNumeroHasta;
    }

    public String getValorMoneda() {
        return valorMoneda;
    }

    public void setValorMoneda(String valorMoneda) {
        this.valorMoneda = valorMoneda;
    }

    public Boolean getValorBooleano() {
        return valorBooleano;
    }

    public void setValorBooleano(Boolean valorBooleano) {
        this.valorBooleano = valorBooleano;
    }

    public LocalDate getValorFecha() {
        return valorFecha;
    }

    public void setValorFecha(LocalDate valorFecha) {
        this.valorFecha = valorFecha;
    }

    public LocalDate getValorFechaHasta() {
        return valorFechaHasta;
    }

    public void setValorFechaHasta(LocalDate valorFechaHasta) {
        this.valorFechaHasta = valorFechaHasta;
    }

    public Set<ValorDeCriterio> getValores() {
        return valores;
    }

    public void setValores(Set<ValorDeCriterio> valores) {
        this.valores = valores == null ? new LinkedHashSet<>() : valores;
    }

    /** Los valores pedidos, en texto y en su orden. Vacio si no es un INCLUYE. */
    @Transient
    public List<String> valoresPedidos() {
        List<String> pedidos = new ArrayList<>();
        for (ValorDeCriterio valor : valores) {
            if (valor.getValor() != null) {
                pedidos.add(valor.getValor());
            }
        }
        return List.copyOf(pedidos);
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
