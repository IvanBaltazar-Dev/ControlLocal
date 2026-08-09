package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoDocumentoSolicitud;
import com.controllocal.domain.comun.Transicionable;
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

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Documento entregado para una solicitud. Nace Registrado con revision
 * Pendiente; el broker lo deja Validado (conforme) u Observado (con la
 * observacion concreta que el agente debe subsanar).
 *
 * <p>Maquina (codigos del cable): R Registrado -&gt; {V Validado | O Observado}.
 * La v1 movia este estado a mano; aqui pasa por Transiciones y por tanto queda
 * auditado (mejora MEJ-01 gratis, sin tocar el cable).
 *
 * <p>{@code rutaArchivo} guarda la CLAVE del almacen hibrido (S3 o disco), no
 * una ruta del sistema de archivos.
 */
@Entity
@Table(name = "documento_solicitud")
public class DocumentoSolicitud extends EntidadDeOrganizacion implements Transicionable {

    public static final String ENTIDAD_TIPO = "DOCUMENTO_SOLICITUD";

    public static final String REGISTRADO = Codigos.DocumentoSolicitud.REGISTRADO;
    public static final String OBSERVADO = Codigos.DocumentoSolicitud.OBSERVADO;
    public static final String VALIDADO = Codigos.DocumentoSolicitud.VALIDADO;
    public static final Set<String> ESTADOS = Set.of(REGISTRADO, OBSERVADO, VALIDADO);

    /** ResultadoRevisionDocumento del cable. */
    public static final String REVISION_PENDIENTE = "P";
    public static final String REVISION_CONFORME = "C";
    public static final String REVISION_OBSERVADO = "O";
    public static final Set<String> RESULTADOS_REVISION = Set.of("P", "C", "O");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitud", nullable = false)
    private SolicitudAlquiler solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_documento_requerido", nullable = false)
    private TipoDocumentoRequerido tipoDocumento;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "ruta_archivo", length = 255)
    private String rutaArchivo;

    /**
     * La fija la APLICACION al registrar la entrega, como {@code registrarEntrega()}
     * de la v1; el DEFAULT now() de la BD queda solo como red de seguridad. Si
     * fuera {@code insertable = false}, la respuesta del alta viajaria con
     * {@code fechaEntrega} nula: Hibernate no relee la fila recien insertada.
     */
    @Column(name = "fecha_entrega", nullable = false)
    private OffsetDateTime fechaEntrega;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    @Column(name = "resultado_revision", length = 1)
    private String resultadoRevision;

    @Column(name = "observaciones")
    private String observaciones;

    // ------------------------------------------------------------------
    // Transicionable
    // ------------------------------------------------------------------

    @Override
    public String entidadTipo() {
        return ENTIDAD_TIPO;
    }

    @Override
    public String estadoActual() {
        return estado;
    }

    @Override
    public void transicionarA(String nuevoEstado) {
        this.estado = EstadoDocumentoSolicitud.desde(nuevoEstado).codigo();
    }

    @Transient
    public EstadoDocumentoSolicitud estadoTipado() {
        return estado == null ? null : EstadoDocumentoSolicitud.desde(estado);
    }

    /**
     * Alta del documento: nace Registrado con revision Pendiente
     * ({@code registrarEntrega()} de la v1). El estado lo fija Transiciones;
     * aqui van la fecha y el resultado de partida.
     */
    public void registrarEntrega() {
        if (fechaEntrega == null) {
            fechaEntrega = OffsetDateTime.now();
        }
        if (resultadoRevision == null) {
            resultadoRevision = REVISION_PENDIENTE;
        }
    }

    /**
     * Marca el desenlace de la revision del broker. El estado lo mueve
     * Transiciones; aqui solo viaja lo que acompana a esa decision.
     *
     * <p>La observacion se PISA siempre, tambien al conformar: dejar conforme
     * un documento borra la observacion previa (asi lo hace la v1).
     */
    public void registrarRevision(String resultadoRevision, String observaciones) {
        this.resultadoRevision = resultadoRevision;
        this.observaciones = observaciones;
    }

    /**
     * Estado que corresponde al resultado de la revision. Ojo con el detalle
     * del cable: SOLO "conforme" valida; cualquier otro resultado —incluido
     * un improbable "pendiente"— deja el documento OBSERVADO.
     */
    public static String estadoSegunRevision(String resultadoRevision) {
        return REVISION_CONFORME.equals(resultadoRevision) ? VALIDADO : OBSERVADO;
    }

    /** Un tipo cuenta como entregado si esta Registrado o Validado (un Observado deja de contar). */
    public boolean cuentaComoEntregado() {
        return REGISTRADO.equals(estado) || VALIDADO.equals(estado);
    }

    public boolean revisionPendiente() {
        return resultadoRevision == null || REVISION_PENDIENTE.equals(resultadoRevision);
    }

    // ------------------------------------------------------------------
    // Accesores
    // ------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SolicitudAlquiler getSolicitud() {
        return solicitud;
    }

    public void setSolicitud(SolicitudAlquiler solicitud) {
        this.solicitud = solicitud;
    }

    public TipoDocumentoRequerido getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(TipoDocumentoRequerido tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    public OffsetDateTime getFechaEntrega() {
        return fechaEntrega;
    }

    public String getResultadoRevision() {
        return resultadoRevision;
    }

    public void setResultadoRevision(String resultadoRevision) {
        this.resultadoRevision = resultadoRevision;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
