package com.controllocal.domain.comun;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vocabulario unico de estados persistidos. El codigo es local al agregado:
 * una misma letra puede tener significados distintos sin crear un supuesto
 * significado global. Las descripciones son metadatos para documentacion; la
 * interfaz publica textos desde sus propios catalogos.
 */
public final class EstadosDominio {

    private EstadosDominio() {
    }

    public interface Codigo {
        String codigo();
        String descripcion();
    }

    /**
     * Literales persistidos centralizados. Son constantes de compilacion para
     * que los aliases del contrato legado puedan seguir usandose en
     * {@code switch} sin convertirse en una segunda fuente de verdad.
     */
    public static final class Codigos {
        private Codigos() {
        }

        public static final class RegistroPropiedad {
            public static final String ACTIVO = "A";
            public static final String INACTIVO = "I";
            private RegistroPropiedad() { }
        }

        public static final class ActivoInactivo {
            public static final String ACTIVO = "A";
            public static final String INACTIVO = "I";
            private ActivoInactivo() { }
        }

        public static final class OperacionAgente {
            public static final String DISPONIBLE = "D";
            public static final String LICENCIA = "L";
            public static final String NO_DISPONIBLE = "N";
            private OperacionAgente() { }
        }

        public static final class DisponibilidadPropiedad {
            public static final String DISPONIBLE = "D";
            public static final String RESERVADO = "R";
            public static final String ALQUILADO = "A";
            public static final String RETIRADO = "T";
            private DisponibilidadPropiedad() { }
        }

        public static final class Publicacion {
            public static final String BORRADOR = "B";
            public static final String PUBLICADA = "P";
            public static final String SUSPENDIDA = "S";
            public static final String CERRADA = "C";
            private Publicacion() { }
        }

        public static final class Prospeccion {
            public static final String PROSPECTO = "P";
            public static final String CONTACTADO = "C";
            public static final String REUNION = "R";
            public static final String PROPUESTA_ENTREGADA = "E";
            public static final String SEGUIMIENTO = "S";
            public static final String CAPTADO = "T";
            public static final String DESCARTADO = "D";
            private Prospeccion() { }
        }

        public static final class Captacion {
            public static final String PENDIENTE = "P";
            public static final String OBSERVADA = "O";
            public static final String RECHAZADA = "R";
            public static final String ACTIVA = "A";
            public static final String CERRADA = "C";
            public static final String VENCIDA = "V";
            private Captacion() { }
        }

        public static final class Oportunidad {
            public static final String ABIERTA = "A";
            public static final String SOLICITUD_CREADA = "S";
            public static final String NO_CONTINUA = "N";
            public static final String FINALIZADA_EXITOSA = "F";
            public static final String FINALIZADA_NO_FAVORABLE = "X";
            private Oportunidad() { }
        }

        public static final class Requerimiento {
            public static final String ACTIVO = "A";
            public static final String PAUSADO = "P";
            public static final String CERRADO = "C";
            private Requerimiento() { }
        }

        public static final class Visita {
            public static final String PROGRAMADA = "P";
            public static final String REPROGRAMADA = "G";
            public static final String CANCELADA = "C";
            public static final String NO_REALIZADA = "N";
            public static final String REALIZADA = "R";
            private Visita() { }
        }

        public static final class Solicitud {
            public static final String REGISTRADA = "G";
            public static final String EN_REVISION = "E";
            public static final String OBSERVADA = "O";
            public static final String APROBADA = "A";
            public static final String RECHAZADA = "R";
            public static final String DESISTIDA = "D";
            public static final String CERRADA = "C";
            private Solicitud() { }
        }

        public static final class DocumentoSolicitud {
            public static final String REGISTRADO = "R";
            public static final String OBSERVADO = "O";
            public static final String VALIDADO = "V";
            private DocumentoSolicitud() { }
        }

        public static final class Contrato {
            public static final String EN_PROCESO = "P";
            public static final String FIRMADO = "D";
            public static final String VIGENTE = "V";
            public static final String RENOVADO = "R";
            public static final String FINALIZADO = "F";
            public static final String RESCINDIDO = "S";
            public static final String ANULADO = "A";
            private Contrato() { }
        }

