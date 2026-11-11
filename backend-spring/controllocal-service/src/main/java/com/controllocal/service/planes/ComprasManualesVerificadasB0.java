package com.controllocal.service.planes;

import com.controllocal.persistence.presupuesto.ComprasManualesB0Repository;
import com.controllocal.service.soporte.Documentos;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.controllocal.service.planes.DatosCompraManual.*;

/** Única fuente manual: consentimiento y fondos durables; no usa captura/retornoWeb. */
@Component
public class ComprasManualesVerificadasB0 implements ComprasVerificadasB0 {
    private final ComprasManualesB0Repository repo;private final Documentos json;
    public ComprasManualesVerificadasB0(ComprasManualesB0Repository repo,Documentos json){this.repo=repo;this.json=json;}
    @Override public Optional<Pedido> consultar(UUID id){
        return repo.consultar(id).filter(c->c.consentidaEn()!=null&&c.fondos()!=null&&repo.verificacionRegistrada(id)).map(c->{
            var t=json.comoObjeto(c.terminos(),Terminos.class);var f=json.comoObjeto(c.fondos(),Fondos.class);var v=f.verificacion();var p=t.producto();
            return new Pedido(c.id(),c.organizacion(),p.tipo(),p.plan(),p.boost(),p.brokersExtra(),t.cicloEsperado(),t.versionCicloEsperada(),t.versionOferta(),c.huellaConsentimiento(),
                    t.corte(),v.importe(),v.moneda(),"MANUAL_WEB_MVP:"+v.origen(),v.referencia(),f.verificadosEn(),v.evidencia());
        });
    }
}
