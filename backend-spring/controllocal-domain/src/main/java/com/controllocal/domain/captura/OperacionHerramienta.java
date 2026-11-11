package com.controllocal.domain.captura;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;

/** Registro técnico UC-K-06/07; no introduce estados comerciales del inmueble. */
@Entity
@Table(name = "operacion_herramienta")
public class OperacionHerramienta extends EntidadDeOrganizacion {
    public static final String PREPARADA = "P", INICIADA = "I", EXITO = "X", RECHAZADA = "R", INCIERTA = "U";
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_operacion") private Long id;
    @Column(nullable = false, length = 36) private String referencia;
    @Column(name = "id_persona", nullable = false) private long idPersona;
    @Column(name = "id_persona_rol", nullable = false) private long idPersonaRol;
    @Column(name = "rol_efectivo", nullable = false, length = 30) private String rolEfectivo;
    @Column(name = "identidad_sesion", nullable = false, length = 64) private String identidadSesion;
    @Column(name = "id_borrador") private Long idBorrador;
    @Column(nullable = false, length = 60) private String capacidad;
    @Column(nullable = false, length = 64) private String huella;
    @Column(nullable = false, columnDefinition = "text") private String contenido;
    @Column(nullable = false, columnDefinition = "text") private String procedencia;
    @Column(name = "caduca_en", nullable = false) private Instant caducaEn;
    @Column(name = "creada_en", nullable = false) private Instant creadaEn;
    @Column(name = "prueba_revision", length = 64) private String pruebaRevision;
    @Column(name = "evento_humano", length = 64) private String eventoHumano;
    @Column(name = "confirmada_en") private Instant confirmadaEn;
    @Column(name = "version_aprobada") private Long versionAprobada;
    @Column(columnDefinition = "text") private String aprobacion;
    @Column(nullable = false, length = 1) private String estado = PREPARADA;
    @Column(columnDefinition = "text") private String resultado;
    @Column(length = 60) private String motivo;
    @Version @Column(nullable = false) private long version;

    protected OperacionHerramienta() { }
    public OperacionHerramienta(long organizacion, long persona, long rol, String banda,
            String sesion, Long borrador, String referencia, String capacidad, String huella,
            String contenido, String procedencia, Instant caducaEn) {
        setOrganizacionId(organizacion); this.idPersona = persona; this.idPersonaRol = rol;
        this.rolEfectivo = banda; this.identidadSesion = sesion; this.idBorrador = borrador;
        this.referencia = referencia; this.capacidad = capacidad; this.huella = huella;
        this.contenido = contenido; this.procedencia = procedencia; this.caducaEn = caducaEn;
        this.creadaEn = Instant.now();
    }
    public void revisar(String prueba) {
        if (!PREPARADA.equals(estado)) throw new IllegalStateException("La actuación ya fue iniciada.");
        pruebaRevision = prueba;
    }
    public void iniciar(String evento, String aprobacion) {
        if (!PREPARADA.equals(estado)) throw new IllegalStateException("La actuación ya fue iniciada.");
        eventoHumano = evento; versionAprobada=version; this.aprobacion=aprobacion;
        confirmadaEn = Instant.now(); estado = INICIADA;
    }
    public void exito(String documento) { resultado = documento; estado = EXITO; motivo = null; }
    public void rechazar(String causa) { estado = RECHAZADA; motivo = causa; }
    public void incierta() { if (INICIADA.equals(estado)) estado = INCIERTA; }
    public Long getId() { return id; }
    public String getReferencia() { return referencia; }
    public long getIdPersona() { return idPersona; }
    public long getIdPersonaRol() { return idPersonaRol; }
    public String getRolEfectivo() { return rolEfectivo; }
    public String getIdentidadSesion() { return identidadSesion; }
    public Long getIdBorrador() { return idBorrador; }
    public String getCapacidad() { return capacidad; }
    public String getHuella() { return huella; }
    public String getContenido() { return contenido; }
    public String getProcedencia() { return procedencia; }
    public Instant getCaducaEn() { return caducaEn; }
    public String getPruebaRevision() { return pruebaRevision; }
    public String getEventoHumano() { return eventoHumano; }
    public Long getVersionAprobada() { return versionAprobada; }
    public String getAprobacion() { return aprobacion; }
    public String getEstado() { return estado; }
    public String getResultado() { return resultado; }
    public String getMotivo() { return motivo; }
    public long getVersion() { return version; }
}
