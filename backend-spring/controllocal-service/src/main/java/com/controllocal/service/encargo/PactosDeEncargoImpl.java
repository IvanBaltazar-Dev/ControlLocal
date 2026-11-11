package com.controllocal.service.encargo;

import com.controllocal.persistence.repositorio.DocumentoEncargoRepository;
import com.controllocal.persistence.repositorio.RevisionEncargoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import static com.controllocal.service.encargo.ConstanciasDeFirmaDeEncargo.*;

/** RF055–064/CAT-E14: la regla procede del documento de la aprobación elegida, no del vigente. */
@Service
@Transactional(readOnly=true)
public class PactosDeEncargoImpl implements PactosDeEncargo {
    private final AccesoEncargo acceso;
    private final RevisionEncargoRepository revisiones;
    private final DocumentoEncargoRepository documentos;
    private final ConstanciasDeFirmaDeEncargo constancias;
    private final Documentos json;
    private final ObjectMapper mapper;
    public PactosDeEncargoImpl(AccesoEncargo acceso,RevisionEncargoRepository revisiones,
            DocumentoEncargoRepository documentos,ConstanciasDeFirmaDeEncargo constancias,
            Documentos json,ObjectMapper mapper) {
        this.acceso=acceso;this.revisiones=revisiones;this.documentos=documentos;
        this.constancias=constancias;this.json=json;this.mapper=mapper;
    }
    @Override
    public PactoFirmado consultarPactoFirmado(ReferenciaPacto ref,Actor actor) {
        Alcances.exigirOperador(actor);
        if(ref==null)throw new ReglaNegocioException("Identifica el Encargo y la revisión aprobatoria del pacto.");
        acceso.exigirLectura(ref.encargo(),actor);
        var r=revisiones.findByOrganizacionIdAndId(actor.idOrganizacion(),ref.revisionAprobatoria())
                .filter(x->Objects.equals(x.getIdEncargo(),ref.encargo()))
                .orElseThrow(()->new NoEncontradoException("Revisión del pacto"));
        var firma=constancias.firmaAprobada(r,actor,LecturaSnapshot.EXPEDIENTE);
        var d=documentos.findByOrganizacionIdAndId(actor.idOrganizacion(),firma.documento())
                .orElseThrow(()->new IllegalStateException("Falta el documento aprobado del pacto."));
        if(!Objects.equals(d.getIdEncargo(),ref.encargo())
                || firma.versionContenido()!=r.getVersionEncargo() || !Objects.equals(d.getHuella(),firma.huellaMaterial()))
            throw new IllegalStateException("El documento no conserva el contenido de esa aprobación.");
        var presentacion=revisiones.findByOrganizacionIdAndId(actor.idOrganizacion(),firma.revisionPresentacion())
                .orElseThrow(()->new IllegalStateException("Falta la presentación aprobada del pacto."));
        if(!Objects.equals(r.getHuellaCobertura(),presentacion.getHuellaCobertura()))
            throw new IllegalStateException("La aprobación no cubre la presentación manuscrita original.");
        var contenido=json.comoMapa(d.getContenido());
        if(contenido.get("condiciones")==null)
            throw new ReglaNegocioException("El documento firmado no conserva un pacto de comisión utilizable.");
        var c=mapper.convertValue(contenido.get("condiciones"),EncargoService.Condiciones.class);
        var regla=new ReglaComision(c.tipoComision(),c.baseCalculo(),c.valorComision(),c.monedaComision(),
                c.tratamientoIgv(),c.motivoSinComision(),c.hitoDevengo(),c.parteObligada(),c.beneficiario(),
                c.exigibilidad(),c.procedencia(),c.tramos()==null?List.of():c.tramos(),c.ejecucion());
        acceso.exigirLectura(ref.encargo(),actor);
        return new PactoFirmado(ref,r.getVersionEncargo(),new AcreditacionPacto(d.getId(),r.getIdPeticion(),
                firma.revisionPresentacion(),firma.huellaMaterial(),r.getHuellaCobertura(),firma.huellaManifestacion()),regla);
    }
}
