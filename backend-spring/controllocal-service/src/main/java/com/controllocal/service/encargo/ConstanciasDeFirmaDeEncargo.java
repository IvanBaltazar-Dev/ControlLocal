package com.controllocal.service.encargo;

import com.controllocal.domain.comercial.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import static com.controllocal.service.encargo.EncargoService.*;

/** Una sola comprobación durable de la manifestación manuscrita y sus referencias originales. */
@Component
public final class ConstanciasDeFirmaDeEncargo {
    enum LecturaSnapshot { EXPEDIENTE, INTEGRIDAD_L1, INTEGRIDAD_COMPARTIDA, INTEGRIDAD_CALENDARIO }
    static final String CONTRATO_FIRMA="MANUSCRITA-C2-20261030-R1";
    static final String MODALIDAD_FIRMA="DOCUMENTO_FIRMADO";
    private final ArchivoEncargoRepository archivos;
    private final RevisionEncargoRepository revisiones;
    private final DocumentoEncargoRepository documentosEncargo;
    private final AccesoArchivoService accesoArchivos;
    private final AccesoEncargo acceso;
    private final Documentos json;
    private final ObjectMapper mapper;
    public ConstanciasDeFirmaDeEncargo(ArchivoEncargoRepository archivos,RevisionEncargoRepository revisiones,
            DocumentoEncargoRepository documentosEncargo,AccesoArchivoService accesoArchivos,
            AccesoEncargo acceso,Documentos json,ObjectMapper mapper) {
        this.archivos=archivos;this.revisiones=revisiones;this.documentosEncargo=documentosEncargo;
        this.accesoArchivos=accesoArchivos;this.acceso=acceso;this.json=json;this.mapper=mapper;
    }
    ArchivoEncargoRepository.ReferenciaActual archivoFirmaActual(UUID ref,long encargo,long documento,Actor actor,LecturaSnapshot lectura) {
        return archivoFirmaActual(ref,encargo,documento,actor,lectura,actor.idOrganizacion());
    }
    private ArchivoEncargoRepository.ReferenciaActual archivoFirmaActual(UUID ref,long encargo,long documento,Actor actor,LecturaSnapshot lectura,long organizacionFuente) {
        var permiso=lectura==LecturaSnapshot.EXPEDIENTE?accesoArchivos.resolver(ref.toString(),actor):null;
        var actual=archivos.actual(organizacionFuente,ref).orElseThrow(()->new NoEncontradoException("Documento firmado"));
        if((permiso!=null && (!"ARCHIVO_ENCARGO".equals(permiso.tipo()) || permiso.idReferencia()!=actual.getId()))
                || !Objects.equals(actual.getEncargo(),encargo) || !Objects.equals(actual.getDocumento(),documento)
                || !ArchivoEncargo.ACEPTACION.equals(actual.getFinalidad()))throw new AccesoNoAutorizadoException();
        if(permiso!=null && !Objects.equals(permiso.huellaContenido(),actual.getHuella()))
            throw new ConflictoException("El documento firmado cambió durante la comprobación de su vínculo.");
        return actual;
    }
    Optional<RevisionEncargo> revisionDeFirma(long org,long encargo,long peticion,long id) {
        return revisiones.findByOrganizacionIdAndId(org,id).filter(r->Objects.equals(r.getIdEncargo(),encargo)
                && Objects.equals(r.getIdPeticion(),peticion) && Set.of("PRESENTAR","REENVIAR").contains(r.getActuacion()));
    }
    Map<String,Object> constanciaConservada(RevisionEncargo r) {
        Object contenido=json.comoMapa(r.getSnapshot()).get("firmaManuscrita");
        if(contenido==null)return null;
        if(!(contenido instanceof Map<?,?>))throw new IllegalStateException("La constancia manuscrita está dañada.");
        Map<String,Object> constancia=mapper.convertValue(contenido,new TypeReference<>(){});
        Map<String,Object> material=new LinkedHashMap<>(constancia);Object hash=material.remove("huellaManifestacion");
        if(!CONTRATO_FIRMA.equals(material.get("versionContrato")) || !MODALIDAD_FIRMA.equals(material.get("modalidad"))
                || !Objects.equals(hash,huella(material)))throw new IllegalStateException("La constancia manuscrita no conserva su identidad.");
        return constancia;
    }
    FirmaPresentada firmaPresentada(PeticionRevisionEncargo p,Actor actor,LecturaSnapshot lectura) {
        if(p.getIdUltimaRevision()==null)return null;
        var ultima=revisiones.findByOrganizacionIdAndId(p.getOrganizacionId(),p.getIdUltimaRevision())
                .orElseThrow(()->new IllegalStateException("Falta el antecedente de la petición."));
        if(!Objects.equals(ultima.getIdEncargo(),p.getIdEncargo()) || !Objects.equals(ultima.getIdPeticion(),p.getId()))
            throw new IllegalStateException("La revisión no pertenece a la petición.");
        long origen;
        if(Set.of("PRESENTAR","REENVIAR").contains(ultima.getActuacion()))origen=ultima.getId();
        else {
            var snapshot=json.comoMapa(ultima.getSnapshot());
            if(snapshot.get("revisionPresentacionFirma")==null)return null;
            origen=numero(snapshot,"revisionPresentacionFirma");
        }
        var r=revisionDeFirma(p.getOrganizacionId(),p.getIdEncargo(),p.getId(),origen)
                .orElseThrow(()->new IllegalStateException("Falta la presentación original de la constancia."));
        return reconstruir(r,p.getIdEncargo(),p.getIdDocumento(),p.getVersionPresentada(),referencias(p.getEvidencias()),actor,lectura);
    }
    /** Resuelve la presentación histórica de ESTA aprobación; nunca la petición o revisión vigentes. */
    FirmaPresentada firmaAprobada(RevisionEncargo aprobacion,Actor actor,LecturaSnapshot lectura) {
        if(!"APROBAR".equals(aprobacion.getActuacion()) || aprobacion.getIdPeticion()==null
                || aprobacion.getIdSolicitudFin()!=null)throw new ReglaNegocioException("La revisión no acredita un pacto firmado.");
        Map<String,Object> s=json.comoMapa(aprobacion.getSnapshot());
        if(s.get("peticion")==null || s.get("revisionPresentacionFirma")==null)
            throw new ReglaNegocioException("La aprobación histórica no conserva la evidencia de la manifestación manuscrita exacta.");
        Peticion p=mapper.convertValue(s.get("peticion"),Peticion.class);
        if(p.encargo()!=aprobacion.getIdEncargo() || !Objects.equals(p.id(),aprobacion.getIdPeticion())
                || p.versionPresentada()!=aprobacion.getVersionEncargo())
            throw new IllegalStateException("La aprobación no conserva su petición y contenido originales.");
        var origen=revisionDeFirma(aprobacion.getOrganizacionId(),p.encargo(),p.id(),numero(s,"revisionPresentacionFirma"))
                .orElseThrow(()->new IllegalStateException("Falta la presentación manuscrita original de la aprobación."));
        FirmaPresentada firma=reconstruir(origen,p.encargo(),p.documento(),p.versionPresentada(),p.evidencias(),actor,lectura);
        if(firma==null)throw new ReglaNegocioException("La aprobación histórica no acredita firma manuscrita.");
        RevisionManuscrita revision=s.get("revisionManuscrita")==null?null:mapper.convertValue(s.get("revisionManuscrita"),RevisionManuscrita.class);
        exigirRevisionManuscrita(revision,firma);
        return firma;
    }
    private FirmaPresentada reconstruir(RevisionEncargo r,long encargo,long documento,long version,List<UUID> originales,
                                         Actor actor,LecturaSnapshot lectura) {
        var m=constanciaConservada(r);
        if(m==null)return null;
        autorizarConstancia(m,encargo,actor,lectura,r.getOrganizacionId());
        List<FirmaManuscrita> firmas=mapper.convertValue(m.get("firmas"),new TypeReference<>(){});
        List<ArchivoFirma> referencias=mapper.convertValue(m.get("archivos"),new TypeReference<>(){});
        if(numero(m,"documento")!=documento || numero(m,"versionContenido")!=version
                || !new HashSet<>(firmas.stream().map(FirmaManuscrita::documentoFirmado).toList()).equals(new HashSet<>(originales)))
            throw new IllegalStateException("La firma conservada no corresponde al documento presentado.");
        return new FirmaPresentada(r.getId(),MODALIDAD_FIRMA,numero(m,"documento"),numero(m,"versionContenido"),
                (String)m.get("huellaMaterial"),firmas,referencias,(String)m.get("huellaManifestacion"));
    }
    private List<UUID> referencias(String evidencias) {
        try { return mapper.readValue(evidencias,new TypeReference<List<UUID>>(){}); }
        catch(Exception fallo) { throw new IllegalStateException("No se conservan las evidencias de la petición.",fallo); }
    }
    void autorizarConstancia(Map<String,Object> m,long encargo,Actor actor,LecturaSnapshot lectura) {
        autorizarConstancia(m,encargo,actor,lectura,actor.idOrganizacion());
    }
    private void autorizarConstancia(Map<String,Object> m,long encargo,Actor actor,LecturaSnapshot lectura,long organizacionFuente) {
        if(lectura==LecturaSnapshot.EXPEDIENTE)acceso.exigirLectura(encargo,actor);
        long documento=numero(m,"documento");
        if(!(m.get("archivos") instanceof List<?> referencias) || referencias.isEmpty())
            throw new IllegalStateException("Faltan los vínculos originales del documento firmado.");
        for(Object item:referencias) {
            if(!(item instanceof Map<?,?>))throw new IllegalStateException("El vínculo original no es legible.");
            Map<String,Object> esperado=mapper.convertValue(item,new TypeReference<>(){});
            UUID ref=UUID.fromString(Objects.toString(esperado.get("referencia"),""));
            var actual=archivoFirmaActual(ref,encargo,documento,actor,lectura,organizacionFuente);
            if(actual.getVersion()!=numero(esperado,"version") || !Objects.equals(actual.getHuella(),esperado.get("huella")))
                throw new ConflictoException("El vínculo del documento firmado cambió; no se entrega otra versión como original.");
        }
        var actual=documentosEncargo.contextoActual(organizacionFuente,documento)
                .orElseThrow(()->new NoEncontradoException("Documento del Encargo"));
        if(!Objects.equals(actual.getEncargo(),encargo))throw new AccesoNoAutorizadoException();
    }
    void exigirRevisionManuscrita(RevisionManuscrita revision,FirmaPresentada firma) {
        if(firma==null)throw new ReglaNegocioException("No consta la presentación del documento con firma manuscrita.");
        if(revision==null || !Boolean.TRUE.equals(revision.documentoYFirmantesRevisados()))
            throw new ReglaNegocioException("La persona competente debe revisar explícitamente el documento y sus firmantes.");
        if(revision.revisionPresentacion()!=firma.revisionPresentacion()
                || !Objects.equals(revision.huellaManifestacion(),firma.huellaManifestacion()))
            throw new ConflictoException("La revisión humana no corresponde a la manifestación presentada.");
    }
    private String huella(Map<String,?> contenido) { return Idempotencia.huellaExacta(json.objetoCanonico(contenido)); }
    static long numero(Map<String,?> mapa,String clave) {
        Object valor=mapa.get(clave);
        if(!(valor instanceof Number n))throw new IllegalStateException("Falta la referencia conservada: "+clave);
        try{return new BigDecimal(n.toString()).longValueExact();}
        catch(ArithmeticException fallo){throw new IllegalStateException("La referencia conservada no es íntegra.",fallo);}
    }
}
