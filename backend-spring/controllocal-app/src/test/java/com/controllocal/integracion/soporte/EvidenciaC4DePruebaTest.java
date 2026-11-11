package com.controllocal.integracion.soporte;

import com.controllocal.service.Actor;
import com.controllocal.service.PropietarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Sólo verifica el formato TEST; los IDs literales no acreditan una autorización. */
class EvidenciaC4DePruebaTest {
    @Test @DisplayName("DATOS_PRIVADOS_NO_EMITIR")
    void soloEmiteCamposTecnicosExplicitosYNuncaSerializaElDto(TestInfo info) throws Exception {
        var anterior=System.out;var bytes=new ByteArrayOutputStream();
        var salida=new PrintStream(bytes,true,StandardCharsets.UTF_8);
        try {
            System.setOut(salida);EvidenciaC4DePrueba.iniciar(info);
            var actor=new Actor(11,22,33,Actor.AGENTE);
            EvidenciaC4DePrueba.actor(actor);
            EvidenciaC4DePrueba.resultado("REGISTRAR_PROPIETARIO",actor,
                    new PropietarioService.FichaPropietario(44L,"N","D","DOCUMENTO_PRIVADO",
                            "NOMBRE_PRIVADO","TELEFONO_PRIVADO","CORREO_PRIVADO","A",true,null,0));
        } finally {
            try {EvidenciaC4DePrueba.limpiar();}
            finally {System.setOut(anterior);salida.close();}
        }
        var texto=bytes.toString(StandardCharsets.UTF_8);
        assertFalse(texto.contains("PRIVADO"));assertFalse(texto.contains("DATOS_PRIVADOS_NO_EMITIR"));
        var permitidos=Set.of("caso","fase","accion","organizacion","personaActor","rolActor","atribucion",
                "tipoRecurso","idRecurso","versionRecurso","propuestaRef","versionPropuesta","estadoCore",
                "estadoRecurso","idOportunidad","idEncargo","idInteraccion","versionMesa","idAcuerdo","versionAcuerdo","pid","txid");
        var lineas=texto.lines().toList();assertEquals(4,lineas.size());
        var json=new ObjectMapper();
        for(var linea:lineas) {
            assertTrue(linea.startsWith("C4_EVIDENCIA "));
            var nodo=json.readTree(linea.substring("C4_EVIDENCIA ".length()));
            nodo.fieldNames().forEachRemaining(campo->assertTrue(permitidos.contains(campo),campo));
            assertTrue(nodo.path("caso").asText().endsWith(".soloEmiteCamposTecnicosExplicitosYNuncaSerializaElDto"));
        }
        var resultado=json.readTree(lineas.get(2).substring("C4_EVIDENCIA ".length()));
        assertEquals(44,resultado.path("idRecurso").asLong());assertEquals(11,resultado.path("organizacion").asLong());
    }

    @Test void unObjetoNoAdmitidoNoSeSerializaYLimpiarRetiraElCaso(TestInfo info) {
        var anterior=System.out;var bytes=new ByteArrayOutputStream();
        var salida=new PrintStream(bytes,true,StandardCharsets.UTF_8);
        try {
            System.setOut(salida);EvidenciaC4DePrueba.iniciar(info);
            var actor=new Actor(11,22,33,Actor.AGENTE);
            var privado=new Object(){@Override public String toString(){throw new AssertionError("No serializar");}};
            assertThrows(IllegalArgumentException.class,()->EvidenciaC4DePrueba.resultado("RESULTADO",actor,Map.of("privado",privado)));
            assertThrows(IllegalArgumentException.class,()->EvidenciaC4DePrueba.estado("NO ES UN CODIGO",actor,"A"));
            EvidenciaC4DePrueba.limpiar();var antes=bytes.toString(StandardCharsets.UTF_8);
            EvidenciaC4DePrueba.actor(actor);
            EvidenciaC4DePrueba.resultado("RESULTADO",actor,privado);
            assertEquals(antes,bytes.toString(StandardCharsets.UTF_8));
        } finally {
            try {EvidenciaC4DePrueba.limpiar();}
            finally {System.setOut(anterior);salida.close();}
        }
    }

