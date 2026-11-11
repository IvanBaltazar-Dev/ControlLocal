package com.controllocal.web.controlador;

import com.controllocal.service.*;
import com.controllocal.service.GobiernoCatalogoCapturaService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GobiernoCatalogoCapturaControllerTest {
    private final GobiernoCatalogoCapturaService servicio=mock(GobiernoCatalogoCapturaService.class);
    private final GobiernoCatalogoCapturaController controller=new GobiernoCatalogoCapturaController(servicio);
    private final Actor gov=new Actor(7,30,41,Actor.TENANT_ADMIN);
    private final ContextosService.SesionAccesoDTO acceso=new ContextosService.SesionAccesoDTO("s".repeat(43),5,
            new ContextosService.IdentidadAutenticada(2,"cuenta","cuenta@example.invalid",null),
            new ContextosService.Contexto("ctx",7,30,41,Actor.TENANT_ADMIN,"EMPRESARIAL","Fixture"),
            Instant.parse("2026-10-28T10:00:00Z"),Instant.parse("2026-10-28T10:30:00Z"),EstadoDeAcceso.SIN_RESTRICCIONES);
    private final ContextoSesion sesion=ContextoSesion.de(acceso);
    @BeforeEach void preparar(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SesionDeRequest(new TokenService("").de(acceso),acceso),null,List.of()));}
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void listadoConservaActorContextoYNoStore(){
        var dto=new CatalogoGobernado(List.of(),List.of());when(servicio.consultar(gov,sesion)).thenReturn(dto);
        var r=controller.consultar();assertEquals(dto,r.getBody());assertEquals("no-store",r.getHeaders().getCacheControl());
    }
    @Test void lecturaHistoricaPasaVersionExplicitaAlCore(){controller.versionPerfil(20,3);verify(servicio).consultarPerfil(20,3L,gov,sesion);}
    @Test void lecturaActualNoInventaLaVersionCero(){controller.definicion(10);verify(servicio).consultarDefinicion(10,null,gov,sesion);}
    @Test void versionarSinVersionNoConvierteAusenciaEnCero(){
        controller.versionarPerfil(20,new GobiernoCatalogoCapturaController.VersionPerfil(null,null));
        verify(servicio).versionarPerfil(20,-1,null,gov,sesion);
    }
    @Test void retiroSinCuerpoConservaAusenciaDeVersionYSustituta(){controller.retirarDefinicion(10,null);verify(servicio).retirarDefinicion(10,-1,null,gov,sesion);}
    @Test void crearNoRecibeOrganizacionActorNiSesionEnElCuerpo(){
        var d=new DatosPerfil(10,"JURISDICCION-TEST","v1-test","Fuente ficticia",List.of(new OpcionDefinida("TEST","Ficticio",1,true)));
        controller.crearPerfil(d);verify(servicio).crearPerfil(d,gov,sesion);
        assertEquals(List.of("idDefinicion","jurisdiccion","versionFuente","fuenteDefinicion","opciones"),
                java.util.Arrays.stream(DatosPerfil.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
    }
    @Test void todasLasPuertasDeclaranGobiernoYUnaDeLasTresCapacidadesReales(){
        int puertas=0;
        for(var metodo:GobiernoCatalogoCapturaController.class.getDeclaredMethods()) {
            if(!(metodo.isAnnotationPresent(GetMapping.class)||metodo.isAnnotationPresent(PostMapping.class)||metodo.isAnnotationPresent(PutMapping.class)))continue;
            puertas++;assertEquals("hasRole('TENANT_ADMIN')",metodo.getAnnotation(PreAuthorize.class).value());
            var ids=metodo.getAnnotation(CapacidadReconocida.class).value();assertEquals(1,ids.length);
            assertTrue(List.of("consultar_catalogo_gobernado","versionar_definicion_gobernada","retirar_definicion_gobernada").contains(ids[0]));
        }
        assertEquals(11,puertas);
    }
}
