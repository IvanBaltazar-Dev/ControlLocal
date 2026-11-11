package com.controllocal.asistencia.conversacion;

import com.controllocal.asistencia.herramientas.Herramienta;
import com.controllocal.asistencia.herramientas.Herramientas;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * <b>Lo que KAIROS sabe pedirle a BROX.</b>
 *
 * <h2>Que declara este enum, y que NO declara</h2>
 * Declara <b>que sabe decir</b> KAIROS: las intenciones que puede reconocer en
 * una frase y traducir a llamadas. Nada mas.
 *
 * <p>No declara quien puede pedirlas. Eso lo decide BROX con la sesion de la
 * persona, y lo publica mediante {@code GET /capacidades} en cada turno.
 * Una version anterior de esta clase llevaba la autonomia dentro y era
 * exactamente el error de tener la regla en el sitio donde nadie la audita.
 *
 * <h2>Una intencion no es una herramienta</h2>
 * <pre>
 *   intencion      lo que la persona quiere      ← este enum
 *   herramienta    lo que KAIROS invoca          ← com.controllocal.asistencia.herramientas
 *   caso de uso    lo que el dominio ya sabe     ← metodo + ruta del Core
 * </pre>
 * No son uno a uno, y forzarlo habria escondido la asimetria que mas importa:
 * <b>{@link #REGISTRAR_PROPIEDAD} usa TRES herramientas</b> —abrir, responder y
 * confirmar—, y solo la tercera crea algo. {@link #herramienta()} devuelve la
 * primera, que es la que decide si la conversacion puede siquiera empezar; las
 * otras las nombra el adaptador en el paso en que las usa.
 *
 * <p>{@link #capacidad()} es el enganche con el catalogo de BROX, y desde este
 * paquete devuelve <b>el nombre de D-K-1 §3</b>. Antes devolvia nombres propios
 * en camelCase que no estaban en ningun documento: dos vocabularios para lo
 * mismo. El contrato versionado C0/K0 publica esos IDs y su disponibilidad;
 * declarar una intencion local no concede permiso para ejecutarla.
 */
public enum Accion {

    /** La ficha por el modelo universal: titulares, atributos y encargos. */
    CONSULTAR_PROPIEDAD(Herramientas.ESTADO_DE_PROPIEDAD),

    /** El alta conversacional, sobre el motor de captura de BROX. */
    REGISTRAR_PROPIEDAD(Herramientas.ABRIR_SESION_CAPTURA),

    /** Retomar lo que quedo a medias, en este canal o en otro. */
    CONTINUAR_BORRADOR(Herramientas.CAPTURAS_EN_CURSO),

    /** Solicitar la propuesta revisable; nunca acredita aprobación humana. */
    PREPARAR_CAPTURA(Herramientas.PREPARAR_ACTUACION_CAPTURA),

    CONSULTAR_CLIENTE(Herramientas.BUSCAR_PERSONA),

    REGISTRAR_PROPIETARIO(Herramientas.REGISTRAR_PROPIETARIO),

    REGISTRAR_INTERACCION(Herramientas.REGISTRAR_INTERACCION),
    ABRIR_OPORTUNIDAD(Herramientas.ABRIR_OPORTUNIDAD),
    RECTIFICAR_INTERACCION(Herramientas.RECTIFICAR_INTERACCION),
    VINCULAR_INTERACCION(Herramientas.VINCULAR_INTERACCION),
    ANOTAR_OBSERVACION_INTERACCION(Herramientas.ANOTAR_OBSERVACION_INTERACCION),
    CONSULTAR_COMPROMISOS_PROSPECCION(Herramientas.CONSULTAR_COMPROMISOS_PROSPECCION),
    ASUMIR_COMPROMISO_PROSPECCION(Herramientas.ASUMIR_COMPROMISO_PROSPECCION),
    ACREDITAR_COMPROMISO_PROSPECCION(Herramientas.ACREDITAR_COMPROMISO_PROSPECCION),
    RESOLVER_COMPROMISO_PROSPECCION(Herramientas.RESOLVER_COMPROMISO_PROSPECCION),
    CONSULTAR_MEDICIONES_INDIVIDUALES(Herramientas.CONSULTAR_MEDICIONES_INDIVIDUALES),
    CONSULTAR_DERECHOS_INFORMES(Herramientas.CONSULTAR_DERECHOS_INFORMES),
    PREPARAR_INFORME_COMERCIAL(Herramientas.PREPARAR_INFORME_COMERCIAL),
    CONSULTAR_REPORTES_PROPIETARIO(Herramientas.CONSULTAR_REPORTES_PROPIETARIO),
    CONSULTAR_AVANCE_REPORTE(Herramientas.CONSULTAR_AVANCE_REPORTE),
    CONSULTAR_ANTECEDENTE_REPORTE(Herramientas.CONSULTAR_ANTECEDENTE_REPORTE),
    PREPARAR_REPORTE_PROPIETARIO(Herramientas.PREPARAR_REPORTE_PROPIETARIO),
    PREVISUALIZAR_REPORTE_PROPIETARIO(Herramientas.PREVISUALIZAR_REPORTE_PROPIETARIO),
    OBTENER_REPORTE_PROPIETARIO(Herramientas.OBTENER_REPORTE_PROPIETARIO),
    CONSULTAR_COMUNICACIONES_REPORTE(Herramientas.CONSULTAR_COMUNICACIONES_REPORTE),
    REGISTRAR_COMUNICACION_REPORTE_REALIZADA(Herramientas.REGISTRAR_COMUNICACION_REPORTE_REALIZADA),
    DEFINICION_DE_METAS(Herramientas.DEFINICION_DE_METAS),
    CONSULTAR_METAS_COMERCIALES(Herramientas.CONSULTAR_METAS_COMERCIALES),
    CONSULTAR_PROPUESTAS_METAS(Herramientas.CONSULTAR_PROPUESTAS_METAS),
    CONSULTAR_PROPUESTA_META(Herramientas.CONSULTAR_PROPUESTA_META),
    PROPONER_META(Herramientas.PROPONER_META),
    ESTABLECER_META(Herramientas.ESTABLECER_META),
    DECIDIR_PROPUESTA_META(Herramientas.DECIDIR_PROPUESTA_META),
    CONSULTAR_AVANCE_METAS(Herramientas.CONSULTAR_AVANCE_METAS),
    CONSULTAR_PULSO_EQUIPO(Herramientas.CONSULTAR_PULSO_EQUIPO),
    RECTIFICAR_COMUNICACION_REPORTE(Herramientas.RECTIFICAR_COMUNICACION_REPORTE),
    CONSULTAR_PROGRAMAS_PERSONALES(Herramientas.CONSULTAR_PROGRAMAS_PERSONALES),
    CONSULTAR_PROGRAMA_PERSONAL(Herramientas.CONSULTAR_PROGRAMA_PERSONAL),
    CONSULTAR_OCURRENCIAS_PROGRAMA_PERSONAL(Herramientas.CONSULTAR_OCURRENCIAS_PROGRAMA_PERSONAL),
    CONSULTAR_RESULTADO_PROGRAMA_PERSONAL(Herramientas.CONSULTAR_RESULTADO_PROGRAMA_PERSONAL),
    CONSULTAR_SEGUIMIENTOS_PROSPECCION(Herramientas.CONSULTAR_SEGUIMIENTOS_PROSPECCION),
    PLANIFICAR_SEGUIMIENTO_PROSPECCION(Herramientas.PLANIFICAR_SEGUIMIENTO_PROSPECCION),
    POSPONER_SEGUIMIENTO_PROSPECCION(Herramientas.POSPONER_SEGUIMIENTO_PROSPECCION),
    REGISTRAR_NO_CONTINUIDAD_OPORTUNIDAD(Herramientas.REGISTRAR_NO_CONTINUIDAD_OPORTUNIDAD),
    CONSULTAR_AGENDA_VISITAS(Herramientas.CONSULTAR_AGENDA_VISITAS),
    PROGRAMAR_VISITA(Herramientas.PROGRAMAR_VISITA),
    CANCELAR_VISITA(Herramientas.CANCELAR_VISITA),
    REPROGRAMAR_VISITA(Herramientas.REPROGRAMAR_VISITA),
    REGISTRAR_RESULTADO_VISITA(Herramientas.REGISTRAR_RESULTADO_VISITA),
    REGISTRAR_CONTRAOFERTA(Herramientas.REGISTRAR_CONTRAOFERTA),
    REGISTRAR_ACEPTACION_OFERTA(Herramientas.REGISTRAR_ACEPTACION_OFERTA),
    REGISTRAR_RECHAZO_OFERTA(Herramientas.REGISTRAR_RECHAZO_OFERTA),
    REGISTRAR_RETIRADA_OFERTA(Herramientas.REGISTRAR_RETIRADA_OFERTA),
    CONSULTAR_OPORTUNIDAD(Herramientas.CONSULTAR_OPORTUNIDAD),
    PRESENTAR_EXPEDIENTE_OPERACION(Herramientas.PRESENTAR_EXPEDIENTE_OPERACION),
    EVALUAR_EXPEDIENTE_OPERACION(Herramientas.EVALUAR_EXPEDIENTE_OPERACION),
    INFORMAR_HITO_CONTRACTUAL(Herramientas.INFORMAR_HITO_CONTRACTUAL),
    CONFIRMAR_HITO_CONTRACTUAL(Herramientas.CONFIRMAR_HITO_CONTRACTUAL),
    RECTIFICAR_HITO_CONTRACTUAL(Herramientas.RECTIFICAR_HITO_CONTRACTUAL),
    REGISTRAR_COBRO(Herramientas.REGISTRAR_COBRO),
    ASIGNAR_REPARTO_COMISION(Herramientas.ASIGNAR_REPARTO_COMISION),
    AUTORIZAR_BORRADOR_ADENDA(Herramientas.AUTORIZAR_BORRADOR_ADENDA),
    REPROGRAMAR_GESTION_COBRO(Herramientas.REPROGRAMAR_GESTION_COBRO),
    CONCLUIR_GESTION_COBRO(Herramientas.CONCLUIR_GESTION_COBRO),
    EVALUAR_PUBLICABILIDAD(Herramientas.EVALUAR_PUBLICABILIDAD),
    CONSULTAR_PUBLICACIONES(Herramientas.CONSULTAR_PUBLICACIONES),
    CONSULTAR_PUBLICACION(Herramientas.CONSULTAR_PUBLICACION),
    CONSULTAR_HISTORIA_PUBLICACION(Herramientas.CONSULTAR_HISTORIA_PUBLICACION),
    CREAR_BORRADOR_ANUNCIO(Herramientas.CREAR_BORRADOR_ANUNCIO),
    EDITAR_BORRADOR_ANUNCIO(Herramientas.EDITAR_BORRADOR_ANUNCIO),
    PUBLICAR_ANUNCIO(Herramientas.PUBLICAR_ANUNCIO),
    ACTUALIZAR_ANUNCIO_VISIBLE(Herramientas.ACTUALIZAR_ANUNCIO_VISIBLE),
    PAUSAR_ANUNCIO(Herramientas.PAUSAR_ANUNCIO),
    REANUDAR_ANUNCIO(Herramientas.REANUDAR_ANUNCIO),
    FINALIZAR_DIFUSION_ANUNCIO(Herramientas.FINALIZAR_DIFUSION_ANUNCIO),
    CONSULTAR_ESTADO_PIEZA(Herramientas.CONSULTAR_ESTADO_PIEZA),
    CONSULTAR_FOTOS_PERMITIDAS_PARA_PIEZA(Herramientas.CONSULTAR_FOTOS_PERMITIDAS_PARA_PIEZA),
    CONSULTAR_PLANTILLAS_Y_FORMATOS_PIEZA(Herramientas.CONSULTAR_PLANTILLAS_Y_FORMATOS_PIEZA),
    PREPARAR_PIEZA_COMERCIAL(Herramientas.PREPARAR_PIEZA_COMERCIAL),
    PREVISUALIZAR_PIEZA_COMERCIAL(Herramientas.PREVISUALIZAR_PIEZA_COMERCIAL),
    REGENERAR_PIEZA_COMERCIAL(Herramientas.REGENERAR_PIEZA_COMERCIAL),
    OBTENER_PIEZA_PARA_DIFUSION(Herramientas.OBTENER_PIEZA_PARA_DIFUSION),
    PREPARAR_MATERIAL_IA(Herramientas.PREPARAR_MATERIAL_IA),
    SOLICITAR_MATERIAL_IA(Herramientas.SOLICITAR_MATERIAL_IA),
    CONSULTAR_MATERIAL_IA(Herramientas.CONSULTAR_MATERIAL_IA),
    EVALUAR_FOTO_LOCAL(Herramientas.EVALUAR_FOTO_LOCAL),
    PRESENTAR_OFERTA(Herramientas.PRESENTAR_OFERTA),


    /** Que encaja con que. Solo lectura. */
    BUSCAR_COINCIDENCIAS(Herramientas.BUSCAR_COINCIDENCIAS),

    CONTINUAR_COINCIDENCIAS(Herramientas.CONTINUAR_COINCIDENCIAS),

    /** Razones y aptitud del candidato reautorizado en el recorrido original. */
    EXPLICAR_MATCH(Herramientas.EXPLICAR_MATCH),

    /** El foco del Inicio: lo que hay que resolver hoy, con su politica de despacho. */
    QUE_RESOLVER_HOY(Herramientas.QUE_RESOLVER_HOY);

    private final Herramienta herramienta;

    /**
     * Una intencion que apunte a una herramienta <b>declarada y sin
     * implementar</b> no arranca.
     *
     * <p>Es un error de programacion, no un caso de uso: significa que KAIROS
     * dice saber reconocer algo que despues no puede ejecutar, y la persona se
     * enteraria en mitad de la conversacion. Fallar al cargar la clase lo
     * convierte en algo que se ve al desplegar, que es cuando todavia no hay
     * nadie esperando una respuesta.
     */
    Accion(Herramienta herramienta) {
        if (!herramienta.implementada()) {
            throw new IllegalStateException(
                    "La accion " + name() + " apunta a la herramienta \"" + herramienta.nombre()
                            + "\", que esta declarada y sin implementar.");
        }
        this.herramienta = herramienta;
    }

    /** La primera herramienta que la intencion invoca. Puede no ser la unica. */
    public Herramienta herramienta() {
        return herramienta;
    }

    /** El nombre con el que BROX la publicaria en su catalogo de capacidades. */
    public String capacidad() {
        return herramienta.nombre();
    }

    public static Optional<Accion> de(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        String limpio = nombre.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(a -> a.name().equals(limpio)).findFirst();
    }
}

