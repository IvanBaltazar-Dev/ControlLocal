package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoProspeccion;
import com.controllocal.domain.comun.Transicionable;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Prospeccion (pre-captacion): el seguimiento del agente al propietario para
 * captar un local. Embudo (codigos del cable congelado):
 * P Prospecto -> C Contactado -> R Reunion -> S En seguimiento -> {T Captado | D Descartado}.
 * El estado SOLO muta via Transiciones (auditoria RC-002); los metodos de
 * este dominio registran los efectos laterales de cada hito (fechas,
 * resultado, reloj de recontacto), calcados del modelo v1.
 */
@Entity
@Table(name = "prospeccion")
public class Prospeccion extends EntidadDeOrganizacion implements Transicionable {

    /** Dias sin nueva accion de seguimiento tras los cuales toca recontactar (alerta el dia 8). */
    public static final int DIAS_RECONTACTO = 7;

    public static final String PROSPECTO = Codigos.Prospeccion.PROSPECTO;
    public static final String CONTACTADO = Codigos.Prospeccion.CONTACTADO;
    public static final String REUNION = Codigos.Prospeccion.REUNION;
    public static final String PROPUESTA_ENTREGADA = Codigos.Prospeccion.PROPUESTA_ENTREGADA;
    public static final String EN_SEGUIMIENTO = Codigos.Prospeccion.SEGUIMIENTO;
    public static final String CAPTADO = Codigos.Prospeccion.CAPTADO;
    public static final String DESCARTADO = Codigos.Prospeccion.DESCARTADO;

    public static final String RESULTADO_PENDIENTE = "P";
    public static final String RESULTADO_ACEPTADA = "A";
    public static final String RESULTADO_RECHAZADA = "R";

    /**
     * <b>DEPRECADO: solo lectura historica.</b> {@code S} vive en
     * {@code ck_prospeccion_resultado} desde V5 y nunca tuvo productor —no
     * existia ni como constante—. La continuidad comercial que pretendia
     * expresar ya la cubre {@link #EN_SEGUIMIENTO}, que si se produce
     * ({@code POST /prospecciones/{id}/propuesta}), asi que una segunda
     * maquina para lo mismo solo anadiria ambiguedad.
     *
     * <p>No confundir con {@code RECONTACTAR} de {@code interaccion_comercial}:
     * ese es otro vocabulario, esta vivo y de el se derivan tareas. No se toca.
     *
     * <p>La constante existe para que el catalogo de productores pueda
     * nombrarlo y para leer filas antiguas si las hubiera. Ningun camino
     * funcional lo escribe, y {@code ProspeccionEstadosTest} lo comprueba.
     */
    @Deprecated
    public static final String RESULTADO_RECONTACTAR_HISTORICO = "S";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prospeccion")
    private Long id;

    @Column(name = "codigo_prospeccion", nullable = false, length = 20)
    private String codigoProspeccion;

    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private OffsetDateTime fechaRegistro;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    @Column(name = "resultado_propuesta", length = 1)
    private String resultadoPropuesta;

    @Column(name = "fecha_contacto")
    private LocalDate fechaContacto;

    @Column(name = "fecha_reunion")
    private LocalDate fechaReunion;

    @Column(name = "fecha_propuesta")
    private LocalDate fechaPropuesta;

    @Column(name = "fecha_recontacto")
    private LocalDate fechaRecontacto;

    @Column(name = "observaciones")
    private String observaciones;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_propiedad", nullable = false)
    private Propiedad propiedad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente", nullable = false)
    private DetalleAgente agente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_captacion")
    private Captacion captacion;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    // ------------------------------------------------------------------
    // Transicionable
    // ------------------------------------------------------------------

    @Override
    public String entidadTipo() {
        return "PROSPECCION";
    }

    @Override
    public String estadoActual() {
        return estado;
    }

    @Override
    public void transicionarA(String nuevoEstado) {
        this.estado = EstadoProspeccion.desde(nuevoEstado).codigo();
        touch();
    }

