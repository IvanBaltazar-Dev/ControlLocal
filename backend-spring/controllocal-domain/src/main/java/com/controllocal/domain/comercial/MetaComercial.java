package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * La meta mensual de un agente para uno de los cuatro KPI canonicos (V65).
 *
 * <h2>La meta del equipo no vive aqui</h2>
 *
 * <p>Y no es un olvido. La meta de un equipo <b>es la suma</b> de las de sus
 * agentes (D-E2-2 §5). Guardarla ademas como fila editable crearia dos verdades
 * que divergen en cuanto alguien edite una sola: el broker abriria «Equipo 56» y
 * encontraria agentes que suman 48, sin forma de saber cual de los dos numeros
 * esta mal. Aqui la suma no puede contradecir a sus sumandos porque no existe
 * como dato.
 *
 * <p>La contrapartida, y es deliberada: si falta la meta de un agente del
 * alcance, el total del equipo <b>no se completa con lo que hay</b>. Se declara
 * cobertura incompleta y el ritmo queda sin base. Una meta parcial produce
 * siempre una brecha a favor, que es la peor clase de error en un tablero de
 * control.
 *
 * <h2>Nadie tiene meta por defecto</h2>
 *
 * <p>La tabla nace vacia. Un agente sin meta no tiene meta cero: no tiene meta,
 * y el sistema lo dice. Es la misma regla que E2.0 fijo para la conversion sin
 * muestra y la que impide que un tablero recien instalado muestre a todo el
 * equipo en rojo contra un objetivo que nadie fijo.
 */
@Entity
@Table(name = "meta_comercial")
public class MetaComercial extends EntidadDeOrganizacion {

    /**
     * Codigos unitarios de los cuatro KPI canonicos. El vocabulario visible vive
     * en {@code KpiCanonico}; aqui esta lo que persiste, que es lo que el CHECK
     * de la tabla acota.
     */
    public static final String KPI_PROPIETARIOS_CONTACTADOS = "C";
    public static final String KPI_PROPIEDADES_CAPTADAS = "P";
    public static final String KPI_SOLICITUDES_INGRESADAS = "S";
    public static final String KPI_CONTRATOS_FIRMADOS = "F";
    public static final Set<String> KPIS = Set.of(
            KPI_PROPIETARIOS_CONTACTADOS, KPI_PROPIEDADES_CAPTADAS,
            KPI_SOLICITUDES_INGRESADAS, KPI_CONTRATOS_FIRMADOS);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_meta")
    private Long id;

    /** El agente al que se le pide. Nunca un broker: el broker no produce (D-E2-2 §4). */
    @Column(name = "id_rol_agente", nullable = false)
    private Long idRolAgente;

    @Column(name = "kpi", nullable = false, length = 1)
    private String kpi;

    @Column(name = "anio", nullable = false)
    private int anio;

    @Column(name = "mes", nullable = false)
    private int mes;

    /** Cero es legitimo: «este mes no se te pide ese resultado». Negativo no. */
    @Column(name = "valor", nullable = false)
    private int valor;

    /** Quien la fijo. Nullable para las cargadas por proceso y no por una persona. */
    @Column(name = "id_rol_autor")
    private Long idRolAutor;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    /**
     * Lo mismo que exige el CHECK, exigido en Java para que el error diga que
     * pasa antes de llegar a PostgreSQL.
     */
    @PrePersist
    @PreUpdate
    void exigirCoherencia() {
        if (kpi == null || !KPIS.contains(kpi)) {
            throw new IllegalStateException(
                    "Meta con KPI desconocido: '" + kpi + "'. Los canonicos son " + KPIS
                            + " y son cuatro: un quinto se decide en D-E2-2, no se anade aqui.");
        }
        if (mes < 1 || mes > 12) {
            throw new IllegalStateException("Meta con mes fuera de rango: " + mes);
        }
        if (valor < 0) {
            throw new IllegalStateException(
                    "Una meta no puede ser negativa: " + valor + ". Cero significa que este mes "
                            + "no se pide ese resultado; para retirar la meta, borra la fila.");
        }
        if (fechaCreacion == null) {
            fechaCreacion = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public int getValor() {
        return valor;
    }

    public void setValor(int valor) {
        this.valor = valor;
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

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
