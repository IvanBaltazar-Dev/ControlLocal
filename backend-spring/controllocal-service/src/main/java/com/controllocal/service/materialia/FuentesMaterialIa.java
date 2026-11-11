package com.controllocal.service.materialia;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.materialia.MaterialIaService.Solicitud;
import java.util.*;

/** Propietario de acceso, versiones, selección permitida y evidencia aplicable CAT-E44. */
public interface FuentesMaterialIa {
    enum EstadoEvidencia { PENDIENTE, ACREDITADA, RECHAZADA, REVOCADA }
    /** Identidad técnica privada realmente seleccionada; no se recibe del cliente ni concede permiso. */
    record DestinoTratamiento(String proveedor,String perfil,String versionPerfil) { }
    /** Exclusivamente servidor. El aviso genérico OPERACION_SERVICIO no acredita estos hechos. */
    record EvidenciaTratamiento(EstadoEvidencia estado,String finalidad,List<String> categorias,
            String condicionesVersion,String proveedorExplicado,String decision,
            String referenciaEvidencia,String legitimacionTerceros,String preferenciaVersion) {
        public EvidenciaTratamiento { categorias=categorias==null?List.of():List.copyOf(categorias); }
    }
    /**
     * Persistida privada; prueba jurídica y procedencia no forman parte del payload al proveedor.
     * instruccionPermitida procede de la minimización de la fuente, nunca de un fallback al texto libre.
     * Puede faltar en una preparación local; sin ella no se transfiere al proveedor.
     */
    record Snapshot(String versionOrigen,String huellaOrigen,Map<String,Object> datosPermitidos,
            String instruccionPermitida,Map<String,Object> utilidadLocal,List<String> pendientesLocales,EvidenciaTratamiento evidencia) {
        public Snapshot {
            datosPermitidos=Map.copyOf(datosPermitidos);utilidadLocal=Map.copyOf(utilidadLocal);
            pendientesLocales=List.copyOf(pendientesLocales);
        }
    }
    Snapshot preparar(Solicitud solicitud,Actor actor,ContextoSesion sesion);
    /** B0 ya comprueba SID; este método vuelve a comprobar autoridad/finalidad/versión y evidencia. */
    void revalidar(Solicitud solicitud,Snapshot snapshot,Actor actor);
    /** Lectura de antecedente: permiso/original/E44 actuales, sin exigir frescura de otros datos físicos. */
    void exigirLecturaHistorica(Solicitud solicitud,Snapshot snapshot,Actor actor);
    /** Artefacto del recurso: uso legítimo actual y evidencia histórica, sin heredar el job privado. */
    void exigirLecturaArtefacto(Solicitud solicitud,Snapshot snapshot,Actor actor);
    /** Proyección en la TX actual: denegaciones esperadas como valor; fallos técnicos se propagan. */
    EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto evaluarLecturaArtefacto(Solicitud solicitud,Snapshot snapshot,Actor actor);
    /** Comprueba cobertura real E44 sobre payload y destino justo antes de invocar; sin default permisivo. */
    void exigirTransferencia(Solicitud solicitud,Snapshot snapshot,DestinoTratamiento destino,Actor actor,ContextoSesion sesion);
    /** Sólo copia minimizada autorizada, nunca EXIF del original; ejecución fuera de SQL. */
    byte[] fotoParaProcesar(Solicitud solicitud,Snapshot snapshot,Actor actor,ContextoSesion sesion);
}