        public static final class Comision {
            public static final String PENDIENTE = "P";
            public static final String PARCIAL = "R";
            public static final String COBRADA = "C";
            public static final String ANULADA = "A";
            private Comision() { }
        }

        public static final class Tarea {
            public static final String PENDIENTE = "P";
            public static final String EN_PROCESO = "E";
            public static final String COMPLETADA = "C";
            public static final String VENCIDA = "V";
            public static final String CANCELADA = "A";
            private Tarea() { }
        }

        public static final class Alerta {
            public static final String ACTIVA = "A";
            public static final String ATENDIDA = "T";
            public static final String DESCARTADA = "D";
            private Alerta() { }
        }

        public static final class RegularizacionEconomica {
            public static final String PENDIENTE = "P";
            public static final String RESUELTA = "R";
            public static final String DESCARTADA = "D";
            private RegularizacionEconomica() { }
        }

        // Identidad y gobierno de accesos. Nacieron en V31/V37/V38 con la
        // palabra completa y V40 los alineo con el resto del esquema.

        public static final class TokenAcceso {
            public static final String VIGENTE = "V";
            public static final String CONSUMIDO = "C";
            public static final String REVOCADO = "R";
            public static final String AGOTADO = "A";
            private TokenAcceso() { }
        }

        public static final class FactorAutenticacion {
            public static final String PENDIENTE = "P";
            public static final String ACTIVO = "A";
            public static final String REVOCADO = "R";
            private FactorAutenticacion() { }
        }

        public static final class ConcesionRecuperacion {
            public static final String PENDIENTE = "P";
            public static final String VIGENTE = "V";
            public static final String CERRADA = "C";
            /** `C` la toma CERRADA; se toma otra letra distintiva del termino. */
            public static final String CADUCADA = "D";
            public static final String AGOTADA = "A";
            private ConcesionRecuperacion() { }
        }
    }

    private abstract static class Conversor<E extends Enum<E> & Codigo>
            implements AttributeConverter<E, String> {
        private final Map<String, E> porCodigo;

        protected Conversor(E[] valores) {
            Map<String, E> indice = new LinkedHashMap<>();
            for (E valor : valores) {
                if (valor.codigo().length() != 1 || indice.put(valor.codigo(), valor) != null) {
                    throw new IllegalStateException("Codigo de estado duplicado o no unitario: " + valor);
                }
            }
            porCodigo = Collections.unmodifiableMap(indice);
        }

        @Override
        public String convertToDatabaseColumn(E atributo) {
            return atributo == null ? null : atributo.codigo();
        }

        @Override
        public E convertToEntityAttribute(String codigo) {
            if (codigo == null) return null;
            E valor = porCodigo.get(codigo);
            if (valor == null) {
                throw new IllegalArgumentException("Codigo de estado no documentado: " + codigo);
            }
            return valor;
        }
    }

