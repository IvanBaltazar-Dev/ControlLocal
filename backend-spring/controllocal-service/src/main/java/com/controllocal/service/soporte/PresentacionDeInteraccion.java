package com.controllocal.service.soporte;

import com.controllocal.service.InteraccionService.FichaInteraccion;
import com.controllocal.service.actividad.CatalogoInteracciones.CanalComunicacion;

/** Rótulos sobre una proyección ya autorizada; no consulta relaciones ni autoriza recursos. */
public final class PresentacionDeInteraccion {
    private PresentacionDeInteraccion() { }

    public static String titulo(FichaInteraccion ficha) {
        var datos = ficha.datos();
        String titulo = datos.tipo() == null ? "Interacción" : datos.tipo().rotulo();
        String canal = canal(ficha);
        if (canal != null) titulo += " · " + canal;
        if (datos.interlocutor() != null && datos.interlocutor().nombre() != null
                && !datos.interlocutor().nombre().isBlank()) {
            titulo += " con " + datos.interlocutor().nombre();
        }
        return titulo;
    }

    public static String resultado(FichaInteraccion ficha) {
        var datos = ficha.datos();
        if (datos.resultado() == null) return ficha.resultadoHistorico();
        return ficha.rotuloResultado();
    }

    public static String estado(FichaInteraccion ficha) {
        return ficha.datos().resultado() == null ? ficha.resultadoHistorico()
                : ficha.datos().resultado().name();
    }

    public static String detalle(FichaInteraccion ficha) {
        String resumen=ficha.datos().resumen();
        String transcripcion=ficha.datosLegados()==null?null:ficha.datosLegados().transcripcionNota();
        if(transcripcion==null || transcripcion.isBlank() || transcripcion.equals(resumen))return resumen;
        if(resumen==null || resumen.isBlank())return transcripcion;
        return resumen+"\n"+transcripcion;
    }

    private static String canal(FichaInteraccion ficha) {
        var datos = ficha.datos();
        if (datos.canal() != null) {
            return datos.canal() == CanalComunicacion.OTRO_IDENTIFICADO
                    ? datos.medioReal() : datos.canal().rotulo();
        }
        String anterior = ficha.canalHistorico();
        if (anterior == null) return null;
        // Lectura del diccionario anterior, sin convertirlo en un código nuevo de C4.
        return switch (anterior) {
            case "L" -> "Llamada";
            case "W" -> "WhatsApp";
            case "E" -> "Email";
            case "P" -> "Presencial";
            case "R" -> "Reunión";
            case "T" -> "Portal";
            case "O" -> "Otro";
            default -> anterior;
        };
    }
}
