package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Cómo se llegó a la meta que hay: quién la fijó o la propuso, de cuánto a
 * cuánto, cuándo y <b>por qué</b> (V66).
 *
 * <h2>Por qué no basta con un {@code fecha_actualizacion}</h2>
 *
 * <p>V65 dejó una fila mutable por agente, KPI y mes. Un {@code PUT} la
 * sobrescribía, y dentro de tres meses la base diría que la meta <b>siempre</b>
 * fue 6: el gráfico de cumplimiento mentiría sin que nadie pudiera notarlo. Es
 * el mismo defecto que E0 corrigió con los precios —un campo que se sobrescribe
 * frente a una serie—, y se arregla igual.
 *
 * <h2>La política que hace cumplir</h2>
 *
 * <p>Un agente <b>no baja su meta porque va perdiendo</b>: eso convertiría el
 * indicador en algo manipulable —voy al 60 %, bajo la meta y vuelvo a verde—.
 * Pero una meta inmutable tampoco sirve: hay vacaciones, altas a mitad de mes,
 * cambios de cartera, bajas.
 *
 * <ul>
 *   <li><b>El agente propone</b>, con motivo obligatorio. Su revisión nace
 *       {@link #ESTADO_EN_ESPERA} y no toca la meta.</li>
 *   <li><b>El broker decide</b>: acepta, rechaza, o fija otro valor. Lo que él
 *       escribe se aplica al escribirlo.</li>
 * </ul>
 *
 * <p>Las dos cosas viven en <b>la misma serie</b>, porque las dos son revisiones
 * de la meta. Separarlas daría dos historias que habría que cruzar para
 * reconstruir una.
 *
 * <h2>Append-only</h2>
 *
 * <p>Nada de aquí se edita nunca, salvo el bloque de decisión —{@code decisor},
 * {@code fechaDecision}, {@code motivoDecision}— que se rellena una sola vez al
 * resolver una propuesta. Corregir una revisión pasada sería volver a poder
 * reescribir la historia, que es justo lo que esta tabla vino a impedir.
 */
@Entity
@Table(name = "meta_revision")
public class MetaRevision extends EntidadDeOrganizacion {

    /** La fija el broker: se aplica en el mismo acto de escribirla. */
    public static final String ORIGEN_BROKER = "B";
    /** La propone el agente: nace en espera y no toca la meta vigente. */
    public static final String ORIGEN_PROPUESTA = "P";
    public static final Set<String> ORIGENES = Set.of(ORIGEN_BROKER, ORIGEN_PROPUESTA);

    public static final String ESTADO_APLICADA = "A";
    public static final String ESTADO_EN_ESPERA = "E";
    public static final String ESTADO_RECHAZADA = "R";
    public static final Set<String> ESTADOS =
            Set.of(ESTADO_APLICADA, ESTADO_EN_ESPERA, ESTADO_RECHAZADA);

    /**
     * Longitud mínima del motivo.
     *
     * <p>Reutiliza el criterio de {@code PoliticaComercial.MOTIVO_REASIGNACION}
     * —también son 10— pero se escribe aquí porque el dominio no puede depender
     * de la capa de servicio. El CHECK de la tabla lo exige igual.
     */
    public static final int MOTIVO_MINIMO = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_revision")
    private Long id;

    @Column(name = "id_rol_agente", nullable = false)
    private Long idRolAgente;

    @Column(name = "kpi", nullable = false, length = 1)
    private String kpi;

    @Column(name = "anio", nullable = false)
    private int anio;

    @Column(name = "mes", nullable = false)
    private int mes;

    @Column(name = "origen", nullable = false, length = 1)
    private String origen;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    /** {@code null} la primera vez que se fija: no había de dónde venir. */
    @Column(name = "valor_anterior")
    private Integer valorAnterior;

    @Column(name = "valor_propuesto", nullable = false)
    private int valorPropuesto;

    @Column(name = "motivo", nullable = false, length = 300)
    private String motivo;

    @Column(name = "id_rol_autor", nullable = false)
    private Long idRolAutor;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "id_rol_decisor")
    private Long idRolDecisor;

    @Column(name = "fecha_decision")
    private OffsetDateTime fechaDecision;

    @Column(name = "motivo_decision", length = 300)
    private String motivoDecision;

    /**
     * Lo mismo que exigen los CHECK, exigido en Java para que el error diga qué
     * pasa antes de llegar a PostgreSQL.
     */
    @PrePersist
    void exigirCoherencia() {
        if (!ORIGENES.contains(origen)) {
            throw new IllegalStateException("Origen de revision desconocido: '" + origen + "'");
        }
        if (!ESTADOS.contains(estado)) {
            throw new IllegalStateException("Estado de revision desconocido: '" + estado + "'");
        }
        if (ORIGEN_BROKER.equals(origen) && !ESTADO_APLICADA.equals(estado)) {
            throw new IllegalStateException(
                    "Lo que fija el broker se aplica al escribirlo: no espera su propia decision.");
        }
        if (motivo == null || motivo.trim().length() < MOTIVO_MINIMO) {
            throw new IllegalStateException(
                    "Explica el cambio de meta con al menos " + MOTIVO_MINIMO + " caracteres: es "
                            + "lo unico que quedara para entenderlo dentro de seis meses.");
        }
        if (valorPropuesto < 0) {
            throw new IllegalStateException("Una meta no puede ser negativa: " + valorPropuesto);
        }
        if (fechaCreacion == null) {
            fechaCreacion = OffsetDateTime.now();
        }
    }

    /** Si todavía espera que el broker decida. */
    public boolean enEspera() {
        return ESTADO_EN_ESPERA.equals(estado);
    }

    /**
     * Resuelve una propuesta. Es la <b>única</b> mutación admitida sobre una
     * revisión ya escrita, y solo desde {@link #ESTADO_EN_ESPERA}.
     */
    public void resolver(String estadoFinal, Long idRolDecisor, String motivoDecision) {
        if (!enEspera()) {
            throw new IllegalStateException(
                    "Esta revision ya se resolvio: reabrirla seria reescribir la historia.");
        }
        if (!ESTADO_APLICADA.equals(estadoFinal) && !ESTADO_RECHAZADA.equals(estadoFinal)) {
            throw new IllegalStateException("Una propuesta se acepta o se rechaza: " + estadoFinal);
        }
        this.estado = estadoFinal;
        this.idRolDecisor = idRolDecisor;
        this.fechaDecision = OffsetDateTime.now();
        this.motivoDecision = motivoDecision;
    }

    public Long getId() {
        return id;
    }

    public Long getIdRolAgente() {
        return idRolAgente;
    }

    public void setIdRolAgente(Long idRolAgente) {
        this.idRolAgente = idRolAgente;
    }

    public String getKpi() {
        return kpi;
    }

    public void setKpi(String kpi) {
        this.kpi = kpi;
    }

    public int getAnio() {
        return anio;
    }

    public void setAnio(int anio) {
        this.anio = anio;
    }

    public int getMes() {
        return mes;
    }

    public void setMes(int mes) {
        this.mes = mes;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Integer getValorAnterior() {
        return valorAnterior;
    }

    public void setValorAnterior(Integer valorAnterior) {
        this.valorAnterior = valorAnterior;
    }

    public int getValorPropuesto() {
        return valorPropuesto;
    }

    public void setValorPropuesto(int valorPropuesto) {
        this.valorPropuesto = valorPropuesto;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Long getIdRolAutor() {
        return idRolAutor;
    }

    public void setIdRolAutor(Long idRolAutor) {
        this.idRolAutor = idRolAutor;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public Long getIdRolDecisor() {
        return idRolDecisor;
    }

    public OffsetDateTime getFechaDecision() {
        return fechaDecision;
    }

    public String getMotivoDecision() {
        return motivoDecision;
    }
}
