package com.controllocal.service.impl;

import com.controllocal.service.Actor;
import com.controllocal.service.ContratoService.PreparacionContrato;
import com.controllocal.service.PreparacionesDeContrato;
import com.controllocal.service.soporte.AccesoContrato;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;

/** No depende del orquestador de contrato, del evaluador financiero ni de Formalización. */
@Service
@Transactional(readOnly=true)
public class PreparacionesDeContratoImpl implements PreparacionesDeContrato {
    private final AccesoContrato acceso;
    private final Documentos documentos;
    public PreparacionesDeContratoImpl(AccesoContrato acceso,Documentos documentos){this.acceso=acceso;this.documentos=documentos;}
    @Override public PreparacionContrato consultar(long id,Actor actor) {
        var c=acceso.actualAutorizada(id,actor);
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("id",c.getId());m.put("estado",c.getEstado());m.put("encargo",c.getEncargo());m.put("oportunidad",c.getOportunidad());
        m.put("solicitud",c.getSolicitud());m.put("anterior",c.getAnterior());
        m.put("renta",c.getRenta()==null?null:c.getRenta().stripTrailingZeros());m.put("moneda",c.getMoneda());
        m.put("inicio",c.getInicio());m.put("fin",c.getFin());
        return new PreparacionContrato(c.getId(),c.getEstado(),c.getEncargo(),c.getOportunidad(),c.getSolicitud(),c.getAnterior(),
                c.getRenta(),c.getMoneda(),c.getInicio(),c.getFin(),Idempotencia.huellaExacta(documentos.objetoCanonico(m)));
    }
}
