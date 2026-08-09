package com.controllocal.domain.seguridad;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoConcesionRecuperacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;

/**
 * Concesion tecnica de recuperacion (V38, §9 del diseño). <b>Nivel 3</b>: lo
 * que se usa cuando una organizacion se queda sin ningun administrador
 * operativo y la via ordinaria ya no alcanza.
 *
 * <h2>Lo que NO es</h2>
 * <b>No es una cuenta, no es un rol y no es una sesion.</b> No produce token:
 * no hay nada en lo que entrar. Cada accion es una llamada suelta que presenta
 * el secreto y consume una capacidad. Una cuenta —aunque este inactiva y con
 * la contrasena partida— seria una identidad privilegiada permanente; esto
 * caduca en 30 minutos y se cierra sola.
 *
 * <h2>Las tres identidades</h2>
 * Guarda <b>custodio A, custodio B y operador</b>, y la base exige que sean
 * distintas (D-S0-52). La regla «quien ejecuta no custodia» no se puede
 * aplicar sobre algo que no se conserva, y un log de texto se reescribe.
 */
@Entity
@Table(name = "concesion_recuperacion")
public class ConcesionRecuperacion extends EntidadDeOrganizacion {

    public static final String PENDIENTE = Codigos.ConcesionRecuperacion.PENDIENTE;
    public static final String VIGENTE = Codigos.ConcesionRecuperacion.VIGENTE;
    public static final String CERRADA = Codigos.ConcesionRecuperacion.CERRADA;
    public static final String CADUCADA = Codigos.ConcesionRecuperacion.CADUCADA;
    public static final String AGOTADA = Codigos.ConcesionRecuperacion.AGOTADA;

    /** Media hora sobra para tres acciones; cuatro horas eran desproporcionadas. */
    public static final int MINUTOS_VENTANA = 30;

    /** Reactivar, revocar el factor y reponer la membresia. Ni una mas. */
    public static final short MAX_ACCIONES = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_concesion")
    private Long id;

    /** Alcance fijado al emitir e <b>inmutable</b>. */
    @Column(name = "id_persona_objetivo", nullable = false)
    private Long idPersonaObjetivo;

    @Column(name = "operador", nullable = false, length = 60)
    private String operador;

    @Column(name = "custodio_a", nullable = false, length = 60)
    private String custodioA;

    @Column(name = "custodio_b", nullable = false, length = 60)
    private String custodioB;

    /** SHA-256 del secreto de 256 bits. El secreto no se guarda en ningun sitio. */
    @Column(name = "hash_secreto", nullable = false, length = 64)
    private String hashSecreto;

    @Column(name = "motivo", nullable = false, length = 300)
    private String motivo;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado = PENDIENTE;

    @Column(name = "max_acciones", nullable = false)
    private short maxAcciones = MAX_ACCIONES;

    @Column(name = "acciones_consumidas", nullable = false)
    private short accionesConsumidas;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "vigente_desde")
    private OffsetDateTime vigenteDesde;

    @Column(name = "expira_en")
    private OffsetDateTime expiraEn;

    @Column(name = "cerrada_en")
    private OffsetDateTime cerradaEn;

    @Column(name = "cierre_motivo", length = 60)
    private String cierreMotivo;

    @Column(name = "prorrogas", nullable = false)
    private short prorrogas;

    /**
     * Vigente <b>y</b> dentro de su ventana. Las dos cosas juntas y a
     * proposito: se comprueba en cada uso, no solo en el barrido programado —
     * la concesion caduca aunque el {@code @Scheduled} no haya corrido.
     */
    public boolean utilizableEn(OffsetDateTime instante) {
        return VIGENTE.equals(estado)
                && expiraEn != null && expiraEn.isAfter(instante)
                && accionesConsumidas < maxAcciones;
    }

    public boolean estaPendiente() {
        return PENDIENTE.equals(estado);
    }

    public Long getId() {
        return id;
    }

    public Long getIdPersonaObjetivo() {
        return idPersonaObjetivo;
    }

    public void setIdPersonaObjetivo(Long idPersonaObjetivo) {
        this.idPersonaObjetivo = idPersonaObjetivo;
    }

    public String getOperador() {
        return operador;
    }

    public void setOperador(String operador) {
        this.operador = operador;
    }

    public String getCustodioA() {
        return custodioA;
    }

    public void setCustodioA(String custodioA) {
        this.custodioA = custodioA;
    }

    public String getCustodioB() {
        return custodioB;
    }

    public void setCustodioB(String custodioB) {
        this.custodioB = custodioB;
    }

    public String getHashSecreto() {
        return hashSecreto;
    }

    public void setHashSecreto(String hashSecreto) {
        this.hashSecreto = hashSecreto;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getEstado() {
        return estado;
    }

    /** Vista tipada del codigo persistido (convencion de EstadosDominio). */
    @Transient
    public EstadoConcesionRecuperacion estadoTipado() {
        return estado == null ? null : EstadoConcesionRecuperacion.desde(estado);
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public short getMaxAcciones() {
        return maxAcciones;
    }

    public short getAccionesConsumidas() {
        return accionesConsumidas;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public OffsetDateTime getVigenteDesde() {
        return vigenteDesde;
    }

    public void setVigenteDesde(OffsetDateTime vigenteDesde) {
        this.vigenteDesde = vigenteDesde;
    }

    public OffsetDateTime getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(OffsetDateTime expiraEn) {
        this.expiraEn = expiraEn;
    }

    public OffsetDateTime getCerradaEn() {
        return cerradaEn;
    }

    public void setCerradaEn(OffsetDateTime cerradaEn) {
        this.cerradaEn = cerradaEn;
    }

    public String getCierreMotivo() {
        return cierreMotivo;
    }

    public void setCierreMotivo(String cierreMotivo) {
        this.cierreMotivo = cierreMotivo;
    }

    public short getProrrogas() {
        return prorrogas;
    }

    public void setProrrogas(short prorrogas) {
        this.prorrogas = prorrogas;
    }
}