    private static <E extends Enum<E> & Codigo> E desde(E[] valores, String codigo) {
        return Arrays.stream(valores)
                .filter(v -> v.codigo().equals(codigo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Codigo de estado no documentado: " + codigo));
    }

    public enum EstadoActivoInactivo implements Codigo {
        ACTIVO(Codigos.ActivoInactivo.ACTIVO, "Activo"),
        INACTIVO(Codigos.ActivoInactivo.INACTIVO, "Inactivo");
        private final String codigo; private final String descripcion;
        EstadoActivoInactivo(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoActivoInactivo desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoActivoInactivo> { public Jpa() { super(values()); } }
    }

    public enum EstadoOperativoAgente implements Codigo {
        DISPONIBLE(Codigos.OperacionAgente.DISPONIBLE, "Disponible"),
        LICENCIA(Codigos.OperacionAgente.LICENCIA, "Licencia"),
        NO_DISPONIBLE(Codigos.OperacionAgente.NO_DISPONIBLE, "No disponible");
        private final String codigo; private final String descripcion;
        EstadoOperativoAgente(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoOperativoAgente desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoOperativoAgente> { public Jpa() { super(values()); } }
    }

    public enum EstadoRegistroPropiedad implements Codigo {
        ACTIVO(Codigos.RegistroPropiedad.ACTIVO, "Activo"),
        INACTIVO(Codigos.RegistroPropiedad.INACTIVO, "Inactivo");
        private final String codigo; private final String descripcion;
        EstadoRegistroPropiedad(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoRegistroPropiedad desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoRegistroPropiedad> { public Jpa() { super(values()); } }
    }

    public enum DisponibilidadComercial implements Codigo {
        DISPONIBLE(Codigos.DisponibilidadPropiedad.DISPONIBLE, "Disponible"),
        RESERVADO(Codigos.DisponibilidadPropiedad.RESERVADO, "Reservado"),
        ALQUILADO(Codigos.DisponibilidadPropiedad.ALQUILADO, "Alquilado"),
        RETIRADO(Codigos.DisponibilidadPropiedad.RETIRADO, "Retirado del mercado");
        private final String codigo; private final String descripcion;
        DisponibilidadComercial(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static DisponibilidadComercial desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<DisponibilidadComercial> { public Jpa() { super(values()); } }
    }

    public enum EstadoPublicacion implements Codigo {
        BORRADOR(Codigos.Publicacion.BORRADOR, "Sin publicar"),
        PUBLICADA(Codigos.Publicacion.PUBLICADA, "Publicada"),
        SUSPENDIDA(Codigos.Publicacion.SUSPENDIDA, "Pausada"),
        CERRADA(Codigos.Publicacion.CERRADA, "Cerrada");
        private final String codigo; private final String descripcion;
        EstadoPublicacion(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoPublicacion desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoPublicacion> { public Jpa() { super(values()); } }
    }

    public enum EstadoProspeccion implements Codigo {
        PROSPECTO(Codigos.Prospeccion.PROSPECTO, "Prospecto"),
        CONTACTADO(Codigos.Prospeccion.CONTACTADO, "Contactado"),
        REUNION(Codigos.Prospeccion.REUNION, "Reunion"),
        PROPUESTA_ENTREGADA(Codigos.Prospeccion.PROPUESTA_ENTREGADA, "Propuesta entregada"),
        SEGUIMIENTO(Codigos.Prospeccion.SEGUIMIENTO, "Seguimiento"),
        CAPTADO(Codigos.Prospeccion.CAPTADO, "Captado"),
        DESCARTADO(Codigos.Prospeccion.DESCARTADO, "Descartado");
        private final String codigo; private final String descripcion;
        EstadoProspeccion(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoProspeccion desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoProspeccion> { public Jpa() { super(values()); } }
    }

    public enum EstadoCaptacion implements Codigo {
        PENDIENTE(Codigos.Captacion.PENDIENTE, "Pendiente de revision"),
        OBSERVADA(Codigos.Captacion.OBSERVADA, "Observada"),
        RECHAZADA(Codigos.Captacion.RECHAZADA, "Rechazada"),
        ACTIVA(Codigos.Captacion.ACTIVA, "Activa"),
        CERRADA(Codigos.Captacion.CERRADA, "Cerrada"),
        VENCIDA(Codigos.Captacion.VENCIDA, "Vencida");
        private final String codigo; private final String descripcion;
        EstadoCaptacion(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoCaptacion desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoCaptacion> { public Jpa() { super(values()); } }
    }

    public enum EstadoOportunidad implements Codigo {
        ABIERTA(Codigos.Oportunidad.ABIERTA, "Abierta"),
        SOLICITUD_CREADA(Codigos.Oportunidad.SOLICITUD_CREADA, "Solicitud creada"),
        NO_CONTINUA(Codigos.Oportunidad.NO_CONTINUA, "No continua"),
        FINALIZADA_EXITOSA(Codigos.Oportunidad.FINALIZADA_EXITOSA, "Finalizada exitosa"),
        FINALIZADA_NO_FAVORABLE(Codigos.Oportunidad.FINALIZADA_NO_FAVORABLE, "Finalizada no favorable");
        private final String codigo; private final String descripcion;
        EstadoOportunidad(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoOportunidad desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoOportunidad> { public Jpa() { super(values()); } }
    }

    public enum EstadoRequerimiento implements Codigo {
        ACTIVO(Codigos.Requerimiento.ACTIVO, "Activo"),
        PAUSADO(Codigos.Requerimiento.PAUSADO, "Pausado"),
        CERRADO(Codigos.Requerimiento.CERRADO, "Cerrado");
        private final String codigo; private final String descripcion;
        EstadoRequerimiento(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoRequerimiento desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoRequerimiento> { public Jpa() { super(values()); } }
    }

    public enum EstadoVisita implements Codigo {
        PROGRAMADA(Codigos.Visita.PROGRAMADA, "Programada"),
        REPROGRAMADA(Codigos.Visita.REPROGRAMADA, "Reprogramada"),
        CANCELADA(Codigos.Visita.CANCELADA, "Cancelada"),
        NO_REALIZADA(Codigos.Visita.NO_REALIZADA, "No realizada"),
        REALIZADA(Codigos.Visita.REALIZADA, "Realizada");
        private final String codigo; private final String descripcion;
        EstadoVisita(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoVisita desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoVisita> { public Jpa() { super(values()); } }
    }

    public enum EstadoSolicitud implements Codigo {
        REGISTRADA(Codigos.Solicitud.REGISTRADA, "Registrada"),
        EN_REVISION(Codigos.Solicitud.EN_REVISION, "En revision"),
        OBSERVADA(Codigos.Solicitud.OBSERVADA, "Observada"),
        APROBADA(Codigos.Solicitud.APROBADA, "Aprobada"),
        RECHAZADA(Codigos.Solicitud.RECHAZADA, "Rechazada"),
        DESISTIDA(Codigos.Solicitud.DESISTIDA, "Desistida"),
        CERRADA(Codigos.Solicitud.CERRADA, "Cerrada");
        private final String codigo; private final String descripcion;
        EstadoSolicitud(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoSolicitud desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoSolicitud> { public Jpa() { super(values()); } }
    }

    public enum EstadoContrato implements Codigo {
        EN_PROCESO(Codigos.Contrato.EN_PROCESO, "En proceso"),
        FIRMADO(Codigos.Contrato.FIRMADO, "Firmado"),
        VIGENTE(Codigos.Contrato.VIGENTE, "Vigente"),
        RENOVADO(Codigos.Contrato.RENOVADO, "Renovado"),
        FINALIZADO(Codigos.Contrato.FINALIZADO, "Finalizado"),
        RESCINDIDO(Codigos.Contrato.RESCINDIDO, "Rescindido"),
        ANULADO(Codigos.Contrato.ANULADO, "Anulado");
        private final String codigo; private final String descripcion;
        EstadoContrato(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoContrato desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoContrato> { public Jpa() { super(values()); } }
    }

    public enum EstadoDocumentoSolicitud implements Codigo {
        REGISTRADO(Codigos.DocumentoSolicitud.REGISTRADO, "Registrado"),
        OBSERVADO(Codigos.DocumentoSolicitud.OBSERVADO, "Observado"),
        VALIDADO(Codigos.DocumentoSolicitud.VALIDADO, "Validado");
        private final String codigo; private final String descripcion;
        EstadoDocumentoSolicitud(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoDocumentoSolicitud desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoDocumentoSolicitud> { public Jpa() { super(values()); } }
    }

    public enum EstadoComision implements Codigo {
        PENDIENTE(Codigos.Comision.PENDIENTE, "Pendiente"),
        PARCIAL(Codigos.Comision.PARCIAL, "Parcial"),
        COBRADA(Codigos.Comision.COBRADA, "Cobrada"),
        ANULADA(Codigos.Comision.ANULADA, "Anulada");
        private final String codigo; private final String descripcion;
        EstadoComision(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoComision desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoComision> { public Jpa() { super(values()); } }
    }

    public enum EstadoTarea implements Codigo {
        PENDIENTE(Codigos.Tarea.PENDIENTE, "Pendiente"),
        EN_PROCESO(Codigos.Tarea.EN_PROCESO, "En proceso"),
        COMPLETADA(Codigos.Tarea.COMPLETADA, "Completada"),
        VENCIDA(Codigos.Tarea.VENCIDA, "Vencida"),
        CANCELADA(Codigos.Tarea.CANCELADA, "Cancelada");
        private final String codigo; private final String descripcion;
        EstadoTarea(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoTarea desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoTarea> { public Jpa() { super(values()); } }
    }

    public enum EstadoAlerta implements Codigo {
        ACTIVA(Codigos.Alerta.ACTIVA, "Activa"),
        ATENDIDA(Codigos.Alerta.ATENDIDA, "Atendida"),
        DESCARTADA(Codigos.Alerta.DESCARTADA, "Descartada");
        private final String codigo; private final String descripcion;
        EstadoAlerta(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoAlerta desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoAlerta> { public Jpa() { super(values()); } }
    }

    public enum EstadoRegularizacionEconomica implements Codigo {
        PENDIENTE(Codigos.RegularizacionEconomica.PENDIENTE, "Pendiente"),
        RESUELTA(Codigos.RegularizacionEconomica.RESUELTA, "Resuelta"),
        DESCARTADA(Codigos.RegularizacionEconomica.DESCARTADA, "Descartada");
        private final String codigo; private final String descripcion;
        EstadoRegularizacionEconomica(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoRegularizacionEconomica desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoRegularizacionEconomica> { public Jpa() { super(values()); } }
    }

    /** Ciclo de un token de un solo uso (recuperacion, invitacion, MFA, elevacion). */
    public enum EstadoTokenAcceso implements Codigo {
        VIGENTE(Codigos.TokenAcceso.VIGENTE, "Vigente"),
        CONSUMIDO(Codigos.TokenAcceso.CONSUMIDO, "Consumido"),
        REVOCADO(Codigos.TokenAcceso.REVOCADO, "Revocado"),
        AGOTADO(Codigos.TokenAcceso.AGOTADO, "Agotado");
        private final String codigo; private final String descripcion;
        EstadoTokenAcceso(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoTokenAcceso desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoTokenAcceso> { public Jpa() { super(values()); } }
    }

    /** Enrolamiento de un segundo factor: nace pendiente y se activa al confirmar. */
    public enum EstadoFactorAutenticacion implements Codigo {
        PENDIENTE(Codigos.FactorAutenticacion.PENDIENTE, "Pendiente"),
        ACTIVO(Codigos.FactorAutenticacion.ACTIVO, "Activo"),
        REVOCADO(Codigos.FactorAutenticacion.REVOCADO, "Revocado");
        private final String codigo; private final String descripcion;
        EstadoFactorAutenticacion(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoFactorAutenticacion desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoFactorAutenticacion> { public Jpa() { super(values()); } }
    }

    /** Concesion de emergencia (break-glass): la abren dos custodios y caduca sola. */
    public enum EstadoConcesionRecuperacion implements Codigo {
        PENDIENTE(Codigos.ConcesionRecuperacion.PENDIENTE, "Pendiente"),
        VIGENTE(Codigos.ConcesionRecuperacion.VIGENTE, "Vigente"),
        CERRADA(Codigos.ConcesionRecuperacion.CERRADA, "Cerrada"),
        CADUCADA(Codigos.ConcesionRecuperacion.CADUCADA, "Caducada"),
        AGOTADA(Codigos.ConcesionRecuperacion.AGOTADA, "Agotada");
        private final String codigo; private final String descripcion;
        EstadoConcesionRecuperacion(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
        public String codigo() { return codigo; } public String descripcion() { return descripcion; }
        public static EstadoConcesionRecuperacion desde(String codigo) { return EstadosDominio.desde(values(), codigo); }
        @Converter public static final class Jpa extends Conversor<EstadoConcesionRecuperacion> { public Jpa() { super(values()); } }
    }
}