    @Transient
    public EstadoProspeccion estadoTipado() {
        return estado == null ? null : EstadoProspeccion.desde(estado);
    }

    // ------------------------------------------------------------------
    // Efectos laterales de los hitos (semantica calcada del modelo v1).
    // ------------------------------------------------------------------

    /** El primer contacto queda fijo; cada contacto reinicia el reloj de recontacto. */
    public void marcarContacto(LocalDate hoy) {
        if (fechaContacto == null) {
            fechaContacto = hoy;
        }
        fechaRecontacto = hoy;
        touch();
    }

    public void marcarReunion(LocalDate hoy) {
        fechaReunion = hoy;
        fechaRecontacto = hoy;
        touch();
    }

    /** La propuesta entregada queda pendiente de respuesta del propietario. */
    public void marcarPropuesta(LocalDate hoy) {
        fechaPropuesta = hoy;
        resultadoPropuesta = RESULTADO_PENDIENTE;
        fechaRecontacto = hoy;
        touch();
    }

    public void marcarSeguimiento(LocalDate hoy) {
        fechaRecontacto = hoy;
        touch();
    }

    /** El propietario acepta: nace la captacion y se apaga el reloj. */
    public void marcarAceptada(Captacion captacion) {
        this.resultadoPropuesta = RESULTADO_ACEPTADA;
        this.captacion = captacion;
        this.fechaRecontacto = null;
        touch();
    }

    /**
     * El propietario RECHAZA la propuesta: el desenlace queda registrado.
     *
     * <p>Antes esto era {@code marcarCierre(motivo, resultado)} con el codigo
     * en un {@code String} libre. Tenia exactamente dos llamadores —rechazo y
     * descarte— pero su firma admitia cualquier letra, asi que la unica
     * defensa contra un valor sin dueno era {@code ck_prospeccion_resultado}:
     * un error de DOMINIO que solo se manifestaba como fallo tardio de
     * persistencia. Dos metodos que dicen lo que hacen cierran esa puerta en
     * tiempo de compilacion.
     */
    public void marcarRechazoDelPropietario(String motivo) {
        this.resultadoPropuesta = RESULTADO_RECHAZADA;
        cerrar(motivo);
    }

    /**
     * El AGENTE descarta la prospeccion. No hubo respuesta del propietario, asi
     * que {@code resultado_propuesta} se queda como estaba: inventar un
     * desenlace que nadie dio seria falsear el embudo.
     */
    public void marcarDescartePorAgente(String motivo) {
        cerrar(motivo);
    }

    private void cerrar(String motivo) {
        this.observaciones = motivo;
        this.fechaRecontacto = null;
        touch();
    }

    /** Sigue viva (ni captada ni descartada): admite eventos del embudo. */
    public boolean enProceso() {
        return !CAPTADO.equals(estado) && !DESCARTADO.equals(estado);
    }

    private void touch() {
        this.fechaActualizacion = OffsetDateTime.now();
    }

    // ------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getCodigoProspeccion() {
        return codigoProspeccion;
    }

    public void setCodigoProspeccion(String codigoProspeccion) {
        this.codigoProspeccion = codigoProspeccion;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public String getResultadoPropuesta() {
        return resultadoPropuesta;
    }

    public LocalDate getFechaContacto() {
        return fechaContacto;
    }

    public LocalDate getFechaReunion() {
        return fechaReunion;
    }

    public LocalDate getFechaPropuesta() {
        return fechaPropuesta;
    }

    public LocalDate getFechaRecontacto() {
        return fechaRecontacto;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public Propiedad getPropiedad() {
        return propiedad;
    }

    public void setPropiedad(Propiedad propiedad) {
        this.propiedad = propiedad;
    }

    public DetalleAgente getAgente() {
        return agente;
    }

    public void setAgente(DetalleAgente agente) {
        this.agente = agente;
    }

    public Captacion getCaptacion() {
        return captacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
