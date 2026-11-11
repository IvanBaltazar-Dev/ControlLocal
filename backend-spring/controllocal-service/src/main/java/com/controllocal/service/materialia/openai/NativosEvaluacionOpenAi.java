package com.controllocal.service.materialia.openai;
import com.controllocal.service.materialia.*;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
/** OFF. No configura perfiles comerciales, modelos alternativos, grants ni tarifas por defecto. */
@Component
@ConditionalOnProperty(name={"controllocal.ia.gasto-externo-habilitado","controllocal.material-ia.evaluacion.habilitada"},havingValue="true",matchIfMissing=false)
public class NativosEvaluacionOpenAi implements NativosEvaluacionMaterialIa {
    private final Environment entorno;private final EvaluacionTecnicaFoto evaluador;
    public NativosEvaluacionOpenAi(Environment entorno,EvaluacionTecnicaFoto evaluador){this.entorno=entorno;this.evaluador=evaluador;}
    public Sesion abrir(ConfiguracionEdicionOpenAi gen,ConfiguracionQcOpenAi qc){
        if(!entorno.getProperty("controllocal.ia.gasto-externo-habilitado",Boolean.class,false)
            ||!entorno.getProperty("controllocal.material-ia.evaluacion.habilitada",Boolean.class,false))
            throw new com.controllocal.service.excepcion.CapacidadDeshabilitadaException();
        ProveedorEdicionOpenAi.Credencial credencial=()->entorno.getProperty("OPENAI_API_KEY");
        var g=new TransporteHttpOpenAi(gen.conexion());
        try{
            var q=new TransporteHttpOpenAi(qc.conexion(),TransporteHttpOpenAi.Destino.RESPUESTAS);
            return new Sesion(new ProveedorEdicionOpenAi(gen,credencial,g),new ControlQcOpenAi(qc,credencial,q,evaluador),()->{try{q.close();}finally{g.close();}});
        }catch(RuntimeException fallo){g.close();throw fallo;}
    }
}
