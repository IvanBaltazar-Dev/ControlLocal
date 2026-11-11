package com.controllocal.service;

import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;

/** CAT-E39/AC044–046: definición gobernada y opciones, sin autoridad comercial implícita. */
public interface GobiernoCatalogoCapturaService {
    /**
     * Opción física consultada en la definición/perfil persistido del tenant.
     * La referencia explícita exige versión exacta; si falta sólo se resuelve
     * el perfil actual único de la jurisdicción expresamente declarada.
     * Una incidencia de definición no se confunde con un fallo técnico.
     */
    record ValidacionOpcion(boolean valida, String motivo, String referenciaCatalogo,
                            String codigo, String rotulo, Long versionDefinicion) { }

    ValidacionOpcion validarOpcion(long organizacion, String clave, String valor,
                                   String referenciaCatalogo, String jurisdiccion);

    record OpcionCatalogo(String codigo, String rotulo) { }
    record OpcionesCatalogo(String referenciaCatalogo, Long versionDefinicion,
                            List<OpcionCatalogo> opciones, String motivo,
                            String jurisdiccion, String versionFuente) {
        /** Catálogo no territorial: no acredita un perfil ni su fuente. */
        public OpcionesCatalogo(String referenciaCatalogo, Long versionDefinicion,
                               List<OpcionCatalogo> opciones, String motivo) {
            this(referenciaCatalogo,versionDefinicion,opciones,motivo,null,null);
        }
    }

    OpcionesCatalogo opcionesVigentes(long organizacion, String clave, String jurisdiccion);

    record OpcionDefinida(String codigo,String rotulo,int orden,boolean activa) { }
    record Aplicacion(String tipoPropiedad,String operacion,String exigencia) { }
    record DatosDefinicion(String clave,String sujeto,String tipoDato,String unidad,String rotulo,
            String ayuda,String familia,int orden,BigDecimal valorMinimo,BigDecimal valorMaximo,
            Integer longitudMaxima,String jurisdiccion,String versionFuente,String fuenteDefinicion,
            List<Aplicacion> aplicaciones,List<OpcionDefinida> opciones) { }
    record Definicion(long id,String referenciaCatalogo,long version,boolean comun,boolean activa,
            Instant retiradaEn,String sustituidaPor,DatosDefinicion datos) { }
    record ResumenDefinicion(long id,String referenciaCatalogo,long version,String clave,String sujeto,
            String tipoDato,String rotulo,boolean comun,boolean activa) { }
    record DatosPerfil(long idDefinicion,String jurisdiccion,String versionFuente,String fuenteDefinicion,
            List<OpcionDefinida> opciones) { }
    record Perfil(long id,String referenciaCatalogo,long version,boolean activo,Instant retiradaEn,DatosPerfil datos) { }
    record ResumenPerfil(long id,String referenciaCatalogo,long version,long idDefinicion,
            String jurisdiccion,boolean activo) { }
    record CatalogoGobernado(List<ResumenDefinicion> definiciones,List<ResumenPerfil> perfiles) { }

    CatalogoGobernado consultar(Actor actor,ContextoSesion sesion);
    Definicion consultarDefinicion(long id,Long version,Actor actor,ContextoSesion sesion);
    Perfil consultarPerfil(long id,Long version,Actor actor,ContextoSesion sesion);
    Definicion crearDefinicion(DatosDefinicion datos,Actor actor,ContextoSesion sesion);
    Definicion versionarDefinicion(long id,long versionEsperada,DatosDefinicion datos,Actor actor,ContextoSesion sesion);
    Definicion retirarDefinicion(long id,long versionEsperada,String sustituidaPor,Actor actor,ContextoSesion sesion);
    Perfil crearPerfil(DatosPerfil datos,Actor actor,ContextoSesion sesion);
    Perfil versionarPerfil(long id,long versionEsperada,DatosPerfil datos,Actor actor,ContextoSesion sesion);
    Perfil retirarPerfil(long id,long versionEsperada,Actor actor,ContextoSesion sesion);
}
