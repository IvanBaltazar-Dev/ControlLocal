package com.controllocal.domain.auditoria;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;

/**
 * <b>Un comando ya ejecutado, con lo que devolvio</b> (V57).
 *
 * <h2>Por que no basta el outbox</h2>
 * {@link EventoDominio} responde <i>quien hizo que y desde donde</i>. Eso es
 * trazabilidad. Si KAIROS reenvia el mismo {@code RegistrarPropiedad} porque
 * se corto la conexion antes de leer la respuesta, el outbox anotara dos altas
 * — correctamente, porque hubo dos — y en la cartera habra dos propiedades
 * identicas. La traza no evita el duplicado: lo documenta.
 *
 * <h2>Y por que no se puede deducir por contenido</h2>
 * Dos departamentos iguales en el mismo edificio son un caso real, no un
 * duplicado. Nadie en el servidor puede distinguir "otra propiedad igual" de
 * "la misma otra vez"; <b>solo el cliente lo sabe</b>, y lo dice con una clave
 * explicita: un identificador por operacion, repetido igual en cada reintento
 * de ESA operacion. Es la misma decision que ya tomo
 * {@code service.soporte.Idempotencia} para los movimientos de comision;
 * aqui se generaliza a cualquier comando.
 *
 * <h2>Guarda el resultado, no solo la clave</h2>
 * El reintento tiene que recibir <b>lo mismo</b> que recibio el primero. Si el
 * segundo intento devolviera un conflicto, un canal conversacional le diria al
 * usuario que fallo algo que en realidad salio bien. Acertar a la primera y
 * reintentar deben ser indistinguibles.
 *
 * <h2>La huella</h2>
 * Misma clave y <b>otro</b> contenido no es un reintento: es una clave
 * reutilizada por error. Devolver el resultado anterior confirmaria una
 * operacion que nadie pidio, asi que se compara el SHA-256 del comando y la
 * discrepancia se rechaza.
 */
@Entity
@Table(name = "comando_idempotente")
public class ComandoIdempotente extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comando")
    private Long id;

    /** La clave que puso el cliente. Un UUID cabe de sobra en 64. */
    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String claveIdempotencia;

    @Column(name = "tipo_comando", nullable = false, length = 60)
    private String tipoComando;

    /** SHA-256 del contenido: distingue el reintento honesto de la clave reciclada. */
    @Column(name = "huella", nullable = false, length = 64)
    private String huella;

    @Column(name = "entidad_tipo", nullable = false, length = 30)
    private String entidadTipo;

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    /** Lo que devolvio, ya serializado por la capa de servicio. */
    @Column(name = "resultado", nullable = false)
    private String resultado = "{}";

    @Column(name = "id_persona_rol")
    private Long idPersonaRol;

    @Column(name = "canal", nullable = false, length = 20)
    private String canal = EventoDominio.CANAL_SPA;

    /** Que agente lo pidio. NULL = lo pidio una persona directamente. */
    @Column(name = "agente", length = 30)
    private String agente;

    /**
     * El mensaje del canal que lo disparo.
     *
     * <p>No sustituye a {@link #claveIdempotencia}, que sigue siendo LA
     * restriccion; deja escrito de que mensaje salio cada comando. En un canal
     * conversacional los dos coinciden a menudo, porque un webhook reenviado
     * trae el mismo identificador de mensaje y esa es la clave natural.
     */
    @Column(name = "mensaje_id", length = 128)
    private String mensajeId;

    @Column(name = "fecha", insertable = false, updatable = false)
    private OffsetDateTime fecha;

    public static ComandoIdempotente de(Long idOrganizacion, String clave, String tipoComando,
                                        String huella, String entidadTipo, Long entidadId,
                                        Long idPersonaRol, String canal, String agente,
                                        String mensajeId, String resultadoJson) {
        ComandoIdempotente comando = new ComandoIdempotente();
        comando.setOrganizacionId(idOrganizacion);
        comando.claveIdempotencia = clave;
        comando.tipoComando = tipoComando;
        comando.huella = huella;
        comando.entidadTipo = entidadTipo;
        comando.entidadId = entidadId;
        comando.idPersonaRol = idPersonaRol;
        comando.canal = canal == null ? EventoDominio.CANAL_SPA : canal;
        comando.agente = agente;
        comando.mensajeId = mensajeId;
        comando.resultado = (resultadoJson == null || resultadoJson.isBlank()) ? "{}" : resultadoJson;
        return comando;
    }

    /**
     * ¿Este comando que acaba de llegar es el MISMO que produjo esta fila?
     *
     * <p>Compara el tipo ademas de la huella: la misma clave con distinto tipo
     * de comando es tan sospechosa como con distinto contenido, y el mensaje
     * que se le da al cliente cambia segun cual de las dos cosas falle.
     */
    @Transient
    public boolean coincideCon(String tipoComando, String huella) {
        return this.tipoComando.equals(tipoComando) && this.huella.equals(huella);
    }

    public Long getId() {
        return id;
    }

    public String getClaveIdempotencia() {
        return claveIdempotencia;
    }

    public String getTipoComando() {
        return tipoComando;
    }

    public String getHuella() {
        return huella;
    }

    public String getEntidadTipo() {
        return entidadTipo;
    }

    public Long getEntidadId() {
        return entidadId;
    }

    public String getResultado() {
        return resultado;
    }

    public Long getIdPersonaRol() {
        return idPersonaRol;
    }

    public String getCanal() {
        return canal;
    }

    public String getAgente() {
        return agente;
    }

    public String getMensajeId() {
        return mensajeId;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }
}
