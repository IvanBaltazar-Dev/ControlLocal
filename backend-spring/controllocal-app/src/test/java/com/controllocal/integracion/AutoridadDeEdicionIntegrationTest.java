package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.integracion.soporte.EncargosDePrueba;
import com.controllocal.integracion.soporte.ActuacionesHumanasC4DePrueba;
import com.controllocal.integracion.soporte.SesionesComercialesDePrueba;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.ContextosService;
import com.controllocal.service.CuentaService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.CifradoSecretos;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.controllocal.service.Actor;
import com.controllocal.service.CaptacionService;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.LocalComercialService;
import com.controllocal.service.PrecioLocalService;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.EncargoFicha;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.TraspasoDeResponsable;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El gate de la autoridad de edicion</b> (P0, V87).
 *
 * <h2>Que estaba roto</h2>
 * {@code PUT /propiedades/{id}} cargaba la fila por {@code (organizacion, id)}
 * y escribia. <b>Cualquier AGENTE del tenant editaba cualquier propiedad</b>, y
 * de paso el importe, la exclusividad y la vigencia de un ENCARGO ajeno — con
 * su hito {@code U} en la serie economica de otro. No era una via: eran ocho, y
 * siete no comprobaban nada mas que el tenant.
 *
 * <h2>Las tres autoridades, que pueden ser tres personas</h2>
 * <pre>
 *   PROPIEDAD           -> su responsable actual  -> los hechos fisicos
 *   ENCARGO de VENTA    -> su propio agente       -> ese encargo
 *   ENCARGO de ALQUILER -> su propio agente       -> ese encargo
 * </pre>
 *
 * <h2>Por que contra PostgreSQL y no en un unitario</h2>
 * Porque lo que hay que demostrar no es que un {@code if} devuelva
 * {@code false}: es que <b>no queda ninguna otra puerta</b>. Eso solo se ve
 * recorriendo las vias de verdad, con dos agentes reales del mismo tenant, y
 * mirando despues las tablas — {@code precio_propiedad} incluida, que es donde
 * aparecen los hitos que ninguna via indirecta debe poder escribir.
 *
 * <h2>Lo que este gate NO comprueba, dicho en vez de disimulado</h2>
 * No comprueba la capa web. Los {@code @PreAuthorize} son una segunda barrera y
 * tienen sus propias pruebas; aqui se ataca el <b>servicio</b>, que es la unica
 * capa que comparten BROX Web y KAIROS. Un 403 que solo existiera en la
 * anotacion no aparece en ninguna de estas pruebas — y ese es exactamente el
 * punto.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AutoridadDeEdicionIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.archivo.AccesoArchivoService archivosFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.web.almacen.AlmacenDocumentos almacenFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.actuacion.ActuacionesService actuacionesFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.soporte.Documentos documentosFirmaTest;


    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired LocalComercialService locales;
    @Autowired PrecioLocalService precios;
    @Autowired PublicacionService publicaciones;
    @Autowired CaptacionService captaciones;
    @Autowired EncargoService ciclo;
    @Autowired ContextosService contextos;
    @Autowired CuentaService cuentasPersonales;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @Autowired PlatformTransactionManager gestor;
    @Autowired com.controllocal.service.GobiernoCatalogoCapturaService catalogosPublicacion;
    private com.controllocal.integracion.soporte.PreparacionPublicacionDePrueba preparacionPublicacion(){
        return new com.controllocal.integracion.soporte.PreparacionPublicacionDePrueba(jdbc,propiedades,ciclo,
                catalogosPublicacion,contextos,cuentasPersonales,factores,cifrado,gestor,
                archivosFirmaTest,almacenFirmaTest,actuacionesFirmaTest,documentosFirmaTest);
    }
    private SesionesComercialesDePrueba sesionesFixture;
    private synchronized SesionesComercialesDePrueba sesiones() {
        if(sesionesFixture==null)sesionesFixture=new SesionesComercialesDePrueba(jdbc,contextos,cuentasPersonales,factores,cifrado);
        return sesionesFixture;
    }
    private ContextoSesion sesion(Actor actor){return sesiones().de(actor);}
    private EncargosDePrueba banco(){return new EncargosDePrueba(jdbc,ciclo,gestor, archivosFirmaTest, almacenFirmaTest, actuacionesFirmaTest, documentosFirmaTest);}
    @Autowired ProspeccionService prospecciones;
    /** La galeria salio de {@code LocalComercialService} en WP-C. */
    @Autowired FotoPropiedadService fotos;

    // ==================================================================
    // 1. La estructura: la columna, su FK y el rastro
    // ==================================================================

    @Test
    @DisplayName("la propiedad declara su responsable, con FK compuesta por organizacion")
    void laColumnaExisteYEstaAtadaAlTenant() {
        assertEquals(1, columnas("propiedad", "id_rol_responsable"),
                "sin columna propia, el responsable habria que deducirlo del encargo -- y con "
                        + "una venta y un alquiler vivos de agentes distintos esa pregunta no "
                        + "tiene respuesta");
        assertTrue(esNullable("propiedad", "id_rol_responsable"),
                "NULL es FALTANTE y tiene que poder existir: la alternativa es rellenarlo, y un "
                        + "dato que no se sabe no se rellena con el caso frecuente");
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from pg_constraint
                 where conname = 'fk_propiedad_responsable_org'
                   and contype = 'f' and array_length(conkey, 1) = 2
                """, Integer.class),
                "la FK tiene que ser compuesta por organizacion: sin el tenant en la clave, un "
                        + "rol de otra corredora entraria por la puerta de al lado");
    }

    @Test
    @DisplayName("el traspaso deja quien, cuando, de quien a quien y por que")
    void elRastroDelTraspasoTieneLasCincoCosas() {
        for (String columna : List.of("id_propiedad", "id_rol_responsable_anterior",
                "id_rol_responsable_nuevo", "id_persona_actor", "tipo_rol_actor", "motivo",
                "fecha_asignacion", "organizacion_id")) {
            assertEquals(1, columnas("asignacion_responsable_propiedad", columna),
                    "sin `" + columna + "` el traspaso no se puede auditar entero");
        }
        assertTrue(esNullable("asignacion_responsable_propiedad", "id_rol_responsable_anterior"),
                "la PRIMERA asignacion de una propiedad FALTANTE no tiene predecesor, y ese "
                        + "hueco es informacion: dice que no lo habia, en vez de nombrar al "
                        + "agente de algun encargo");
        assertFalse(esNullable("asignacion_responsable_propiedad", "motivo"),
                "sin motivo, el expediente dice que la propiedad cambio de manos y no dice "
                        + "por que");
    }

    // ==================================================================
    // 2. Otro agente del tenant NO edita lo ajeno
    // ==================================================================

    @Test
    @DisplayName("otro AGENTE del mismo tenant no edita una propiedad ajena")
    void otroAgenteNoEditaLaPropiedadAjena() {
        Actor duena = agente(0);
        Actor ajena = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");

        // CONTROL POSITIVO. Sin esto, un fallo que hiciera imposible cualquier
        // edicion dejaria la prueba siguiente verde sin proteger nada.
        propiedades.editar(idPropiedad, edicionDeFicha(idPropiedad,"Lo edita su responsable"), duena);
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(idPropiedad,
                "Prospección TEST de la otra agente: concede lectura, no edición física", null),ajena);
        assertNotNull(propiedades.consultar(idPropiedad,ajena),"El negativo de escritura debe alcanzar la guarda de edición, con lectura legítima previa");

        AccesoNoAutorizadoException error = assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.editar(idPropiedad,
                        edicionDeFicha(idPropiedad,"Lo edita quien no responde"), ajena),
                "cualquier agente del tenant editaba cualquier propiedad: es el defecto que "
                        + "abrio este P0");
        assertTrue(error.getMessage().contains("otro agente"),
                "el rechazo tiene que decir POR QUE, no solo que no. Llego: " + error.getMessage());

        assertEquals("Lo edita su responsable", descripcionDe(idPropiedad),
                "la edicion denegada no puede haber escrito nada");
    }

    @Test
    @DisplayName("ver no concede editar: el supervisor autorizado recibe la negativa de escritura")
    void verNoConcedeEditar() {
        Actor duena = agente(0);
        Actor ajena = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");

        assertThrows(NoEncontradoException.class,
                () -> propiedades.consultar(idPropiedad, ajena),
                "C1: el agente ajeno no obtiene una ficha para conocer que no puede editar");
        FichaPropiedadUniversal vista = propiedades.consultar(idPropiedad, broker());
        assertNotNull(vista.ubicacion(), "su supervisor actual si tiene lectura autorizada");
        assertFalse(vista.responsabilidad().puedeEditar(),
                "la ficha tiene que decir que no puede, no dejarselo deducir al cliente");
        assertEquals(AutoridadDePropiedad.NO_OPERA, vista.responsabilidad().motivo());
        assertNotNull(vista.responsabilidad().motivoTexto(),
                "el motivo viaja EN PALABRAS desde el Core: si lo tradujera el cliente, BROX Web "
                        + "y KAIROS acabarian con dos redacciones del mismo rechazo");

        assertTrue(propiedades.consultar(idPropiedad, duena).responsabilidad().puedeEditar(),
                "y el responsable si puede: sin esta mitad, la prueba pasaria con la ficha "
                        + "diciendo siempre que no");
    }

    // ==================================================================
    // 3. BROKER y TENANT_ADMIN no escriben hechos de la propiedad
    //    por NINGUNA de las vias
    // ==================================================================

    @Test
    @DisplayName("BROKER y TENANT_ADMIN no escriben hechos de la propiedad por ninguna via")
    void supervisarYGobernarNoEsEscribir() {
        Actor duena = agente(0);
        long idPropiedad = registrar(duena, "ALQUILER");

        for (Actor quien : List.of(broker(), tenantAdmin())) {
            String banda = quien.rolEfectivo();

            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.editar(idPropiedad, edicionDeFicha(idPropiedad,"Editado por " + banda),
                            quien),
                    banda + " no escribe la ficha por PUT /propiedades/{id}");

            assertThrows(AccesoNoAutorizadoException.class,
                    () -> locales.desactivar(idPropiedad, quien),
                    banda + " no retira la propiedad por DELETE /locales/{id}");

            assertThrows(AccesoNoAutorizadoException.class,
                    // La galeria se fue a FotoPropiedadService (WP-C) y su
                    // autoridad viajo con ella: sigue siendo la de la PROPIEDAD,
                    // y ahora vale igual por /propiedades y por /locales.
                    () -> fotos.agregar(idPropiedad, "k/" + UUID.randomUUID(), "f.jpg",
                            FotoPropiedadService.TOPE_UNIVERSAL, quien),
                    banda + " no anade fotos a la ficha");

            assertThrows(AccesoNoAutorizadoException.class,
                    () -> precios.registrar(idPropiedad,
                            new PrecioLocalService.DatosPrecio("U", "PEN", new BigDecimal("9999"),
                                    null, "ALQUILER"),
                            quien),
                    banda + " no escribe la serie economica del encargo");

            assertThrows(AccesoNoAutorizadoException.class,
                    () -> preparacionPublicacion().publicar(publicaciones,unEncargoDe(idPropiedad, "A"),
                            anuncio(), quien),
                    banda + " no publica el encargo de un agente -- y publicar escribe hito `P`");

            // La via mas directa al trato, y la que faltaba: `PUT
            // /captaciones/{id}` reescribe importe, exclusividad, vigencia,
            // urgencia y observaciones del ENCARGO. Su unica guarda era
            // `cargarConAcceso`, que para el broker supervisor y para el
            // gobierno del tenant responde SI -- solo la anotacion del
            // controlador los frenaba, y la autoridad no vive en la anotacion.
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> ciclo.editar(unEncargoDe(idPropiedad,"A"),
                            edicionDeEncargoDeAlquiler(versionDe(unEncargoDe(idPropiedad,"A")),new BigDecimal("9999"),true),
                            quien,sesion(quien),UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla()),
                    banda + " no edita el encargo por PUT /captaciones/{id}");
        }

        assertEquals(0,hitosDe(idPropiedad,"U"),"Abrir un borrador no autoriza ni publica precio en C2.");
        assertEquals(0, hitosDe(idPropiedad, "P"),
                "ninguna de las vias de gobierno puede haber dejado un hito publicado");
    }

    /**
     * <b>Y lo que el broker SI puede</b>: decidir quien responde.
     *
     * <p>Va aqui pegado a la prueba anterior a proposito. Sin esta mitad, la de
     * arriba pasaria igual si la autoridad denegara absolutamente todo al
     * broker — y entonces una propiedad FALTANTE no tendria salida y el P0
     * habria creado un callejon en vez de una regla.
     */
    @Test
    @DisplayName("lo que el broker si decide es QUIEN responde, no que dice la ficha")
    void elBrokerDecideQuienResponde() {
        Actor duena = agente(0);
        Actor otra = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");

        // La ficha ANTES, para poder afirmar que el traspaso no la toca.
        FichaPropiedadUniversal antes = propiedades.consultar(idPropiedad, duena);
        int filasAntes = filasDeTraspaso();

        TraspasoDeResponsable traspaso = propiedades.asignarResponsable(idPropiedad,
                otra.idRolOperativo(), "Rotacion de cartera del equipo",
                observado(idPropiedad), broker());

        // 1) El rastro existe y esta completo. Un traspaso sin fila seria un
        //    cambio de manos que nadie puede auditar -- y es uno de los ataques
        //    que el auditor va a intentar.
        assertEquals(filasAntes + 1, filasDeTraspaso(),
                "el traspaso tiene que dejar EXACTAMENTE una fila: ni ninguna, ni dos");
        assertEquals(duena.idRolOperativo(), traspaso.idResponsableAnterior());
        assertEquals(otra.idRolOperativo(), traspaso.idResponsableNuevo());
        assertEquals("BROKER", traspaso.rolActor(), "y con que banda actuo");
        assertNotNull(traspaso.idPersonaActor(), "y quien lo autorizo, por su persona");
        assertEquals("Rotacion de cartera del equipo", traspaso.motivo());
        assertNotNull(traspaso.fecha());

        // 2) Y NO modifica ningun atributo inmobiliario. Cambiar de responsable
        //    no cambia el inmueble: si el traspaso arrastrara datos, seria un
        //    acto de gobierno con efectos que nadie pidio.
        FichaPropiedadUniversal despues = propiedades.consultar(idPropiedad, duena);
        assertThrows(NoEncontradoException.class,
                () -> propiedades.consultar(idPropiedad, otra),
                "el traspaso de gobierno no crea una relacion lectora de prospeccion o Encargo");
        assertEquals(atributosDe(antes), atributosDe(despues),
                "el traspaso movio la autoridad y de paso un dato del inmueble");
        assertEquals(antes.descripcion(), despues.descripcion());
        assertEquals(antes.disponibilidadComercial(), despues.disponibilidadComercial());
        assertEquals(antes.titulares().size(), despues.titulares().size());
    }

    /** Un traspaso sin motivo no es auditable, y el Core lo rechaza. */
    @Test
    @DisplayName("un traspaso sin motivo se rechaza: el expediente no puede decir solo que cambio")
    void elTraspasoExigeMotivo() {
        Actor duena = agente(0);
        Actor otra = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");
        int filasAntes = filasDeTraspaso();

        assertThrows(ReglaNegocioException.class,
                () -> propiedades.asignarResponsable(idPropiedad, otra.idRolOperativo(), "  ",
                        observado(idPropiedad), broker()));
        assertEquals(filasAntes, filasDeTraspaso(), "y no deja fila a medias");
        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad),
                "ni mueve la columna sin dejar rastro, que seria lo peor de los dos mundos");

        // Y un agente no se traspasa la propiedad a si mismo: si pudiera, la
        // autoridad seria autoservicio y no seria autoridad.
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.asignarResponsable(idPropiedad, otra.idRolOperativo(),
                        "Me la quedo yo, que para eso la veo", observado(idPropiedad), otra));
        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad));
    }

    // ==================================================================
    // 4. KAIROS no obtiene mas permiso que Web, ni al reves
    // ==================================================================

    /**
     * <b>La misma regla por las dos superficies.</b>
     *
     * <p>Y no porque se hayan comprobado las dos: porque <b>es la misma</b>.
     * KAIROS no tiene escritor propio — entra por estos mismos casos de uso con
     * la cabecera {@code X-Origen}, que viaja como {@code procedencia} dentro
     * del comando. Esta prueba manda el <b>mismo comando dos veces</b>,
     * cambiando solo esa palabra, y exige la misma respuesta en los dos
     * sentidos: que KAIROS no pase donde Web no pasa, y que Web no pase donde
     * KAIROS no pasa.
     */
    @Test
    @DisplayName("KAIROS no obtiene mas permiso que Web, ni Web mas que KAIROS")
    void lasDosSuperficiesRecibenLaMismaRegla() {
        Actor duena = agente(0);
        Actor ajena = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(idPropiedad,
                "Prospección TEST lectora para comparar los dos canales", null),ajena);
        assertNotNull(propiedades.consultar(idPropiedad,ajena));

        // Los nombres REALES del vocabulario (`EventoDominio.CANALES`): SPA es
        // BROX Web y WHATSAPP es el canal por el que entra la conversacion de
        // KAIROS. "KAIROS" a secas no es un canal del Core y se rechaza con
        // 400 -- lo cual es, de paso, otra cosa que conviene que sea cierta:
        // declararse un canal inventado no es una forma de entrar.
        for (String canal : List.of("SPA", "WHATSAPP")) {
            assertThrows(AccesoNoAutorizadoException.class,
                    () -> propiedades.editar(idPropiedad,
                            edicionDeFicha(idPropiedad,"Entrada por " + canal, canal), ajena),
                    "por " + canal + " el agente ajeno tampoco edita: la autoridad vive en el "
                            + "servicio, no en la puerta, precisamente para que declararse otro "
                            + "canal no cambie nada");

            // Y el permitido pasa por los DOS. Sin esta mitad, la prueba
            // anterior seria compatible con "KAIROS no puede hacer nada".
            propiedades.editar(idPropiedad, edicionDeFicha(idPropiedad,"Entrada valida por " + canal, canal),
                    duena);
        }
        assertEquals("Entrada valida por WHATSAPP", descripcionDe(idPropiedad),
                "la ultima del bucle es la conversacional: si solo hubiera pasado la de pantalla, "
                        + "aqui se leeria SPA");
    }

    // ==================================================================
    // 5. FALTANTE: visible, no editable, con motivo
    // ==================================================================

    /**
     * <b>Sin responsable no edita nadie</b> — y no queda "de todos" en silencio.
     *
     * <p>Se llega al estado por SQL directo porque es exactamente el estado de
     * la mayoria de las propiedades tras V87 -- en {@code controllocal_dev},
     * de TODAS: la columna nacio NULL y no se rellena. La cifra exacta de la
     * base de pruebas NO se escribe aqui porque las propias suites la mueven
     * en cada corrida, y una cifra que caduca sola no es evidencia. Reproducirlo
     * escribiendo NULL es la unica forma de probar contra el dato que de verdad
     * hay.
     */
    @Test
    @DisplayName("una propiedad sin responsable es FALTANTE: visible, no editable, con motivo")
    void sinResponsableNadieEditaYLaFichaLoDice() {
        Actor duena = agente(0);
        Actor otra = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");
        dejarSinResponsable(idPropiedad);

        FichaPropiedadUniversal ficha = propiedades.consultar(idPropiedad, duena);
        assertNotNull(ficha.codigo(), "su encargo propio conserva lectura aun sin responsable fisico");
        assertNull(ficha.responsabilidad().idResponsable(),
                "y no se le inventa un dueno, ni un actor SISTEMA que tape el hueco");
        assertFalse(ficha.responsabilidad().puedeEditar());
        assertEquals(AutoridadDePropiedad.FALTA_RESPONSABLE, ficha.responsabilidad().motivo(),
                "el motivo tiene que distinguir «no hay responsable» de «hay otro»: son "
                        + "cosas distintas y se arreglan de forma distinta");

        assertThrows(NoEncontradoException.class,
                () -> propiedades.consultar(idPropiedad, otra),
                "FALTANTE no concede lectura al agente sin relacion");
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.editar(idPropiedad,edicionDeFicha(idPropiedad,"Sin dueno"),duena),
                "El Encargo concede lectura, pero FALTANTE no concede edición física");
        assertThrows(NoEncontradoException.class,
                () -> propiedades.editar(idPropiedad,edicionDeFicha(idPropiedad,"Sin dueno"),otra),
                "Sin relación lectora tampoco se revela por edición si tiene responsable físico");

        // Y el broker la saca de FALTANTE. La primera asignacion no tiene
        // anterior, y ese NULL es informacion.
        TraspasoDeResponsable primera = propiedades.asignarResponsable(idPropiedad,
                otra.idRolOperativo(), "Asignacion inicial tras la migracion",
                observado(idPropiedad), broker());
        assertNull(primera.idResponsableAnterior(),
                "no habia predecesor. Rellenarlo con el agente del encargo seria inventarle "
                        + "una procedencia al permiso");
        assertThrows(NoEncontradoException.class,
                () -> propiedades.consultar(idPropiedad, otra),
                "la asignacion inicial tampoco fabrica una relacion lectora");
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(idPropiedad,
                "Prospeccion expresa posterior a la asignacion inicial", null), otra);
        propiedades.editar(idPropiedad, edicionDeFicha(idPropiedad,"Ya tiene responsable"), otra);
        assertEquals("Ya tiene responsable", descripcionDe(idPropiedad));
    }

    /**
     * <b>FALTANTE bloquea la PROPIEDAD, no el ENCARGO.</b>
     *
     * <p>Es la mitad que impide que este P0 se convierta en un candado general.
     * Quien tiene un encargo legitimo lo sigue operando aunque nadie responda
     * por el inmueble: la autoridad del encargo es la del encargo.
     */
    @Test
    @DisplayName("sin responsable, el agente del encargo sigue operando SU encargo")
    void faltanteNoBloqueaElEncargo() {
        Actor duena = agente(0);
        long idPropiedad = registrar(duena, "ALQUILER");
        dejarSinResponsable(idPropiedad);

        // La ficha no se puede tocar...
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.editar(idPropiedad, edicionDeFicha(idPropiedad,"No"), duena));

        // ...y su encargo si.
        long idEncargo=unEncargoDe(idPropiedad,"A");
        ciclo.editar(idEncargo,edicionDeEncargoDeAlquiler(versionDe(idEncargo),new BigDecimal("3100"),false),
                duena,sesion(duena),UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());
        assertEquals(new BigDecimal("3100.00").stripTrailingZeros(),
                importeVivoDe(idPropiedad, "A").stripTrailingZeros(),
                "el encargo responde a su agente, no al responsable de la propiedad");
    }

    // ==================================================================
    // 6. Tras el traspaso, el anterior deja de poder
    // ==================================================================

    @Test
    @DisplayName("tras el traspaso el agente anterior deja de poder editar, y el nuevo puede")
    void elTraspasoMueveLaAutoridadYSoloLaAutoridad() {
        Actor antigua = agente(0);
        Actor nueva = agente(1);
        long idPropiedad = registrar(antigua, "ALQUILER");
        Long incorporoAntes = incorporoDe(idPropiedad);

        propiedades.editar(idPropiedad, edicionDeFicha(idPropiedad,"Antes del traspaso"), antigua);
        propiedades.asignarResponsable(idPropiedad, nueva.idRolOperativo(),
                "La agente anterior sale del equipo",
                observado(idPropiedad), broker());

        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.editar(idPropiedad, edicionDeFicha(idPropiedad,"Despues del traspaso"),
                        antigua),
                "el traspaso quita la autoridad al anterior. Si no, seria una suma de permisos");
        assertThrows(NoEncontradoException.class,
                () -> propiedades.consultar(idPropiedad, nueva),
                "el traspaso no concede lectura por si mismo");
        assertThrows(NoEncontradoException.class,
                () -> propiedades.editar(idPropiedad, edicionDeFicha(idPropiedad,"La escribe la nueva"), nueva),
                "sin relacion lectora no se entrega la ficha y se revierte la edicion");
        assertEquals("Antes del traspaso", descripcionDe(idPropiedad),
                "el fallo de la respuesta no deja una escritura parcial");
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(idPropiedad,
                "Prospeccion expresa posterior al traspaso", null), nueva);
        propiedades.editar(idPropiedad, edicionDeFicha(idPropiedad,"La escribe la nueva"), nueva);
        assertEquals("La escribe la nueva", descripcionDe(idPropiedad));

        // Y la historia anterior no se destruye.
        assertEquals(incorporoAntes, incorporoDe(idPropiedad),
                "`id_rol_incorporo` es procedencia historica e inmutable: quien la incorporo la "
                        + "incorporo, y el traspaso de HOY no reescribe eso");
        // El expediente acumula y DISTINGUE: el alta que la creo y el traspaso
        // que la movio. Se compara el contenido y no un recuento -- un recuento
        // no habria notado que el alta dejo de anotarse.
        assertEquals(List.of("TRASPASO", "ALTA"),
                // Se lee con el BROKER: desde C2 el expediente es superficie de
                // GOBIERNO y el AGENTE no lo abre, ni siquiera el responsable
                // vigente. Lo que esta prueba comprueba es el RASTRO, no quien
                // puede leerlo -- eso lo mide AlcanceYGobierno...
                propiedades.traspasosDe(idPropiedad, broker()).stream()
                        .map(TraspasoDeResponsable::origen).toList(),
                "un traspaso deja su fila y no borra la del alta");
    }

    // ==================================================================
    // 7. PROPIEDAD y ENCARGO son independientes en las DOS direcciones
    // ==================================================================

    @Test
    @DisplayName("traspasar la propiedad NO reasigna sus encargos")
    void elTraspasoDeLaPropiedadNoTocaLosEncargos() {
        Actor antigua = agente(0);
        Actor nueva = agente(1);
        long idPropiedad = registrarConVentaYAlquiler(antigua);
        Map<String, Long> antes = agentesDeLosEncargos(idPropiedad);

        propiedades.asignarResponsable(idPropiedad, nueva.idRolOperativo(),
                "Cambio de responsable, los encargos siguen donde estaban",
                observado(idPropiedad), broker());

        assertEquals(antes, agentesDeLosEncargos(idPropiedad),
                "cambiar quien responde por el inmueble no cambia quien negocio cada encargo: "
                        + "son autoridades distintas y pueden ser tres personas");
    }

    @Test
    @DisplayName("reasignar un ENCARGO no cambia al responsable de la propiedad")
    void laReasignacionDelEncargoNoTocaAlResponsable() {
        Actor antigua = agente(0);
        Actor nueva = agente(1);
        long idPropiedad = registrar(antigua, "ALQUILER");
        long idEncargo = unEncargoDe(idPropiedad, "A");
        Long responsableAntes = responsableDe(idPropiedad);

        long versionObservadaIdEncargo=versionDe(idEncargo);
        captaciones.reasignar(idEncargo,new CaptacionService.DatosReasignacion(nueva.idRolOperativo(),antigua.idRolOperativo(),versionObservadaIdEncargo,"El encargo pasa a otra agente"),broker(),sesion(broker()),UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());

        assertEquals(responsableAntes, responsableDe(idPropiedad),
                "reasignar el encargo movio el encargo. Si ademas moviera al responsable de la "
                        + "propiedad, las dos autoridades serian una sola con dos nombres");
        assertEquals(nueva.idRolOperativo(), agentesDeLosEncargos(idPropiedad).get("A"));
    }

    // ==================================================================
    // 8. El ENCARGO lo edita SU agente (P0-4)
    // ==================================================================

    /**
     * <b>El encargo de venta no lo edita el agente del alquiler.</b>
     *
     * <p>Es el caso que {@code controllocal_dev} ya tiene alcanzable —la
     * propiedad 3259, con los dos encargos vivos— y que el esquema permite sin
     * tocar codigo: {@code uq_captacion_viva_por_operacion} es unico por
     * {@code (id_propiedad, motivo_operacion)} y ninguna restriccion obliga a
     * que los dos sean del mismo agente.
     */
    @Test
    @DisplayName("el encargo de VENTA no lo edita el agente del ALQUILER, ni al reves")
    void cadaEncargoRespondeASuAgente() {
        Actor deVenta = agente(0);
        Actor deAlquiler = agente(1);
        long idPropiedad = registrarConVentaYAlquiler(deVenta);
        long idAlquiler = unEncargoDe(idPropiedad, "A");
        long versionObservadaIdAlquiler=versionDe(idAlquiler);
        captaciones.reasignar(idAlquiler,new CaptacionService.DatosReasignacion(deAlquiler.idRolOperativo(),deVenta.idRolOperativo(),versionObservadaIdAlquiler,"El alquiler lo lleva otra agente"),broker(),sesion(broker()),UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());
        // Y que la propiedad responda a UNA de las dos no puede decidir nada
        // sobre el encargo de la otra: es justo lo que se esta probando.
        propiedades.asignarResponsable(idPropiedad, deAlquiler.idRolOperativo(),
                "Responsable del inmueble",
                observado(idPropiedad), broker());

        int hitosVentaAntes = hitosDeEncargo(unEncargoDe(idPropiedad, "V"), "U");

        assertThrows(AccesoNoAutorizadoException.class,
                () -> ciclo.editar(unEncargoDe(idPropiedad,"V"),edicionVenta(versionDe(unEncargoDe(idPropiedad,"V")),new BigDecimal("999999"),"USD"),
                        deAlquiler,sesion(deAlquiler),UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla()),
                "ser responsable de la PROPIEDAD no concede permiso sobre el ENCARGO de otro");

        assertEquals(hitosVentaAntes, hitosDeEncargo(unEncargoDe(idPropiedad, "V"), "U"),
                "y sobre todo: no puede haber dejado un hito `U` en la serie economica ajena. "
                        + "El permiso importa; el rastro economico falso importa mas");
    }

    @Test
    @DisplayName("las condiciones comerciales de un encargo tampoco las escribe un tercero")
    void lasCondicionesDelEncargoSonDeSuAgente() {
        Actor deVenta = agente(0);
        Actor deAlquiler = agente(1);
        long idPropiedad = registrarConVentaYAlquiler(deVenta);
        long idVenta = unEncargoDe(idPropiedad, "V");
        long idAlquiler = unEncargoDe(idPropiedad, "A");
        long versionObservadaIdAlquiler=versionDe(idAlquiler);
        captaciones.reasignar(idAlquiler,new CaptacionService.DatosReasignacion(deAlquiler.idRolOperativo(),deVenta.idRolOperativo(),versionObservadaIdAlquiler,"Reparto del equipo: el alquiler pasa a otra agente"),broker(),sesion(broker()),UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());

        assertThrows(AccesoNoAutorizadoException.class,
                () -> ciclo.editar(idVenta,edicionDeCondiciones(versionDe(idVenta)),deAlquiler,sesion(deAlquiler),UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla()),
                "las condiciones gobernadas son datos del ENCARGO tanto como el importe: sin "
                        + "esto, nombrar un id ajeno bastaba para escribir en el");
    }

    /**
     * <b>Y por la OTRA puerta del mismo hecho</b>: {@code PUT /captaciones/{id}}.
     *
     * <p>Las pruebas de arriba entran al encargo por {@code PUT
     * /propiedades/{id}}, que si preguntaba. Esta entra por el recurso del
     * encargo, que escribe exactamente lo mismo —importe, exclusividad,
     * vigencia, urgencia y observaciones— y cuya unica guarda era
     * {@code cargarConAcceso}: el alcance de LECTURA. Alcanzar para ver no es
     * alcanzar para escribir, y por eso un agente del mismo equipo quedaba
     * fuera solo porque el alcance del agente es el mismo, no porque nadie lo
     * hubiera decidido.
     *
     * <p>El control positivo va <b>primero</b> y a proposito: el cuerpo de esta
     * peticion tiene tres validaciones que rechazan antes de mirar la autoridad
     * —la operacion no se edita, la vigencia es obligatoria, y la comision de un
     * alquiler se calcula sobre la renta mensual—, asi que sin demostrar antes
     * que ESE cuerpo escribe, el 403 del ajeno podria ser un 400 disfrazado.
     */
    @Test
    @DisplayName("otro AGENTE no edita un encargo ajeno por PUT /captaciones/{id}; su propio agente si")
    void elEncargoNoLoEditaOtroAgentePorLaPuertaDeCaptaciones() {
        Actor duena = agente(0);
        Actor ajena = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");
        long idEncargo = unEncargoDe(idPropiedad, "A");

        assertEquals(Boolean.FALSE, exclusividadDe(idEncargo),
                "el encargo nace sin exclusividad: es el valor que la edicion tiene que mover");

        // CONTROL POSITIVO: su propio agente si escribe, y se comprueba en la
        // base -- no en la ficha que devuelve el propio caso de uso.
        ciclo.editar(idEncargo,edicionDeEncargoDeAlquiler(versionDe(idEncargo),new BigDecimal("4321"),true),
                duena,sesion(duena),UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());
        assertEquals(0, new BigDecimal("4321").compareTo(importeVivoDe(idPropiedad, "A")),
                "el agente del encargo lo edita: sin esta mitad, la negacion de abajo pasaria "
                        + "igual en un sistema que no dejara editar a nadie");
        assertEquals(Boolean.TRUE, exclusividadDe(idEncargo));

        assertThrows(AccesoNoAutorizadoException.class,
                () -> ciclo.editar(idEncargo,edicionDeEncargoDeAlquiler(versionDe(idEncargo),new BigDecimal("999999"),false),
                        ajena,sesion(ajena),UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla()),
                "el encargo lo edita quien lo negocio (P0-4): llegar por /captaciones/{id} no "
                        + "puede ser una puerta mas barata que llegar por PUT /propiedades/{id}");

        assertEquals(0, new BigDecimal("4321").compareTo(importeVivoDe(idPropiedad, "A")),
                "y el rechazo no puede haber escrito el importe: el permiso importa, el "
                        + "importe escrito por quien no lo negocio importa mas");
        assertEquals(Boolean.TRUE, exclusividadDe(idEncargo),
                "ni la exclusividad, que se pacta con el titular y no la revoca un tercero");
    }

    // ==================================================================
    // 8 bis. D-P0-12: las capacidades del encargo las resuelve el Core,
    //        y la banda comercial se exige TAMBIEN en el Core
    // ==================================================================

    /**
     * <b>Decidir y cerrar son operaciones COMERCIALES: el gobierno no las
     * hereda</b> (D-S0-17, filas 5 y 7).
     *
     * <h2>Que estaba a medias</h2>
     * La regla estaba <b>congelada y escrita</b> —la matriz declara BROKER en
     * las dos filas— pero lo unico que la sostenia era el {@code @PreAuthorize}
     * del controlador. Una anotacion protege <b>una puerta</b>; KAIROS entra por
     * este mismo caso de uso, y {@code cargarConAcceso} le dice que si al
     * gobierno del tenant, porque para <b>leer</b> si alcanza el encargo. Es
     * exactamente la misma forma del defecto que P0-4 cerro en
     * {@code actualizar}.
     *
     * <p>Se ataca el <b>servicio</b> y no el controlador a proposito: un 403 que
     * solo existiera en la anotacion no aparece en esta prueba, y ese es el
     * punto.
     */
    @Test
    @DisplayName("D-P0-12: el TENANT_ADMIN no decide ni cierra un encargo, tampoco por el Core")
    void elGobiernoDelTenantNoDecideNiCierraEncargos() {
        Actor duena=agente(0), supervisor=broker(), gobierno=tenantAdmin();
        long idPropiedad=registrar(duena,"ALQUILER");
        long idEncargo=abrirVentaCon(idPropiedad,duena);
        prepararFisica(idPropiedad,duena);
        var pendiente=banco().presentarExistente(idEncargo,casoComercial(idPropiedad,"VENTA",duena));
        long versionPresentada=versionDe(idEncargo);
        var decision=new EncargoService.Decision("APROBAR",pendiente.peticion().version(),versionPresentada,"Revisión del fixture",
                new EncargoService.RevisionManuscrita(pendiente.peticion().firma().revisionPresentacion(),pendiente.peticion().firma().huellaManifestacion(),true));
        assertThrows(AccesoNoAutorizadoException.class,()->actuacionesFirmaTest.prepararDecisionEncargo(gobierno,pendiente.peticion().id(),decision,
                sesion(gobierno),com.controllocal.service.soporte.Procedencia.deLaPantalla()));
        assertEquals("P",estadoDelEncargo(idEncargo));
        assertEquals("PENDIENTE",ciclo.consultarPeticion(pendiente.peticion().id(),supervisor).estado());
        var propuesta=actuacionesFirmaTest.prepararDecisionEncargo(supervisor,pendiente.peticion().id(),decision,sesion(supervisor),com.controllocal.service.soporte.Procedencia.deLaPantalla());
        new ActuacionesHumanasC4DePrueba(actuacionesFirmaTest,documentosFirmaTest).confirmar(propuesta,supervisor,sesion(supervisor),EncargoService.Peticion.class);
        assertEquals("A",estadoDelEncargo(idEncargo));
        long versionAprobada=versionDe(idEncargo);
        var fin=new EncargoService.DatosFin("FINALIZACION",LocalDate.now(),"Fin solicitado en el fixture",versionAprobada,
                pendiente.peticion().evidencias());
        assertThrows(AccesoNoAutorizadoException.class,()->ciclo.finalizar(idEncargo,fin,gobierno,sesion(gobierno),
                UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla()));
        assertEquals("A",estadoDelEncargo(idEncargo));
        assertEquals(versionAprobada,versionDe(idEncargo));
    }

    /**
     * <b>Las tres capacidades, calculadas con las MISMAS guardas que los
     * comandos</b> (D-P0-12).
     *
     * <p>Se miden sobre el <b>mismo</b> encargo en sus dos estados —PENDIENTE y
     * ACTIVA— y para las cuatro identidades que lo miran. Un segundo criterio
     * "solo para pintar" es como se llega a un boton activo que el backend
     * rechaza cuando la persona ya escribio.
     *
     * <p><b>El bróker sale {@code puedeEditar=false} incluso sobre una
     * PENDIENTE</b>, y no es un olvido: por P0-4 el encargo lo edita <b>su
     * propio agente</b> —importe, exclusividad, vigencia y condiciones las
     * cambia quien las negocio—, y el bróker decide sobre el, que es otra cosa
     * y por eso es otro booleano.
     *
     * <p><b>Y la cuarta, {@code puedeReasignar}, rompe el patron de las otras
     * tres a proposito</b>: el TENANT_ADMIN <b>si</b> la tiene. Reasignar entre
     * equipos es organigrama y no operacion comercial (D-S0-17 fila 6), asi que
     * es la unica de las cuatro que el gobierno del tenant hereda — y el AGENTE
     * no la tiene ni sobre el encargo que lleva, porque quien lleva un encargo
     * no decide dejar de llevarlo.
     */
    @Test
    @DisplayName("D-P0-12: las capacidades del encargo salen del mismo predicado que los comandos")
    void lasCapacidadesDelEncargoLasResuelveElCore() {
        Actor duena=agente(0), otra=agente(1), supervisor=broker(), gobierno=tenantAdmin();
        long idPropiedad=registrar(duena,"ALQUILER");
        long idEncargo=abrirVentaCon(idPropiedad,duena);
        var propias=ciclo.consultar(idEncargo,duena).facultades();
        assertTrue(propias.editar());assertFalse(propias.decidir());assertFalse(propias.finalizar());assertFalse(propias.reasignar());
        var delBroker=ciclo.consultar(idEncargo,supervisor).facultades();
        assertFalse(delBroker.editar());assertFalse(delBroker.decidir(),"Sin petición pendiente no se anuncia revisión.");
        assertTrue(delBroker.reasignar());
        assertThrows(AccesoNoAutorizadoException.class,()->ciclo.consultar(idEncargo,gobierno));
        assertThrows(AccesoNoAutorizadoException.class,()->ciclo.consultar(idEncargo,otra));
        assertFalse(captaciones.candidatosAReasignacion(idEncargo,null,1,20,gobierno).items().isEmpty());
        prepararFisica(idPropiedad,duena);
        var pendiente=banco().presentarExistente(idEncargo,casoComercial(idPropiedad,"VENTA",duena));
        assertTrue(ciclo.consultar(idEncargo,supervisor).facultades().decidir(),"La petición real habilita esa actuación.");
        var decision=new EncargoService.Decision("APROBAR",pendiente.peticion().version(),versionDe(idEncargo),"Revisión competente del fixture",
                new EncargoService.RevisionManuscrita(pendiente.peticion().firma().revisionPresentacion(),pendiente.peticion().firma().huellaManifestacion(),true));
        var propuesta=actuacionesFirmaTest.prepararDecisionEncargo(supervisor,pendiente.peticion().id(),decision,sesion(supervisor),com.controllocal.service.soporte.Procedencia.deLaPantalla());
        new ActuacionesHumanasC4DePrueba(actuacionesFirmaTest,documentosFirmaTest).confirmar(propuesta,supervisor,sesion(supervisor),EncargoService.Peticion.class);
        assertFalse(ciclo.consultar(idEncargo,duena).facultades().editar(),"Los términos aceptados no admiten edición ordinaria.");
        assertFalse(ciclo.consultar(idEncargo,supervisor).facultades().decidir());
        assertTrue(ciclo.consultar(idEncargo,supervisor).facultades().finalizar());
        assertFalse(ciclo.consultar(idEncargo,supervisor).facultades().reasignar(),"No se reescriben términos aceptados.");
        // La fachada conservada sólo anuncia su comando vigente de reasignación; no revive las tres rutas retiradas.
        var legado=captaciones.obtener(idEncargo,supervisor);
        assertEquals(legado.capacidades(),captaciones.obtenerPorCodigo(legado.codigoCaptacion(),supervisor).capacidades());
        assertTrue(captaciones.listar(new CaptacionService.FiltrosCaptacion(null,null,null,1,20),supervisor)
                .items().stream().allMatch(f->f.capacidades()==null));
    }

    // ==================================================================
    // 9. Ninguna via indirecta escribe el historico economico ajeno
    // ==================================================================

    /**
     * <b>Las tres puertas a la serie economica de un encargo, recorridas.</b>
     *
     * <p>No basta con tapar {@code actualizarEncargo}: un hito {@code U} o
     * {@code P} se escribe tambien desde {@code POST /locales/{id}/precios} y
     * desde las <b>tres</b> operaciones de publicacion. La ultima fue un
     * hallazgo de este corte — la inventarió el gate, no el encargo.
     */
    @Test
    @DisplayName("ninguna via indirecta escribe un hito economico en un encargo ajeno")
    void ningunaViaIndirectaTocaLaSerieAjena() {
        Actor duena = agente(0);
        Actor ajena = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");
        long idEncargo = unEncargoDe(idPropiedad, "A");
        int uAntes = hitosDeEncargo(idEncargo, "U");
        int pAntes = hitosDeEncargo(idEncargo, "P");

        assertThrows(AccesoNoAutorizadoException.class,
                () -> precios.registrar(idPropiedad,
                        new PrecioLocalService.DatosPrecio("U", "PEN", new BigDecimal("1"), null,
                                "ALQUILER"),
                        ajena),
                "POST /locales/{id}/precios declaraba en la matriz «un local de sus captaciones» "
                        + "y solo comprobaba el tenant");

        assertThrows(AccesoNoAutorizadoException.class,
                () -> preparacionPublicacion().publicar(publicaciones,idEncargo, anuncio(), ajena),
                "publicar escribe un hito `P` en la serie del encargo: es la tercera puerta");

        assertEquals(uAntes, hitosDeEncargo(idEncargo, "U"), "ni un `U` de mas");
        assertEquals(pAntes, hitosDeEncargo(idEncargo, "P"), "ni un `P` de mas");

        // El positivo se habilita mediante petición, evidencia y revisión reales del fixture.
        prepararFisica(idPropiedad,duena);
        banco().habilitarExistente(idEncargo,casoComercial(idPropiedad,"ALQUILER",duena));
        preparacionPublicacion().publicar(publicaciones,idEncargo, anuncio(), duena);
        assertTrue(hitosDeEncargo(idEncargo, "P") > pAntes,
                "el agente del encargo si publica y su hito si entra");
    }

    // ==================================================================
    // 10. El alta y la frontera de informacion
    // ==================================================================


    /**
     * <b>El responsable es dato de gobierno interno.</b>
     *
     * <p>Viaja en la ficha operativa —que ya es del tenant— y no puede salir por
     * ninguna proyeccion externa. Hoy la unica que existe es {@code publicacion}
     * (lo que se anuncio: canal, titulo, importe y url), y se comprueba aqui que
     * <b>no lleva ninguna columna de gobierno</b>. La regla de construccion que
     * lo hace sostenible es que las respuestas se montan campo a campo y nunca
     * serializando la entidad.
     */
    @Test
    @DisplayName("el responsable no sale por la unica proyeccion externa que existe")
    void elResponsableNoCruzaLaFrontera() {
        for (String interna : List.of("id_rol_responsable", "id_rol_incorporo",
                "organizacion_id_interno", "id_persona_actor")) {
            assertEquals(0, columnas("publicacion", interna),
                    "`publicacion` es lo que el mercado ve: no puede llevar `" + interna + "`");
        }
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_name = 'publicacion' and column_name like '%responsable%'
                """, Integer.class),
                "ninguna columna de responsabilidad en la proyeccion externa");
    }

    // ==================================================================
    // 11. El estado de los datos que ya existen
    // ==================================================================

    /**
     * <b>La columna nacio NULL y nadie la relleno.</b>
     *
     * <p>No es un pendiente: es P0-3. La semilla no declara un responsable de la
     * propiedad —declara una captacion de AGE-001 para LOC-0001 y deja LOC-0002
     * sin ninguna—, asi que escribirlo seria derivarlo del encargo, que es
     * justo lo que se descarto. Esta prueba fija esa decision para que un
     * backfill silencioso no entre despues como "arreglo".
     */
    @Test
    @DisplayName("V87 no infirio ningun responsable de los datos que ya estaban")
    void v87NoInventoNingunResponsable() {
        // Todo responsable tiene que poder JUSTIFICARSE por una de dos vias, y
        // solo dos:
        //
        //   (a) lo puso el ALTA  -> entonces coincide con `id_rol_incorporo`,
        //       porque los dos salen del MISMO `actor.idRolOperativo()` en la
        //       misma transaccion de `registrar`;
        //   (b) lo puso un BROKER -> entonces tiene su fila de traspaso.
        //
        // Cualquier otra cosa es un valor que aparecio sin acto que lo
        // explique, y eso es exactamente lo que seria un backfill inferido: uno
        // que hubiera copiado `captacion.id_rol_agente` daria distinto de
        // `id_rol_incorporo` en cuanto la propiedad la captara otro; y uno sobre
        // filas anteriores a V76 --que no saben quien las incorporo-- daria
        // responsable con incorporo NULL.
        //
        // NO se usa `fecha_registro` como frontera temporal, y esta dicho porque
        // fue el primer intento y era falso: `sanear-residuo-de-pruebas.sql`
        // REESCRIBE esa fecha a proposito ("envejece su propiedad a frontera - 2
        // dias"), asi que hay propiedades nacidas despues de V87 con fecha
        // anterior. Ocho de ellas, todas con el mismo timestamp al segundo, que
        // es la firma de un UPDATE masivo y no de ocho altas. Un proxy temporal
        // que otro proceso reescribe no es una frontera.
        Integer sinJustificar = jdbc.queryForObject("""
                select count(*) from propiedad p
                 where p.id_rol_responsable is not null
                   and p.id_rol_responsable is distinct from p.id_rol_incorporo
                   and not exists (select 1 from asignacion_responsable_propiedad a
                                    where a.id_propiedad = p.id_propiedad)
                """, Integer.class);
        assertEquals(0, sinJustificar == null ? 0 : sinJustificar,
                "estas propiedades tienen responsable y ningun acto que lo explique: ni su alta "
                        + "(que lo dejaria igual a id_rol_incorporo) ni un traspaso de broker. "
                        + "Si aparecen filas aqui, alguien dedujo el responsable de algo -- del "
                        + "encargo vivo, de la prospeccion o de la nada -- y eso es justo lo que "
                        + "P0-3 descarto: el dato que no se sabe se declara FALTANTE");

        // CONTROL POSITIVO. El cero de arriba solo vale si la consulta MIRA
        // algo: tiene que haber propiedades con responsable, o estaria contando
        // sobre un conjunto vacio y saldria cero pase lo que pase.
        Integer conResponsable = jdbc.queryForObject(
                "select count(*) from propiedad where id_rol_responsable is not null",
                Integer.class);
        assertTrue(conResponsable != null && conResponsable > 0,
                "no hay ninguna propiedad con responsable en esta base, asi que el cero de "
                        + "arriba no ha comprobado nada. Un cero sin control positivo no es una "
                        + "medicion -- es la leccion de `grep -iF` del 2026-08-24.");

        // Y la otra mitad, que es la que fija la decision: DESPUES de V87 sigue
        // habiendo propiedades FALTANTE. Si esto llegara a cero sin que nadie
        // hubiera asignado nada, seria que alguien las relleno.
        Integer faltantes = jdbc.queryForObject(
                "select count(*) from propiedad where id_rol_responsable is null", Integer.class);
        assertTrue(faltantes != null && faltantes > 0,
                "V87 dejo la columna NULL en todo lo que ya existia, y eso es P0-3, no un "
                        + "pendiente. Si ya no queda ninguna FALTANTE, hubo un backfill.");
    }

    // ==================================================================
    // 10. El ALTA fija el responsable — y SOLO el alta de una propiedad
    //     NUEVA (decision del titular, 2026-08-30; V88)
    // ==================================================================

    @Test
    @DisplayName("propiedad NUEVA: el registrante queda responsable y el alta deja su fila")
    void elAltaFijaAlResponsableYLoDejaEnElExpediente() {
        Actor quien = agente(0);
        long idPropiedad = registrar(quien, "ALQUILER");

        assertEquals(quien.idRolOperativo(), responsableDe(idPropiedad),
                "el actor del alta es un hecho conocido, no una inferencia");

        // Con el BROKER que lo supervisa: el expediente es gobierno (C2).
        List<TraspasoDeResponsable> expediente = propiedades.traspasosDe(idPropiedad, broker());
        assertEquals(1, expediente.size(),
                "hasta V88 la columna aparecia poblada y el expediente no decia de donde "
                        + "salio: un valor de autoridad sin acto que lo explique");
        TraspasoDeResponsable alta = expediente.get(0);
        assertEquals("ALTA", alta.origen(),
                "y tiene que decir que nace del ALTA, no de un traspaso");
        assertNull(alta.idResponsableAnterior(),
                "no hay a quien desplazar: la propiedad acaba de existir. Y ese hueco es "
                        + "informacion, no un campo por rellenar");
        assertEquals(quien.idRolOperativo(), alta.idResponsableNuevo());
        assertEquals(Actor.AGENTE, alta.rolActor(),
                "la firma un AGENTE, que es justo lo que el CHECK de V87 no admitia");
        assertEquals(quien.idPersona(), alta.idPersonaActor());
        assertNotNull(alta.motivo(), "el motivo lo redacta el Core, no cada cliente");
    }

    /**
     * <b>El limite critico, en la base y no en un comentario.</b>
     *
     * <p>«Detectar o reutilizar una propiedad existente jamas debe ejecutar el
     * alta del responsable.» Aqui se comprueba que, aunque alguien lo
     * intentara por SQL, <b>la base lo rechaza</b>: el indice parcial
     * {@code uq_asignacion_alta_por_propiedad} admite <b>una sola</b> fila de
     * origen {@code ALTA} por propiedad.
     */
    @Test
    @DisplayName("una SEGUNDA alta sobre la misma propiedad no entra ni por SQL")
    void soloPuedeHaberUnAltaPorPropiedad() {
        Actor quien = agente(0);
        Actor otra = agente(1);
        long idPropiedad = registrar(quien, "ALQUILER");

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        insert into asignacion_responsable_propiedad
                            (organizacion_id, id_propiedad, id_rol_responsable_anterior,
                             id_rol_responsable_nuevo, id_persona_actor, tipo_rol_actor,
                             origen, motivo)
                        values (?, ?, null, ?, ?, 'AGENTE', 'ALTA', 'Segunda alta')
                        """, quien.idOrganizacion(), idPropiedad, otra.idRolOperativo(),
                        otra.idPersona()),
                "un comentario pide que no ocurra; este indice lo IMPIDE, venga del canal "
                        + "que venga y lo escriba quien lo escriba");
    }

    /**
     * <b>Retomar no es crear.</b> Otro agente prospecta y capta una propiedad
     * que ya existe: nace su ENCARGO, y el responsable de la PROPIEDAD no se
     * mueve.
     */
    @Test
    @DisplayName("propiedad existente retomada por OTRO agente: el responsable no cambia")
    void retomarUnaPropiedadExistenteNoCambiaAlResponsable() {
        Actor duena = agente(0);
        Actor otra = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");
        int altasAntes = altasDe(idPropiedad);

        // La otra agente la prospecta y la capta en VENTA: encargo nuevo suyo
        // sobre una propiedad que no es suya.
        long idProspeccion = prospecciones.registrar(
                new ProspeccionService.DatosProspeccion(idPropiedad, "La retoma otra agente", null),
                otra).id();
        prospecciones.contactar(idProspeccion, otra);
        prospecciones.captar(idProspeccion, capturaDeVenta(), otra);

        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad),
                "captar una propiedad que ya existia no convierte a nadie en responsable de "
                        + "ella: solo abre un encargo");
        assertEquals(altasAntes, altasDe(idPropiedad), "y no aparece una segunda alta");
        assertEquals(otra.idRolOperativo(), agentesDeLosEncargos(idPropiedad).get("V"),
                "el encargo SI es suyo -- si no, esta prueba estaria midiendo que no paso nada");
    }

    @Test
    @DisplayName("un ENCARGO nuevo sobre propiedad existente no cambia al responsable")
    void unEncargoNuevoNoCambiaAlResponsable() {
        Actor duena = agente(0);
        Actor otra = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");
        int altasAntes = altasDe(idPropiedad);

        abrirVentaCon(idPropiedad, otra);

        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad),
                "abrir una VENTA no cambia quien responde por el inmueble: son autoridades "
                        + "distintas y ese es todo el punto de P0");
        assertEquals(altasAntes, altasDe(idPropiedad));
    }

    @Test
    @DisplayName("propiedad FALTANTE + encargo nuevo: sigue FALTANTE")
    void unEncargoNuevoNoSacaDeFaltanteAUnaPropiedadHistorica() {
        Actor duena = agente(0);
        Actor otra = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");
        dejarSinResponsable(idPropiedad);

        abrirVentaCon(idPropiedad, otra);

        assertNull(responsableDe(idPropiedad),
                "una propiedad historica sin responsable NO se adopta abriendole un encargo: "
                        + "sigue FALTANTE hasta que un BROKER asigne");
        assertFalse(propiedades.consultar(idPropiedad, otra).responsabilidad().puedeEditar(),
                "y sigue sin poder editarla nadie");
    }

    /**
     * <b>Solo el traspaso de BROKER saca a una propiedad existente de donde
     * este.</b> Recorre las puertas que podrian pretenderlo —abrirle un encargo
     * y operar ese encargo— y despues comprueba que la unica que mueve la
     * columna es la del broker.
     */
    @Test
    @DisplayName("de una propiedad ya existente, solo el traspaso de BROKER cambia al responsable")
    void soloElTraspasoDeBrokerCambiaAlResponsableDeUnaPropiedadExistente() {
        Actor duena = agente(0);
        Actor otra = agente(1);
        long idPropiedad = registrar(duena, "ALQUILER");

        // Puerta 1: abrirle un encargo.
        abrirVentaCon(idPropiedad, otra);
        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad));

        // Puerta 2: operar ese encargo propio -- legitimo, y sin efecto aqui.
        long idVenta=unEncargoDe(idPropiedad,"V");
        ciclo.editar(idVenta,edicionVenta(versionDe(idVenta),new BigDecimal("410000"),"USD"),
                otra,sesion(otra),UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());
        assertEquals(duena.idRolOperativo(), responsableDe(idPropiedad),
                "operar un encargo propio no concede la ficha: son tres autoridades");

        // Puerta 3: la unica que si.
        propiedades.asignarResponsable(idPropiedad, otra.idRolOperativo(),
                "Traspaso autorizado por el broker del equipo",
                observado(idPropiedad), broker());
        assertEquals(otra.idRolOperativo(), responsableDe(idPropiedad));

        assertEquals(List.of("TRASPASO", "ALTA"),
                propiedades.traspasosDe(idPropiedad, broker()).stream()
                        .map(TraspasoDeResponsable::origen).toList(),
                "el expediente acumula y distingue los dos hechos, del mas reciente al mas "
                        + "antiguo");
    }

    /**
     * <b>Web y KAIROS producen la misma semantica del alta.</b>
     *
     * <p>No solo el mismo permiso: el mismo <b>efecto</b>. Un alta por el canal
     * conversacional deja exactamente el mismo rastro que una por pantalla,
     * porque es el mismo caso de uso.
     */
    @Test
    @DisplayName("el alta por WHATSAPP deja el mismo rastro que por SPA")
    void lasDosSuperficiesProducenLaMismaSemanticaDelAlta() {
        Actor quien = agente(0);
        long porPantalla = registrarPorCanal(quien, "SPA");
        long porConversacion = registrarPorCanal(quien, "WHATSAPP");

        for (long id : List.of(porPantalla, porConversacion)) {
            assertEquals(quien.idRolOperativo(), responsableDe(id));
            List<TraspasoDeResponsable> expediente = propiedades.traspasosDe(id, broker());
            assertEquals(1, expediente.size());
            assertEquals("ALTA", expediente.get(0).origen());
            assertEquals(Actor.AGENTE, expediente.get(0).rolActor());
            assertNull(expediente.get(0).idResponsableAnterior());
        }
    }

    // ==================================================================
    // Fixtures
    // ==================================================================

    private long registrar(Actor quien, String operacion) {
        return propiedades.registrar(new ComandoRegistro(null, null, null, "DEPARTAMENTO", "VIVIENDA",
                "Caso P0",
                new Ubicacion("Av. Autoridad " + UUID.randomUUID().toString().substring(0, 8),
                        "Miraflores", null, null, null, "101", null, null, null),
                List.of(new Titular(unPropietario(quien), null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada(operacion, new BigDecimal("3000"), "PEN",
                        null, null, null, null, Boolean.FALSE, null, null)),
                null), quien).idPropiedad();
    }

    private long registrarConVentaYAlquiler(Actor quien) {
        return propiedades.registrar(new ComandoRegistro(null, null, null, "DEPARTAMENTO", "VIVIENDA",
                "Caso P0 con dos encargos",
                new Ubicacion("Av. Dos Encargos " + UUID.randomUUID().toString().substring(0, 8),
                        "Miraflores", null, null, null, "101", null, null, null),
                List.of(new Titular(unPropietario(quien), null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("350000"), "USD",
                                null, null, null, null, Boolean.FALSE, null, null),
                        new OperacionSolicitada("ALQUILER", new BigDecimal("2500"), "PEN",
                                null, null, null, null, Boolean.FALSE, null, null)),
                null), quien).idPropiedad();
    }

    /**
     * El alta declarando el canal, que es lo unico que separa BROX Web de
     * KAIROS: el caso de uso es el mismo y por eso el efecto tiene que serlo.
     */
    private long registrarPorCanal(Actor quien, String canalDeclarado) {
        return propiedades.registrar(new ComandoRegistro(UUID.randomUUID().toString(),
                canal(canalDeclarado), null, "DEPARTAMENTO", "VIVIENDA",
                "Caso P0 por " + canalDeclarado,
                new Ubicacion("Av. Canal " + UUID.randomUUID().toString().substring(0, 8),
                        "Miraflores", null, null, null, "101", null, null, null),
                List.of(new Titular(unPropietario(quien), null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("3000"), "PEN",
                        null, null, null, null, Boolean.FALSE, null, null)),
                null), quien).idPropiedad();
    }

    /** Un encargo de VENTA sobre una propiedad que YA existe. */
    private long abrirVentaCon(long idPropiedad, Actor deQuien) {
        // La prospección propia establece una relación física real, sin atribuir su edición.
        prospecciones.registrar(new com.controllocal.service.ProspeccionService.DatosProspeccion(
                idPropiedad,"Prospección explícita de TEST para abrir su propio Encargo",null),deQuien);
        return new TransactionTemplate(gestor).execute(tx -> ciclo.abrir(
                new EncargoService.DatosApertura(idPropiedad,deQuien.idRolOperativo(),
                    "ENC-T-"+UUID.randomUUID().toString().substring(0,12),
                    com.controllocal.domain.inmueble.OperacionInmobiliaria.VENTA,
                    LocalDate.now(),LocalDate.now().plusMonths(6),false,
                    "Encargo de venta explícito del fixture",EncargosDePrueba.venta(
                        new BigDecimal("400000"),"USD",new BigDecimal("0.03"),"PROPIETARIO_TEST","AGENCIA_TEST"),
                    com.controllocal.service.soporte.Procedencia.deLaPantalla(),List.of()),deQuien).id());
    }

    /**
     * Un cuerpo <b>valido</b> para editar un encargo de ALQUILER por
     * {@code PUT /captaciones/{id}}.
     *
     * <p>Se construye completo a proposito, porque este caso de uso valida
     * antes de escribir y cualquier hueco convertiria un rechazo de AUTORIDAD
     * en uno de validacion: la operacion no se edita
     * ({@code exigirMismaOperacion}, por eso viaja ALQUILER), la vigencia es
     * obligatoria y el fin posterior al inicio ({@code validarEncargo}), y la
     * comision de un alquiler se calcula sobre la renta mensual y en su misma
     * moneda ({@code CondicionesEconomicas.exigirBaseCoherente}).
     */
    private EncargoService.Edicion edicionDeEncargoDeAlquiler(
            long versionEsperada, BigDecimal importe, Boolean exclusividad) {
        return new EncargoService.Edicion(versionEsperada,LocalDate.now(),LocalDate.now().plusMonths(6),
                exclusividad,"Edición explícita del fixture",
                new EncargoService.Condiciones(importe,"PEN","MENSUAL","P","R",new BigDecimal("0.05"),
                    "PEN","I",null,"FIRMA_CONTRATO","PROPIETARIO_TEST","AGENCIA_TEST",
                    "AL_FIRMAR","PACTO_FICTICIO_TEST",List.of(),null,null),"Corrección acordada del importe",null,null);
    }

    /** Las condiciones con las que se capta una prospeccion, en VENTA. */
    private ProspeccionService.DatosCaptura capturaDeVenta() {
        return new ProspeccionService.DatosCaptura("VENTA", new BigDecimal("400000"), "USD",
                new BigDecimal("3"), "P", "V", "I", Boolean.FALSE, null, null);
    }

    /** Cuantas filas de origen ALTA tiene esta propiedad. Debe ser 0 o 1. */
    private int altasDe(long idPropiedad) {
        Integer n = jdbc.queryForObject("""
                select count(*) from asignacion_responsable_propiedad
                 where id_propiedad = ? and origen = 'ALTA'
                """, Integer.class, idPropiedad);
        return n == null ? 0 : n;
    }

    private ComandoEdicion edicionDeFicha(long idPropiedad,String descripcion) {
        return edicionDeFicha(idPropiedad,descripcion, null);
    }

    /**
     * Una edicion que toca SOLO la ficha fisica: ni operaciones ni condiciones.
     * Es la que tiene que pedir la autoridad de la PROPIEDAD.
     */
    private ComandoEdicion edicionDeFicha(long idPropiedad,String descripcion, String canal) {
        return new ComandoEdicion(UUID.randomUUID().toString(), canal(canal), descripcion,
                null, null, null, null, null, null).conVersion(versionFisicaDe(idPropiedad));
    }

    /**
     * Una edicion que toca SOLO un encargo. No lleva descripcion, ni ubicacion,
     * ni atributos, y por eso no pide la autoridad de la propiedad: es lo que
     * permite que su agente lo siga operando aunque la propiedad este FALTANTE.
     */
    

    /**
     * La moneda se declara porque no es decorativa: {@code ck_condicion_tipo_base}
     * exige que la comision porcentual lleve la MISMA moneda que la referencia,
     * asi que cambiar el importe de un encargo en USD mandando PEN rompe la
     * invariante economica -- y con razon.
     */
    private EncargoService.Edicion edicionVenta(long version,BigDecimal importe,String moneda) {
        return new EncargoService.Edicion(version,LocalDate.now(),LocalDate.now().plusMonths(6),false,
                "Edición de venta de TEST",EncargosDePrueba.venta(importe,moneda,new BigDecimal("0.03"),
                        "PROPIETARIO_TEST","AGENCIA_TEST"),"Nuevo importe acordado",null,null);
    }

    private EncargoService.Edicion edicionDeCondiciones(long version) {
        return new EncargoService.Edicion(version,null,null,null,null,null,
                "Condiciones comerciales explícitas de TEST",List.of(new ValorAtributo("garantia_meses", "2")),List.of());
    }

    /** El canal declarado por el cliente, que es lo unico que separa Web de KAIROS. */
    private static com.controllocal.service.soporte.Procedencia canal(String canal) {
        return canal == null ? null
                : com.controllocal.service.soporte.Procedencia.deCabecera(canal);
    }

    private com.controllocal.integracion.soporte.PreparacionPublicacionDePrueba.DatosAnuncio anuncio() {
        return new com.controllocal.integracion.soporte.PreparacionPublicacionDePrueba.DatosAnuncio("WEB_PROPIA", null,
                new BigDecimal("2500"), "PEN", "Anuncio P0 " + UUID.randomUUID(), null, null);
    }

    // ------------------------------------------------------------------
    // Actores: dos agentes DISTINTOS del MISMO tenant, que es el caso que
    // el defecto necesitaba para existir.
    // ------------------------------------------------------------------

    /**
     * <b>Dos agentes del mismo tenant, supervisados por el mismo broker.</b>
     *
     * <p>Las tres condiciones importan y ninguna es decorativa. <b>Del mismo
     * tenant</b>, porque el defecto que este P0 cierra vivia justamente dentro
     * del tenant —entre corredoras ya cortaba V6—. <b>Dos</b>, porque «otro
     * agente no puede» seria una frase sin sujeto con uno solo. Y
     * <b>supervisados por el mismo broker</b>, porque el traspaso y la
     * reasignacion del encargo los ejecuta ese broker: si no alcanzara a los
     * dos, la prueba mediria su falta de alcance en vez de la regla.
     */
    private Actor agente(int indice) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a
                  join persona_rol r on r.id_persona_rol = a.id_persona_rol
                  join supervision_agente s on s.id_rol_agente = a.id_persona_rol
                                           and s.fecha_fin is null
                 where s.id_rol_broker = ?
                 order by a.id_persona_rol
                """, idBrokerConEquipo());
        assertTrue(filas.size() >= 2,
                "este gate necesita DOS agentes del mismo equipo: encontro " + filas.size());
        Map<String, Object> fila = filas.get(indice);
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }

    private Long idBrokerConEquipo() {
        Long id = jdbc.queryForObject("""
                select s.id_rol_broker from supervision_agente s
                 where s.fecha_fin is null
                 group by s.id_rol_broker, s.organizacion_id
                having count(*) >= 2
                 order by count(*) desc, s.id_rol_broker limit 1
                """, Long.class);
        assertNotNull(id, "sin un broker que supervise a dos agentes no hay escenario que probar");
        return id;
    }

    private Actor broker() {
        Map<String, Object> fila = jdbc.queryForList("""
                select b.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_broker b join persona_rol r on r.id_persona_rol = b.id_persona_rol
                 where b.id_persona_rol = ?
                """, idBrokerConEquipo()).get(0);
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.BROKER);
    }

    /** El gobierno del tenant, con la misma identidad y otra banda. */
    private Actor tenantAdmin() {return sesiones().gobiernoDe(broker().idOrganizacion());}

    // ------------------------------------------------------------------
    // Lecturas directas
    // ------------------------------------------------------------------

    private Long unPropietario(Actor actor) {
        return jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
    }

    private int filasDeTraspaso() {
        Integer n = jdbc.queryForObject(
                "select count(*) from asignacion_responsable_propiedad", Integer.class);
        return n == null ? 0 : n;
    }

    /** Los atributos de la ficha como `clave=valor`, para compararlos enteros. */
    private static List<String> atributosDe(FichaPropiedadUniversal ficha) {
        return ficha.atributos().stream()
                .map(a -> a.clave() + "=" + a.valor())
                .sorted()
                .toList();
    }

    private Long responsableDe(long idPropiedad) {
        return jdbc.queryForObject(
                "select id_rol_responsable from propiedad where id_propiedad = ?",
                Long.class, idPropiedad);
    }

    /**
     * <b>Lo que la prueba «vio» justo antes de mandar el comando</b> (D-P0-9).
     *
     * <p>El traspaso declara sobre que responsable actua, y aqui se lee de la
     * base en el mismo instante en que se decide, que es lo que hace una
     * pantalla: cargar la ficha y decidir sobre lo que muestra. Fijarlo a mano
     * haria que estas pruebas midieran el 409 en vez de la regla que estan
     * midiendo; y pasar «lo que hay ahora» automaticamente dentro del servicio
     * seria justo la reinterpretacion que D-P0-9 prohibe -- por eso el
     * observado entra por el comando y no lo deduce el Core.
     */
    private PropiedadUniversalService.ResponsableObservado observado(long idPropiedad) {
        return PropiedadUniversalService.ResponsableObservado.de(responsableDe(idPropiedad));
    }

    private Long incorporoDe(long idPropiedad) {
        return jdbc.queryForObject(
                "select id_rol_incorporo from propiedad where id_propiedad = ?",
                Long.class, idPropiedad);
    }

    private void dejarSinResponsable(long idPropiedad) {
        jdbc.update("update propiedad set id_rol_responsable = null where id_propiedad = ?",
                idPropiedad);
    }

    private String descripcionDe(long idPropiedad) {
        return jdbc.queryForObject("select descripcion from propiedad where id_propiedad = ?",
                String.class, idPropiedad);
    }

    private String estadoDelEncargo(long idEncargo) {
        return jdbc.queryForObject("select estado from captacion where id_captacion = ?",
                String.class, idEncargo);
    }

    private long unEncargoDe(long idPropiedad, String operacion) {
        return jdbc.queryForObject("""
                select id_captacion from captacion
                 where id_propiedad = ? and motivo_operacion = ? and estado in ('P','A')
                 order by id_captacion desc limit 1
                """, Long.class, idPropiedad, operacion);
    }

    private Map<String, Long> agentesDeLosEncargos(long idPropiedad) {
        Map<String, Long> porOperacion = new java.util.LinkedHashMap<>();
        jdbc.queryForList("""
                select motivo_operacion, id_rol_agente from captacion
                 where id_propiedad = ? and estado in ('P','A') order by motivo_operacion
                """, idPropiedad).forEach(fila -> porOperacion.put(
                        (String) fila.get("motivo_operacion"),
                        ((Number) fila.get("id_rol_agente")).longValue()));
        return porOperacion;
    }

    private BigDecimal importeVivoDe(long idPropiedad, String operacion) {
        // La FK va de la captacion a su condicion y no al reves
        // (`captacion.id_condicion_economica`, con su unico parcial): la
        // condicion no sabe de que encargo es.
        return jdbc.queryForObject("""
                select c.importe_referencia
                  from captacion cap
                  join condicion_economica_captacion c
                    on c.id_condicion_economica = cap.id_condicion_economica
                 where cap.id_propiedad = ? and cap.motivo_operacion = ? and cap.estado in ('P','A')
                 order by cap.id_captacion desc limit 1
                """, BigDecimal.class, idPropiedad, operacion);
    }

    /** La exclusividad pactada, leida de la base y no de la ficha que devuelve el caso de uso. */
    private Boolean exclusividadDe(long idEncargo) {
        return jdbc.queryForObject("select exclusividad from captacion where id_captacion = ?",
                Boolean.class, idEncargo);
    }

    private int hitosDe(long idPropiedad, String hito) {
        Integer n = jdbc.queryForObject(
                "select count(*) from precio_propiedad where id_propiedad = ? and hito = ?",
                Integer.class, idPropiedad, hito);
        return n == null ? 0 : n;
    }

    private int hitosDeEncargo(long idEncargo, String hito) {
        Integer n = jdbc.queryForObject(
                "select count(*) from precio_propiedad where id_captacion = ? and hito = ?",
                Integer.class, idEncargo, hito);
        return n == null ? 0 : n;
    }

    private long columnas(String tabla, String columna) {
        Long n = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_name = ? and column_name = ?
                """, Long.class, tabla, columna);
        return n == null ? 0 : n;
    }

    private boolean esNullable(String tabla, String columna) {
        return "YES".equals(jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                 where table_name = ? and column_name = ?
                """, String.class, tabla, columna));
    }
    /** Versión observada por el montaje ANTES del comando o de abrir la carrera. */
    private long versionDe(long idEncargo) {
        return jdbc.queryForObject("select version from captacion where id_captacion=?",Long.class,idEncargo);
    }
    /** El atacante puede conocer la versión; no constituye autorización sobre el recurso. */
    private long versionFisicaDe(long idPropiedad) {
        return jdbc.queryForObject("select version_fisica from propiedad where id_propiedad=?",Long.class,idPropiedad);
    }

    private EncargosDePrueba.Caso casoComercial(long id,String operacion,Actor responsable) {
        var op=com.controllocal.domain.inmueble.OperacionInmobiliaria.desde(operacion);
        var condiciones=op==com.controllocal.domain.inmueble.OperacionInmobiliaria.VENTA
                ?EncargosDePrueba.venta(new BigDecimal("400000"),"USD",new BigDecimal("0.03"),"PROPIETARIO_TEST","AGENCIA_TEST")
                :EncargosDePrueba.alquiler(new BigDecimal("3000"),"PEN",BigDecimal.ONE,"PROPIETARIO_TEST","AGENCIA_TEST");
        Actor supervisor=broker();
        return new EncargosDePrueba.Caso(id,op,LocalDate.now(),LocalDate.now().plusMonths(6),false,condiciones,
                new EncargosDePrueba.Actuante(responsable,sesion(responsable)),new EncargosDePrueba.Actuante(supervisor,sesion(supervisor)));
    }
    private void prepararFisica(long id,Actor responsable) {
        var conocido=com.controllocal.service.captura.SuficienciaInmueble.EstadoDato.CONOCIDO;
        var aplica=com.controllocal.service.captura.SuficienciaInmueble.Aplicabilidad.APLICA;
        var contexto=new com.controllocal.service.captura.SuficienciaInmueble.Contexto("D","V","Departamento 101 ficticio "+id,null,
                Map.of("unidadDeEdificio",aplica,"mantenimiento",aplica),java.util.Set.of());
        propiedades.editar(id,new ComandoEdicion(UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla(),
                null,null,null,List.of(new ValorAtributo("metraje_total","90",null,null,"DECLARADO",null,null,
                                "DOCUMENTO_FICTICIO_90M2",new PropiedadUniversalService.MetadatosDeCaptura(conocido,"AREA_TOTAL",null,null,false)),
                        new ValorAtributo("dormitorios","3"),new ValorAtributo("banos","2"),new ValorAtributo("piso","3"),
                        new ValorAtributo("ascensores","1"),new ValorAtributo("cuota_mantenimiento","100","PEN",null,null,null,null,"PACTO_FICTICIO_MANTENIMIENTO")),
                null,null,null,contexto).conVersion(versionFisicaDe(id)),responsable);
    }

}
