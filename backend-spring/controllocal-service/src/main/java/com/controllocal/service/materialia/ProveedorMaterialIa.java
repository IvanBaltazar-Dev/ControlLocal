package com.controllocal.service.materialia;

import com.controllocal.service.materialia.MaterialIaService.Accion;
import com.controllocal.service.presupuesto.PresupuestoIaService.ResultadoFacturable;
import com.controllocal.service.presupuesto.PresupuestoIaService.ComprobacionNoProcesamiento;
import com.controllocal.service.planes.UsoAlmacenamientoB0.LimiteRecepcion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.*;
import java.util.function.Consumer;
import com.controllocal.service.presupuesto.ObservacionHttpIa;

/** Puerto técnico interno. No existe adaptador de red habilitado por defecto. */
public interface ProveedorMaterialIa {
    record Peticion(String claveExterna,Accion accion,Map<String,Object> datosPermitidos,String instruccion,
                    @JsonIgnore byte[] fotoAutorizada,@JsonIgnore LimiteRecepcion limiteRecepcion) {
        public Peticion {
            datosPermitidos=Map.copyOf(datosPermitidos);fotoAutorizada=fotoAutorizada==null?null:fotoAutorizada.clone();
            if(accion==Accion.MEJORAR_FOTO&&(limiteRecepcion==null||limiteRecepcion.reserva()==null
                    ||limiteRecepcion.maximoBytesContenido()<=0||limiteRecepcion.versionFuente()==null
                    ||limiteRecepcion.versionFuente().isBlank()||limiteRecepcion.huellaFuente()==null
                    ||limiteRecepcion.huellaFuente().isBlank()||limiteRecepcion.namespaceHash()==null
                    ||!limiteRecepcion.namespaceHash().matches("[0-9a-f]{64}")))
                throw new IllegalArgumentException("La mejora exige su previsión de recepción servidor vigente.");
            if(accion!=Accion.MEJORAR_FOTO&&limiteRecepcion!=null)
                throw new IllegalArgumentException("Esta acción no genera una fotografía derivada.");
        }
        @Override @JsonIgnore public byte[] fotoAutorizada(){return fotoAutorizada==null?null:fotoAutorizada.clone();}
        @Override @JsonIgnore public LimiteRecepcion limiteRecepcion(){return limiteRecepcion;}
    }
    record Salida(String texto,String contentType,@JsonIgnore byte[] foto,String proceso,String versionProceso,
                  ResultadoFacturable medicion) {
        public Salida { foto=foto==null?null:foto.clone(); }
        @Override @JsonIgnore public byte[] foto(){return foto==null?null:foto.clone();}
    }
    /** Identidad estable privada del adaptador/proveedor seleccionado. Metadata pura, sin red ni valor por defecto. */
    String identificador();
    boolean atiende(Accion accion,String perfil,String versionPerfil);
    /** Una llamada por claim comprometido; el adaptador no reintenta ni cambia la identidad. */
    Salida ejecutar(Peticion peticion,Consumer<ObservacionHttpIa> observador);
    final class ProcesamientoIncierto extends RuntimeException {
        public ProcesamientoIncierto(){super("El procesamiento conserva su operación pendiente de verificación.");}
    }
    final class NoIniciadoComprobado extends RuntimeException {
        private final ComprobacionNoProcesamiento evidencia;
        public NoIniciadoComprobado(ComprobacionNoProcesamiento evidencia){super("El procesamiento no comenzó.");this.evidencia=evidencia;}
        public ComprobacionNoProcesamiento evidencia(){return evidencia;}
    }
}
