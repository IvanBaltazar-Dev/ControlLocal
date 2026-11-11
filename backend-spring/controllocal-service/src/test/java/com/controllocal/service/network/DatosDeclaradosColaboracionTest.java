package com.controllocal.service.network;

import com.controllocal.domain.colaboracion.*;
import com.controllocal.persistence.repositorio.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.controllocal.domain.colaboracion.LadoColaboracion.*;

class DatosDeclaradosColaboracionTest {
    private final IdentidadColaboracionRepository identidades=mock(IdentidadColaboracionRepository.class);
    private final CanalColaboracionRepository canales=mock(CanalColaboracionRepository.class);
    private final DatosDeclaradosColaboracion fuente=new DatosDeclaradosColaboracion(identidades,canales);
    private final Instant ahora=Instant.parse("2026-10-29T12:00:00Z");
    private SolicitudColaboracion s;
    @BeforeEach void montar(){s=SolicitudColaboracion.nueva(1,2,4,9,100,200,100,3,6,5,"oferta","{}","a".repeat(64),ahora);ReflectionTestUtils.setField(s,"id",7L);s.transicionarA("P");}
    @Test void camposNoSolicitadosNoLeenPerfilNiCanal() {
        assertTrue(fuente.deLaParte(s,DEMANDA,Set.of("operacion"),LocalDate.of(2026,10,29)).isEmpty());verifyNoInteractions(identidades,canales);
    }
    @Test void nombreProfesionalYAgenciaProcedenSoloDeSusColumnasDeclaradas() {
        var i=mock(IdentidadColaboracionRepository.Identidad.class);when(i.getNombreProfesional()).thenReturn("Ana profesional");when(i.getInmobiliaria()).thenReturn("Inmobiliaria ejemplo");when(i.getModalidad()).thenReturn("EMPRESARIAL");
        when(identidades.deResponsable(1,100,LocalDate.of(2026,10,29))).thenReturn(Optional.of(i));
        var d=fuente.deLaParte(s,DEMANDA,Set.of("nombreProfesional","inmobiliaria"),LocalDate.of(2026,10,29));
        assertEquals(Map.of("nombreProfesional","Ana profesional","inmobiliaria","Inmobiliaria ejemplo"),d);verifyNoInteractions(canales);
    }
    @Test void independienteNoInventaUnaAgenciaYNombresFaltantesNoSeRellenan() {
        var i=mock(IdentidadColaboracionRepository.Identidad.class);when(i.getNombreProfesional()).thenReturn(" ");when(i.getInmobiliaria()).thenReturn("Nombre del espacio");when(i.getModalidad()).thenReturn("INDIVIDUAL");
        when(identidades.deResponsable(1,100,LocalDate.of(2026,10,29))).thenReturn(Optional.of(i));
        assertTrue(fuente.deLaParte(s,DEMANDA,Set.of("nombreProfesional","inmobiliaria"),LocalDate.of(2026,10,29)).isEmpty());
    }
    @Test void ausenciaDeDeclaracionOmiteCanalSinImportarDatosPersonales() {
        assertTrue(fuente.deLaParte(s,DEMANDA,Set.of("canalComercial"),LocalDate.of(2026,10,29)).isEmpty());verifyNoInteractions(identidades,canales);
    }
    @Test void canalEsExactamenteLaDeclaracionDeEstaSolicitudLadoYVersion() {
        var c=CanalColaboracion.declarar(s,DEMANDA,1,100,3,"Atención comercial declarada",ahora);ReflectionTestUtils.setField(c,"id",8L);s.registrarCanal(DEMANDA,8);
        when(canales.findByOrganizacionIdAndId(1,8)).thenReturn(Optional.of(c));
        assertEquals(Map.of("canalComercial","Atención comercial declarada"),fuente.deLaParte(s,DEMANDA,Set.of("canalComercial"),LocalDate.of(2026,10,29)));verifyNoInteractions(identidades);
    }
    @Test void referenciaDeOtraSolicitudOFalloTecnicoNoProduceCanalVacio() {
        var c=CanalColaboracion.declarar(s,ENCARGO,2,200,6,"Canal ajeno",ahora);ReflectionTestUtils.setField(c,"id",8L);s.registrarCanal(DEMANDA,8);
        when(canales.findByOrganizacionIdAndId(1,8)).thenReturn(Optional.of(c));
        assertThrows(IllegalStateException.class,()->fuente.deLaParte(s,DEMANDA,Set.of("canalComercial"),LocalDate.of(2026,10,29)));
        when(canales.findByOrganizacionIdAndId(1,8)).thenThrow(new IllegalStateException("SQL"));
        assertThrows(IllegalStateException.class,()->fuente.deLaParte(s,DEMANDA,Set.of("canalComercial"),LocalDate.of(2026,10,29)));
    }
}