    @Test void cobroYGestionC5EmitenSoloIdentidadesSinImportesMotivosNiObjetoPrivado(TestInfo info) {
        var anterior=System.out;var bytes=new ByteArrayOutputStream();var salida=new PrintStream(bytes,true,StandardCharsets.UTF_8);
        try {
            System.setOut(salida);EvidenciaC4DePrueba.iniciar(info);var actor=new Actor(11,22,33,Actor.BROKER);
            var fecha=java.time.LocalDate.of(2026,10,31);var registro=java.time.OffsetDateTime.parse("2026-10-31T17:00:00Z");
            EvidenciaC4DePrueba.resultado("REGISTRAR_COBRO",actor,new com.controllocal.service.ComisionService.ResultadoCobro(
                    51,2,61,new com.controllocal.service.ComisionService.Dinero(new java.math.BigDecimal("1225.55"),"PEN"),fecha,registro,null));
            EvidenciaC4DePrueba.resultado("CONCLUIR_GESTION",actor,new com.controllocal.service.ComisionService.ResultadoGestion(
                    51,3,new com.controllocal.service.ComisionService.GestionCobro(true,null,"MOTIVO_PRIVADO",registro)));
        } finally {try {EvidenciaC4DePrueba.limpiar();}finally {System.setOut(anterior);salida.close();}}
        var texto=bytes.toString(StandardCharsets.UTF_8);assertFalse(texto.contains("1225.55"));assertFalse(texto.contains("PRIVADO"));
        assertFalse(texto.contains("2026-10-31"));assertFalse(texto.contains("PEN"));
        assertTrue(texto.contains("\"idMovimiento\":61"));assertTrue(texto.contains("\"idRecurso\":51"));
    }

    @Test void publicacionEmiteSoloIdentidadVersionEstadoYEncargo(TestInfo info)throws Exception{
        var anterior=System.out;var bytes=new ByteArrayOutputStream();var salida=new PrintStream(bytes,true,StandardCharsets.UTF_8);
        try{
            System.setOut(salida);EvidenciaC4DePrueba.iniciar(info);
            var fecha=java.time.LocalDateTime.of(2026,10,31,10,0);
            var f=new com.controllocal.service.PublicacionService.FichaPublicacion(51L,61L,"CANAL_PRIVADO","TITULO_PRIVADO",new java.math.BigDecimal("982345.67"),"PEN","ROTULO_PRIVADO","P","ESTADO_ROTULO_PRIVADO",fecha,fecha,"https://privado.example.invalid","ORIGEN_PRIVADO",2,3,4,null,71L,5L);
            EvidenciaC4DePrueba.resultado("PUBLICAR_ANUNCIO",new Actor(11,22,33,Actor.AGENTE),f);
        }finally{try{EvidenciaC4DePrueba.limpiar();}finally{System.setOut(anterior);salida.close();}}
        String texto=bytes.toString(StandardCharsets.UTF_8);assertFalse(texto.contains("PRIVADO"));assertFalse(texto.contains("982345.67"));assertFalse(texto.contains("PEN"));assertFalse(texto.contains("2026-10-31"));assertFalse(texto.contains("example.invalid"));
        var json=new ObjectMapper().readTree(texto.lines().filter(l->l.contains("RETORNO_CORE")).findFirst().orElseThrow().substring("C4_EVIDENCIA ".length()));
        assertEquals(51,json.path("idRecurso").asLong());assertEquals(61,json.path("idEncargo").asLong());assertEquals(2,json.path("versionRecurso").asLong());assertEquals("P",json.path("estadoRecurso").asText());
        var permitidos=Set.of("caso","fase","accion","organizacion","personaActor","rolActor","atribucion","tipoRecurso","idRecurso","versionRecurso","idEncargo","estadoRecurso");json.fieldNames().forEachRemaining(c->assertTrue(permitidos.contains(c),c));
    }
}
