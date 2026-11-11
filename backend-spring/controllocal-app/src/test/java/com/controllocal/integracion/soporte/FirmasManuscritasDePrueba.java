package com.controllocal.integracion.soporte;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.encargo.EncargoService.*;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.almacen.AlmacenDocumentos;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Instrumento sintetico SOLO TEST para los consumidores reales del banco y de C2/L1.
 * Conserva el PDF preparado y agrega trazos ficticios identificados como TEST.
 * No autentica ni imita la firma de una persona real; la revision del escenario
 * se declara expresamente y se confirma por la maquina humana de Actuaciones.
 */
public final class FirmasManuscritasDePrueba {
    private final EncargoService encargos;
    private final AccesoArchivoService acceso;
    private final AlmacenDocumentos almacen;

    public FirmasManuscritasDePrueba(EncargoService encargos,AccesoArchivoService acceso,AlmacenDocumentos almacen){
        this.encargos=encargos;this.acceso=acceso;this.almacen=almacen;
    }

    /** El llamante elige explicitamente firmantes/fecha; no se derivan de un UUID ni de un correo. */
    public List<FirmaManuscrita> aportar(long encargo,Documento documento,List<Firmante> firmantes,
                                       LocalDate fechaFirma,Actor actor,ContextoSesion sesion){
        Objects.requireNonNull(documento);Objects.requireNonNull(fechaFirma);
        if(firmantes==null||firmantes.isEmpty())throw new AssertionError("El escenario debe declarar sus firmantes TEST.");
        var original=acceso.resolver(documento.referencia().toString(),actor);
        var binario=almacen.abrir(original.claveProveedor()).orElseThrow(()->new AssertionError("El PDF preparado no esta disponible."));
        acceso.verificarContenido(original,binario.contenido());acceso.revalidar(original,actor);
        byte[] firmado=DocumentoManuscritoDePrueba.transformar(binario.contenido(),firmantes.stream()
                .map(f->"Firmante TEST: titularidad "+f.titularidad()+" / persona "+f.persona()).toList());
        long version=encargos.consultar(encargo,actor).version();
        var carga=encargos.cargarArchivo(new CargaArchivo(encargo,documento.id(),"ACEPTACION_ENCARGO",
                "instrumento-manuscrito-TEST.pdf","application/pdf",firmado,version),actor,sesion,clave(),Procedencia.deLaPantalla());
        var archivo=encargos.adjuntarOReutilizar(new VinculoArchivo(carga.referencia(),encargo,documento.id(),
                "ACEPTACION_ENCARGO",version),actor,sesion,clave(),Procedencia.deLaPantalla());
        return firmantes.stream().map(f->new FirmaManuscrita(f,fechaFirma,archivo.referencia(),
                "Instrumento ficticio TEST del documento "+documento.id()+"; trazo sintetico de ensayo, sin autenticar firma real.")).toList();
    }

    private static String clave(){return "FIRMA-MANUSCRITA-TEST-"+UUID.randomUUID();}
}
