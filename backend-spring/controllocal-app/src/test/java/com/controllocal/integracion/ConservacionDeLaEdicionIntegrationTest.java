package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.AtributoFicha;
import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.CondicionesDeEncargo;
import com.controllocal.service.PropiedadUniversalService.EncargoFicha;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService.HitoFicha;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.TitularFicha;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>El gate de conservacion de la edicion</b> (Corte 0A).
 *
 * <h2>Lo que vigila</h2>
 * <pre>
 *   leer -&gt; abrir el editor -&gt; NO modificar ese dato -&gt; guardar -&gt; releer  ==  identico
 * </pre>
 *
 * <p>El producto del Corte 0A <b>no es un editor nuevo</b>: es la prueba de que
 * BROX puede editar cualquier propiedad sin destruir, inventar ni reinterpretar
 * informacion que el usuario no modifico. El editor universal es el medio.
 *
 * <h2>Por que la ida y vuelta se hace contra la FICHA</h2>
 * Una pantalla fiel no se inventa el contenido del formulario: lo carga de lo
 * que el Core publica y lo devuelve. Por eso {@link #comandoEspejo} construye el
 * comando de edicion <b>desde la ficha leida</b>. Si la ficha no se puede
 * devolver sin perdida, ninguna pantalla puede ser fiel — y entonces la fuga no
 * esta en Angular, esta en el contrato.
 *
 * <p>Una prueba de persistencia aislada no ve nada de esto: cada mitad guarda
 * bien lo suyo. La perdida aparece <b>en medio</b>, y solo un recorrido completo
 * la enseña. Es la misma forma que cerro D-E4-3.
 *
 * <h2>Cobertura, y por que no basta un departamento feliz</h2>
 * Los <b>siete</b> codigos {@code L, O, D, C, T, A, X}, propiedad <b>y</b>
 * encargos, y los <b>seis</b> tipos de valor que el catalogo sabe declarar hoy
 * para la PROPIEDAD — TEXTO, ENTERO, DECIMAL, BOOLEANO, LISTA y LISTA_MULTIPLE.
 * Mas los dos escenarios que el recorrido por tipo no ve: una propiedad con
 * venta y alquiler a la vez, y una propiedad con un encargo historico ya
 * cerrado.
 *
 * <p><b>Este gate no enriquece el catalogo.</b> Si al ejercitarlo se ve que a
 * Departamento le falta una clave o que su LISTA es pobre, eso es material de
 * los cortes de profundidad, no de aqui: 0A tiene que poder conservar
 * <b>incluso un modelo incompleto</b>. Si para no corromper hubiera que
 * enriquecer primero, la contencion no seria contencion.
 *
 * <p><b>Pero al reves si manda</b>: cuando el catalogo crece, los casos de este
 * gate crecen con el. Su contrato es "la carga mas ancha que el catalogo le
 * permite HOY", y un caso congelado en el catalogo de anteayer deja de medir lo
 * que dice medir sin que nada se ponga rojo. V80 anadio 28 claves a Departamento
 * y 16 a Casa, y entraron aqui con el.
 *
 * <h2>Una sola puerta, y sus invariantes ancladas en ella</h2>
 * Hubo aqui dos casos que median {@code LocalComercialService} -- la puerta por
 * la que BROX Web editaba-- y que estaban rojos a proposito: uno porque aquella
 * puerta reescribia el {@code uso} en cada guardado, otro porque solo sabia
 * representar dos de los siete tipos. V71 la retiro y los dos casos se
 * borraron; lo que <b>no</b> se borro son sus invariantes, que no eran de la
 * puerta sino del corte:
 *
 * <ul>
 *   <li>{@link #editarNoReescribeElUso}</li>
 *   <li>{@link #guardarUnBloqueNoTocaNingunOtro}, que ademas fija la regla del
 *       editor por bloques: cada bloque posee un campo del comando y la
 *       ausencia de los demas significa "no lo toques"</li>
 * </ul>
 *
 * <p>Se re-anclaron sobre la puerta universal <b>antes</b> de retirar la vieja,
 * que es el orden que importa: la proteccion de reemplazo existe antes de
 * quitar la que sustituye, no despues.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConservacionDeLaEdicionIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;

    // ==================================================================
    // Los siete tipos, con los valores que su catalogo admite hoy
    // ==================================================================

    /**
     * Un tipo con la carga de atributos mas ancha que el catalogo le permite
     * <b>hoy</b>, sin anadir ninguna clave.
     *
     * @param familias los tipos de dato que este caso ejercita de verdad, para
     *                 que el conjunto se pueda auditar de un vistazo
     */
    private record CasoDeTipo(String tipo, String uso, List<ValorAtributo> atributos,
                              Set<String> familias) {

        @Override
        public String toString() {
            return tipo;
        }
    }

    private static ValorAtributo v(String clave, String valor) {
        return new ValorAtributo(clave, valor);
    }

    /**
     * <b>Un caso por tipo, con TODAS las claves que ese tipo admite hoy.</b>
     *
     * <p>Estas listas ya no se escriben a mano mirando lo que habia: se derivan
     * del catalogo y se comprueban contra el en
     * {@link #cadaCasoLlevaTodoLoQueSuTipoAdmite()}. Antes de V81 eran listas
     * congeladas, y el resultado medido fue que <b>L cargaba 13 de 24 claves,
     * O 10 de 28 y A 12 de 22</b>: siete claves que V80 sembro para el local,
     * trece para la oficina y cuatro para el almacen <b>no las tocaba nadie</b>
     * en la ida y vuelta, y nada se ponia rojo por ello.
     *
     * <p>Ese es el modo de fallo que importa: el javadoc de {@link CasoDeTipo}
     * prometia "la carga mas ancha que el catalogo le permite HOY" y lo que
     * habia era la de anteayer. Una promesa que nadie comprueba deja de ser
     * cierta en el primer corte que siembra algo.
     */
    private static Stream<CasoDeTipo> tipos() {
        return Stream.of(
                new CasoDeTipo("LOCAL", "COMERCIAL", List.of(
                        v("metraje_total", "120.50"),
                        v("antiguedad_anios", "8"),
                        v("estacionamientos", "2"),
                        v("metraje_construido", "110.25"),
                        v("ambientes", "4"),
                        v("piso", "3"),
                        v("cuota_mantenimiento", "350.00"),
                        v("frente", "7.50"),
                        v("carga_electrica_kw", "15.50"),
                        v("altura_libre", "3.20"),
                        v("apto_licencia_funcionamiento", "true"),
                        v("rubro_permitido", "Restaurante"),
                        v("zonificacion", "CZ"),
                        v("partida_registral", "P-11223344"),
                        v("oficina_registral", "LIMA"),
                        v("independizado", "false"),
                        ValorAtributo.multiple("cargas_gravamenes",
                                List.of("NINGUNA", "HIPOTECA")),
                        v("estado_conservacion", "ESTRENO"),
                        v("etapa_entrega", "EN_PLANOS"),
                        v("ascensores", "1"),
                        ValorAtributo.multiple("vigilancia",
                                List.of("NO_TIENE", "PORTERO_DIURNO")),
                        v("restriccion_reglamento_interno", "Mudanzas de 9 a 17 h"),
                        v("accesibilidad_movilidad_reducida", "true"),
                        v("niveles_internos", "1"),
                        v("nivel_implementacion", "CASCO_OBRA_GRIS"),
                        v("horario_acceso_edificio", "H24_7"),
                        v("fibra_optica", "true"),
                        v("gas", "SIN_RED_CERCANA"),
                        v("suministro_electrico", "MONOFASICO_220"),
                        v("respaldo_electrico", "NO_TIENE"),
                        v("aire_acondicionado", "NINGUNO"),
                        v("medidor_servicios", "INDEPENDIENTE"),
                        v("sistema_contra_incendios", "NINGUNO"),
                        v("extraccion_humos", "SIN_DUCTO"),
                        v("tipo_acceso", "A_PIE_DE_CALLE"),
                        v("en_esquina", "false"),
                        v("metraje_arrendable", "118.00"),
                        v("aforo_itse", "40"),
                        v("certificado_itse", "VIGENTE"),
                        v("acceso_vehiculo_maximo", "CAMIONETA"),
                        // V84: el hecho sobre el que se pacta `entrega_desocupado`,
                        // que llega a los siete tipos.
                        v("estado_ocupacion", "OCUPADO_POR_INQUILINO"),
                        // CORTE 5 · 5B (`V85`): la unica clave del suelo que llega
                        // al local. Avenida o pasaje es el doble de precio para el
                        // mismo metraje, y eso vale igual para un local que para un
                        // terreno.
                        v("tipo_via_acceso", "CALLE_O_JIRON")),
                        Set.of("BOOLEANO", "DECIMAL", "ENTERO", "LISTA", "LISTA_MULTIPLE", "TEXTO")),

                new CasoDeTipo("OFICINA", "COMERCIAL", List.of(
                        v("metraje_total", "85.00"),
                        v("antiguedad_anios", "5"),
                        v("estacionamientos", "1"),
                        v("metraje_construido", "80.00"),
                        v("ambientes", "3"),
                        v("piso", "7"),
                        v("cuota_mantenimiento", "400.00"),
                        v("amoblado", "false"),
                        v("carga_electrica_kw", "12.00"),
                        v("apto_licencia_funcionamiento", "false"),
                        v("rubro_permitido", "Restaurante"),
                        v("partida_registral", "P-11223344"),
                        v("oficina_registral", "LIMA"),
                        v("independizado", "false"),
                        ValorAtributo.multiple("cargas_gravamenes",
                                List.of("NINGUNA", "HIPOTECA")),
                        v("estado_conservacion", "ESTRENO"),
                        v("etapa_entrega", "EN_PLANOS"),
                        v("ascensores", "2"),
                        ValorAtributo.multiple("vigilancia",
                                List.of("NO_TIENE", "PORTERO_DIURNO")),
                        ValorAtributo.multiple("areas_comunes",
                                List.of("GIMNASIO", "PISCINA")),
                        v("unidades_por_piso", "4"),
                        v("restriccion_reglamento_interno", "Mudanzas de 9 a 17 h"),
                        v("accesibilidad_movilidad_reducida", "true"),
                        v("niveles_internos", "1"),
                        v("vista", "INTERIOR"),
                        v("depositos", "1"),
                        v("deposito_area", "6.00"),
                        v("tipo_estacionamiento", "SIMPLE"),
                        v("nivel_implementacion", "CASCO_OBRA_GRIS"),
                        v("recepcion_edificio", "false"),
                        v("horario_acceso_edificio", "H24_7"),
                        v("fibra_optica", "false"),
                        v("certificacion_sostenible", "NINGUNA"),
                        v("gas", "SIN_RED_CERCANA"),
                        v("suministro_electrico", "MONOFASICO_220"),
                        v("respaldo_electrico", "NO_TIENE"),
                        v("aire_acondicionado", "NINGUNO"),
                        v("medidor_servicios", "INDEPENDIENTE"),
                        v("sistema_contra_incendios", "NINGUNO"),
                        v("en_esquina", "false"),
                        v("clase_edificio", "A_PLUS"),
                        v("metraje_arrendable", "92.00"),
                        v("banos_comunes_piso", "true"),
                        v("posiciones_trabajo", "24"),
                        v("salas_reunion", "2"),
                        v("aforo_itse", "30"),
                        v("certificado_itse", "VIGENTE"),
                        v("estado_ocupacion", "DESOCUPADO")),
                        Set.of("BOOLEANO", "DECIMAL", "ENTERO", "LISTA", "LISTA_MULTIPLE", "TEXTO")),

                // V80 ensancho la vivienda de 10 claves a 38. Este caso las
                // lleva TODAS, que es lo que dice el contrato de este record:
                // "la carga de atributos mas ancha que el catalogo le permite
                // HOY". Un corte de profundidad que siembre bien y escriba mal
                // se ve aqui, en la ida y vuelta, y no en produccion.
                new CasoDeTipo("DEPARTAMENTO", "VIVIENDA", List.of(
                        v("metraje_total", "95.00"),
                        v("antiguedad_anios", "12"),
                        v("estacionamientos", "1"),
                        v("metraje_construido", "90.00"),
                        v("ambientes", "5"),
                        v("piso", "4"),
                        v("cuota_mantenimiento", "280.00"),
                        v("dormitorios", "3"),
                        v("banos", "2.5"),
                        v("amoblado", "false"),
                        v("partida_registral", "P-11223344"),
                        v("oficina_registral", "LIMA"),
                        v("independizado", "true"),
                        v("declaratoria_fabrica", "false"),
                        ValorAtributo.multiple("cargas_gravamenes",
                                List.of("NINGUNA", "HIPOTECA")),
                        v("estado_conservacion", "ESTRENO"),
                        v("etapa_entrega", "EN_PLANOS"),
                        v("ascensores", "2"),
                        ValorAtributo.multiple("vigilancia",
                                List.of("NO_TIENE", "PORTERO_DIURNO")),
                        ValorAtributo.multiple("areas_comunes",
                                List.of("GIMNASIO", "PISCINA")),
                        v("unidades_por_piso", "4"),
                        v("restriccion_reglamento_interno", "Mudanzas de 9 a 17 h"),
                        v("accesibilidad_movilidad_reducida", "true"),
                        v("tipologia", "MONOAMBIENTE"),
                        v("niveles_internos", "2"),
                        v("medios_banos", "1"),
                        v("cuarto_servicio", "1"),
                        v("bano_servicio", "false"),
                        v("tipo_cocina", "CERRADA"),
                        v("lavanderia", "INDEPENDIENTE"),
                        v("estudio", "true"),
                        v("vista", "INTERIOR"),
                        v("terraza", "true"),
                        v("area_terraza", "18.50"),
                        v("balcon", "true"),
                        v("jardin", "false"),
                        v("patio", "true"),
                        v("area_jardin_patio", "0.00"),
                        v("depositos", "1"),
                        v("deposito_area", "6.25"),
                        v("tipo_estacionamiento", "SIMPLE"),
                        v("torre_bloque", "Torre B"),
                        v("mascotas_reglamento", "true"),
                        v("gas", "SIN_RED_CERCANA"),
                        v("agua_caliente", "NO_TIENE"),
                        v("respaldo_electrico", "NO_TIENE"),
                        v("estado_ocupacion", "OCUPADO_POR_EL_PROPIETARIO")),
                        Set.of("BOOLEANO", "DECIMAL", "ENTERO", "LISTA", "LISTA_MULTIPLE", "TEXTO")),

                new CasoDeTipo("CASA", "VIVIENDA", List.of(
                        v("metraje_total", "210.00"),
                        v("antiguedad_anios", "20"),
                        v("estacionamientos", "2"),
                        v("metraje_construido", "180.00"),
                        v("ambientes", "7"),
                        v("cuota_mantenimiento", "150.00"),
                        v("dormitorios", "4"),
                        v("banos", "3.5"),
                        v("amoblado", "true"),
                        v("pisos_edificacion", "2"),
                        v("zonificacion", "CZ"),
                        v("area_terreno", "250.00"),
                        v("partida_registral", "P-11223344"),
                        v("oficina_registral", "LIMA"),
                        v("declaratoria_fabrica", "true"),
                        v("area_segun_partida", "13.50"),
                        ValorAtributo.multiple("cargas_gravamenes",
                                List.of("NINGUNA", "HIPOTECA")),
                        v("estado_conservacion", "ESTRENO"),
                        ValorAtributo.multiple("vigilancia",
                                List.of("NO_TIENE", "PORTERO_DIURNO")),
                        ValorAtributo.multiple("areas_comunes",
                                List.of("GIMNASIO", "PISCINA")),
                        v("en_condominio", "true"),
                        v("medios_banos", "1"),
                        v("cuarto_servicio", "2"),
                        v("bano_servicio", "false"),
                        v("estudio", "true"),
                        v("terraza", "false"),
                        v("area_terraza", "24.00"),
                        v("jardin", "false"),
                        v("patio", "true"),
                        v("area_jardin_patio", "70.50"),
                        v("piscina", "true"),
                        v("tipo_estacionamiento", "SIMPLE"),
                        v("mascotas_reglamento", "true"),
                        v("gas", "SIN_RED_CERCANA"),
                        v("agua_caliente", "NO_TIENE"),
                        v("estado_ocupacion", "OCUPADO_POR_TERCEROS_SIN_TITULO"),
                        // CORTE 5 · 5B (`V85`): las cinco claves del suelo que NO
                        // son solo del terreno. Una casa que se compra por su lote
                        // se cotiza por lo mismo que un terreno --partida, fondo,
                        // frentes, altura permitida y riesgo declarado-- y ahi el
                        // dato es el mismo hecho, no una version reducida.
                        //
                        // `area_terreno` SE QUEDA en este caso: para una CASA no
                        // nombra la misma verdad que `metraje_total`. D-7 retira
                        // SOLO la fila de `T`.
                        v("situacion_registral", "EN_SANEAMIENTO"),
                        v("fondo", "20.00"),
                        v("posicion_en_manzana", "UN_FRENTE"),
                        v("altura_normativa_pisos", "5"),
                        v("zona_de_riesgo", "false")),
                        Set.of("BOOLEANO", "DECIMAL", "ENTERO", "LISTA", "LISTA_MULTIPLE", "TEXTO")),

                new CasoDeTipo("TERRENO", "COMERCIAL", List.of(
                        v("metraje_total", "500.00"),
                        v("antiguedad_anios", "1"),
                        v("estacionamientos", "1"),
                        v("frente", "15.50"),
                        v("zonificacion", "CZ"),
                        // `area_terreno` SE VA DE ESTE CASO, Y SOLO DE ESTE (D-7,
                        // `V85`). Para un TERRENO nombraba la MISMA verdad que
                        // `metraje_total` --que es la superficie canonica, columna
                        // `propiedad.metraje`, ALT en los siete y NOT NULL--, y dos
                        // claves para una verdad no comparan nada. `V85` retira su
                        // aplicabilidad a `T`, asi que desde ahi el alta la rechaza
                        // y dejarla aqui pondria este caso rojo.
                        //
                        // SE VA DE `T` Y **SOLO** DE `T`: los casos de CASA (250.00)
                        // y ALMACEN (900.00) la conservan, porque ahi NO es la misma
                        // verdad -- una casa se tasa por el par (terreno, construida)
                        // y una nave tiene patio ademas de techo.
                        //
                        // Este fixture es ademas el que habia producido las filas
                        // de `area_terreno` sobre terrenos que `V85` encontro en
                        // `controllocal_repositorios`: 307, todas con 500.00
                        // contra un metraje de 500.00, o sea COINCIDIENDO POR
                        // CONSTRUCCION.
                        //
                        // Esa cifra es HISTORIA y se dice como tal: se midio
                        // ANTES de aplicar la migracion, y la retirada de D-7 se
                        // llevo las que coincidian y cerro la puerta de `T`.
                        // Cuantas sobreviven hoy NO se escribe aqui -- lo mide
                        // el gate en la columna `nota` de la comprobacion
                        // «5B ningun area_terreno de un TERRENO repite su
                        // metraje canonico».
                        //
                        // Por eso `V85` escribe su regla como invariante --"ninguna se pierde
                        // sin coincidir con `metraje_total` o sin quedar contada"-- y
                        // nunca como la cifra "0 discrepantes", que es lo que este
                        // fixture hacia cierto y dejaria de serlo en cuanto alguien
                        // registrara un terreno a mano.
                        // V84 REESCRIBE ESTA LINEA, NO LA BORRA. Hasta el Corte 5
                        // aqui iba `servicios_disponibles` con el texto libre
                        // «Agua, luz y desague»: la clave era LISTA sin una sola
                        // opcion, asi que aceptaba cualquier cadena, y este
                        // fixture era una de las dos que escribian ese legado que
                        // quedo en `controllocal_repositorios`.
                        //
                        // AL REESCRIBIRLA, ESTE FIXTURE DEJO DE PRODUCIR LEGADO, y
                        // durante unas horas NADIE lo producia: esas filas eran
                        // residuo, sobre una base nueva el universo era cero y la
                        // comprobacion «5A ningun inmueble con legado recibio un
                        // servicio sin que nadie lo afirmara» salia verde sin mirar
                        // nada. Lo
                        // midio la auditoria del 2026-08-25. El productor esta
                        // repuesto donde vive la comprobacion que lo necesita:
                        // `OcupacionYServiciosIntegrationTest.sembrarLegadoAmbiguo`,
                        // que escribe por SQL porque la puerta normal ya rechaza la
                        // clave. Aqui NO se repone: este caso mide la conservacion
                        // de la edicion, y sembrar una clave retirada le anadiria
                        // un proposito que no es el suyo.
                        //
                        // La clave queda retirada (`activo = false`) y la sustituyen
                        // DOS hechos separados: en la periferia se tiene luz y no
                        // desague, o al reves, y un solo campo agregado escondia
                        // justo esa combinacion. Las dos son PUB en T, asi que este
                        // terreno tambien deja de ser publicable sin ellas.
                        v("agua_desague", "CON_FACTIBILIDAD_APROBADA"),
                        v("energia_electrica", "CONECTADO"),
                        v("partida_registral", "P-11223344"),
                        v("oficina_registral", "LIMA"),
                        v("area_segun_partida", "11.50"),
                        ValorAtributo.multiple("cargas_gravamenes",
                                List.of("NINGUNA", "HIPOTECA")),
                        v("gas", "SIN_RED_CERCANA"),
                        v("acceso_vehiculo_maximo", "CAMIONETA"),
                        v("via_de_acceso", "Panamericana Sur km 32"),
                        v("estado_ocupacion", "DESOCUPADO"),
                        // CORTE 5 · 5B (`V85`): las 18 claves del suelo. Todas
                        // aplican a T, y ninguna de las caracteristicas que el
                        // TERRENO tenia antes hablaba del suelo COMO SUELO.
                        //
                        // ESTE COMENTARIO YA NO LLEVA CIFRAS DEL CATALOGO NI DEL
                        // PROTOTIPO, y es la enmienda de la septima ronda.
                        // Llevaba cuatro -- cuantas caracteristicas gana el
                        // TERRENO, cuantas claves declara el prototipo
                        // `motor-captura.js`, cuantas tiene el Core y cuantas
                        // imprime el prototipo para TERRENO -- y TRES de ellas
                        // las movio ESTE MISMO CORTE al sembrar las 18 claves y
                        // al ampliar el contrato-dato, asi que nacieron
                        // caducadas y siguieron caducadas seis rondas sin que
                        // nada avisara. Una cifra que el propio corte incrementa
                        // no puede vivir escrita a mano en un comentario: se
                        // miden con `node docs/ai/modelo/motor-captura.js`,
                        // contra `catalogo_atributo`, y en la columna `nota` de
                        // la comprobacion «M2 no se retiraron claves del
                        // catalogo del sistema» del gate SQL. La deriva
                        // prototipo/Core sigue anotada como N18 en
                        // `pendientes-brox.md`.
                        //
                        // `tipo_via_acceso` CONVIVE con `via_de_acceso`, dos lineas
                        // mas arriba, y por eso van las dos en este caso: una dice
                        // CUAL es la via, la otra de que CLASE es. Si una sustituyera
                        // a la otra, este fixture seria el sitio donde se notaria.
                        v("condicion_terreno", "URBANO_HABILITADO"),
                        v("situacion_registral", "INSCRITO_EN_SUNARP"),
                        v("zona_de_riesgo", "false"),
                        v("restriccion_arqueologica", "NO_APLICA"),
                        v("fondo", "25.00"),
                        v("posicion_en_manzana", "ESQUINA"),
                        v("topografia", "PLANO"),
                        v("tipo_via_acceso", "AVENIDA"),
                        v("estado_via", "ASFALTADA"),
                        // CERO A PROPOSITO, y es la unica clave de las 18 que se
                        // elige por su valor y no por comodidad: declarar 0 m2
                        // construidos es una MEDIDA --el lote esta vacio-- y no es
                        // lo mismo que dejarlo en blanco, que es "no consta". Si la
                        // ida y vuelta convirtiera el 0 en nulo, esa distincion se
                        // perderia sin que nada mas lo dijera.
                        v("edificacion_existente", "0.00"),
                        v("cercado", "true"),
                        v("certificado_parametros_vigente", "true"),
                        v("altura_normativa_pisos", "8"),
                        v("coeficiente_edificacion", "3.50"),
                        v("area_libre_minima", "30.00"),
                        v("retiro_municipal", "3.00"),
                        v("lote_minimo_normativo", "160.00"),
                        v("usos_compatibles", "Comercio vecinal, vivienda multifamiliar")),
                        // BOOLEANO entra aqui con `V85`: hasta 5B el TERRENO no
                        // tenia ni una sola clave booleana y ahora tiene tres
                        // (`cercado`, `certificado_parametros_vigente`,
                        // `zona_de_riesgo`).
                        Set.of("BOOLEANO", "DECIMAL", "ENTERO", "LISTA", "LISTA_MULTIPLE", "TEXTO")),

                new CasoDeTipo("ALMACEN", "INDUSTRIAL", List.of(
                        v("metraje_total", "800.00"),
                        v("antiguedad_anios", "15"),
                        v("estacionamientos", "4"),
                        v("metraje_construido", "750.00"),
                        v("ambientes", "2"),
                        v("cuota_mantenimiento", "11.50"),
                        v("frente", "20.00"),
                        v("carga_electrica_kw", "45.50"),
                        v("altura_libre", "8.50"),
                        v("apto_licencia_funcionamiento", "false"),
                        v("rubro_permitido", "Restaurante"),
                        v("zonificacion", "CZ"),
                        v("area_terreno", "900.00"),
                        v("partida_registral", "P-11223344"),
                        v("oficina_registral", "LIMA"),
                        v("independizado", "false"),
                        v("area_segun_partida", "10.50"),
                        ValorAtributo.multiple("cargas_gravamenes",
                                List.of("NINGUNA", "HIPOTECA")),
                        v("estado_conservacion", "ESTRENO"),
                        v("etapa_entrega", "EN_PLANOS"),
                        ValorAtributo.multiple("vigilancia",
                                List.of("NO_TIENE", "PORTERO_DIURNO")),
                        v("en_condominio", "false"),
                        v("nivel_implementacion", "CASCO_OBRA_GRIS"),
                        v("horario_acceso_edificio", "H24_7"),
                        v("gas", "SIN_RED_CERCANA"),
                        v("suministro_electrico", "MONOFASICO_220"),
                        v("respaldo_electrico", "NO_TIENE"),
                        v("medidor_servicios", "INDEPENDIENTE"),
                        v("sistema_contra_incendios", "NINGUNO"),
                        v("en_esquina", "false"),
                        v("metraje_arrendable", "780.00"),
                        v("aforo_itse", "80"),
                        v("certificado_itse", "VIGENTE"),
                        v("area_libre", "320.00"),
                        v("profundidad_patio_maniobras", "28.00"),
                        v("acceso_vehiculo_maximo", "CAMIONETA"),
                        v("muelles_carga", "4"),
                        v("tipo_muelle", "SIN_MUELLE"),
                        v("puertas_ingreso", "2"),
                        v("ancho_puerta_ingreso", "4.50"),
                        v("alto_puerta_ingreso", "5.20"),
                        v("capacidad_portante_piso", "6.00"),
                        v("tipo_piso", "CONCRETO_PULIDO"),
                        v("luz_entre_columnas", "12 x 24"),
                        v("posiciones_pallet", "1200"),
                        v("area_oficinas", "60.00"),
                        v("condicion_almacenamiento", "SECO"),
                        v("balanza_camionera", "false"),
                        v("estacionamientos_camiones", "6"),
                        v("via_de_acceso", "Panamericana Sur km 32"),
                        // V84: `agua_desague` llega tambien a la nave, y ahi es OPC.
                        v("agua_desague", "CONECTADO"),
                        v("estado_ocupacion", "DESOCUPADO"),
                        // CORTE 5 · 5B (`V85`): la via llega tambien a la nave, y
                        // ahi decide el negocio -- a un almacen al que se llega por
                        // trocha no entra un trailer. Van las DOS, `via_de_acceso`
                        // (arriba, TEXTO: cual) y estas (que clase y en que estado).
                        v("tipo_via_acceso", "CARRETERA"),
                        v("estado_via", "AFIRMADA")),
                        Set.of("BOOLEANO", "DECIMAL", "ENTERO", "LISTA", "LISTA_MULTIPLE", "TEXTO")),

                // OTRO admitia tres claves hasta V84 y ahora admite cuatro: el
                // Corte 5 le da `estado_ocupacion` porque `entrega_desocupado` ya
                // se pacta tambien sobre un OTRO, y el hecho no puede llegar menos
                // lejos que su condicion. Sigue siendo el tipo que el modelo casi
                // no sabe describir, y 0A tiene que conservarlo igual.
                new CasoDeTipo("OTRO", "MIXTO", List.of(
                        v("metraje_total", "60"), v("antiguedad_anios", "3"),
                        v("estacionamientos", "1"),
                        v("estado_ocupacion", "DESOCUPADO")),
                        Set.of("ENTERO", "DECIMAL", "LISTA")));
    }

    /**
     * El conjunto ejercita las SEIS familias de valor que el catalogo declara
     * hoy para el sujeto PROPIEDAD. Si alguien recorta un caso y con el se va la
     * unica LISTA o el unico BOOLEANO, el gate deja de medir lo que dice medir y
     * nadie se entera.
     *
     * <p>Eran cinco hasta V80. LISTA_MULTIPLE existe en la PROPIEDAD desde V79
     * ({@code cargas_gravamenes}) y el recorrido no la tocaba: la familia mas
     * dificil de conservar en una ida y vuelta --N filas y no una-- era
     * justamente la unica que este gate no probaba. V80 trae dos mas
     * ({@code vigilancia} y {@code areas_comunes}) y con ellas entra en el
     * recorrido.
     */
    /**
     * <b>La promesa del record, convertida en gate.</b>
     *
     * <p>{@link CasoDeTipo} dice llevar "la carga de atributos mas ancha que el
     * catalogo le permite HOY". Esto lo COMPRUEBA contra el catalogo real en vez
     * de confiar en que alguien se acuerde: si un corte siembra una clave para
     * un tipo y no la anade a su caso, la ida y vuelta de esa clave no se prueba
     * y <b>este test se pone rojo diciendo cual falta</b>.
     *
     * <p>Sin el, la unica senal de que faltaban treinta y una claves fue que
     * alguien las contara a mano dos cortes despues.
     */
    @Test
    @DisplayName("cada caso lleva TODAS las claves que su tipo admite en el catalogo")
    void cadaCasoLlevaTodoLoQueSuTipoAdmite() {
        Map<String, String> codigoDe = Map.of(
                "LOCAL", "L", "OFICINA", "O", "DEPARTAMENTO", "D", "CASA", "C",
                "TERRENO", "T", "ALMACEN", "A", "OTRO", "X");
        List<String> incompletos = new ArrayList<>();
        tipos().forEach(caso -> {
            Set<String> enElCaso = caso.atributos().stream()
                    .map(ValorAtributo::clave).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
            List<String> admitidas = jdbc.queryForList("""
                    select c.clave
                      from catalogo_atributo c
                      join catalogo_atributo_tipo t on t.id_catalogo_atributo = c.id_catalogo_atributo
                     where c.del_sistema and c.activo and t.tipo_propiedad = ?
                     order by c.orden
                    """, String.class, codigoDe.get(caso.tipo()));
            List<String> faltan = admitidas.stream().filter(k -> !enElCaso.contains(k)).toList();
            if (!faltan.isEmpty()) {
                incompletos.add(caso.tipo() + " no ejercita " + faltan.size() + " de "
                        + admitidas.size() + ": " + String.join(", ", faltan));
            }
        });
        assertEquals(List.of(), incompletos,
                "Hay claves del catalogo que ningun caso escribe, asi que su ida y vuelta no se prueba.");
    }

    @Test
    @DisplayName("los siete tipos cubren las seis familias de valor que el catalogo declara")
    void losSieteTiposCubrenLasSeisFamilias() {
        Set<String> cubiertas = new TreeSet<>();
        tipos().forEach(caso -> cubiertas.addAll(caso.familias()));
        assertEquals(
                Set.of("BOOLEANO", "DECIMAL", "ENTERO", "LISTA", "LISTA_MULTIPLE", "TEXTO"),
                cubiertas,
                "El recorrido tiene que tocar las seis familias que sabe declarar el catalogo.");
    }

    // ==================================================================
    // El recorrido, por los siete tipos
    // ==================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("tipos")
    @DisplayName("guardar sin cambiar nada devuelve exactamente la misma propiedad")
    void guardarSinCambiarNadaConserva(CasoDeTipo caso) {
        long id = registrar(caso, List.of(venta("150000")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());

        propiedades.editar(id, comandoEspejo(antes), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        exigirIdentico(retrato(antes), retrato(despues), Set.of(),
                "Guardar sin tocar nada cambio la propiedad (" + caso.tipo() + ").");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tipos")
    @DisplayName("cambiar solo la descripcion no mueve ningun otro dato")
    void cambiarUnaSolaCosaConservaElResto(CasoDeTipo caso) {
        long id = registrar(caso, List.of(venta("150000")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());

        ComandoEdicion espejo = comandoEspejo(antes);
        propiedades.editar(id, new ComandoEdicion(null, null, "Descripcion nueva",
                espejo.ubicacion(), espejo.titulares(), espejo.atributos(),
                espejo.operaciones(), null), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        assertEquals("Descripcion nueva", despues.descripcion(),
                "El unico cambio pedido no llego.");
        exigirIdentico(retrato(antes), retrato(despues), Set.of(),
                "Cambiar la descripcion movio algo mas (" + caso.tipo() + ").");
    }

    /**
     * <b>La ubicacion parcial.</b> Un editor que solo pinta direccion y distrito
     * —que es lo unico que el Core exige— manda el resto vacio. Eso no es "ya no
     * lo se": es "no lo estoy editando". La diferencia entre <b>ausencia</b>,
     * <b>null</b> y <b>vacio</b> tiene que estar decidida en el contrato, porque
     * de ella depende entera la invariante de 0A.
     */
    @Test
    @DisplayName("una ubicacion parcial no borra lo que no trae")
    void ubicacionParcialNoBorraLoQueNoTrae() {
        long id = registrar(casoDe("DEPARTAMENTO"), List.of(venta("180000")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());
        assertNotNull(antes.ubicacion().zonaUrbanizacion(), "El caso tiene que traer zona.");

        propiedades.editar(id, new ComandoEdicion(null, null, null,
                new Ubicacion(antes.ubicacion().direccion(), antes.ubicacion().distrito(),
                        null, null, null, null, null, null, null),
                null, null, null, null), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        exigirIdentico(retrato(antes), retrato(despues), Set.of(),
                "Una ubicacion parcial borro datos que nadie pidio borrar.");
    }

    // ==================================================================
    // Venta y alquiler a la vez: dos encargos que no se contaminan
    // ==================================================================

    @Test
    @DisplayName("venta y alquiler: cambiar algo fisico deja los dos encargos intactos")
    void cambioFisicoNoTocaNingunEncargo() {
        long id = registrar(casoDe("CASA"), List.of(venta("320000"), alquiler("2800")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());
        assertEquals(2, antes.encargos().size(), "El caso necesita los dos encargos.");

        // El resto de atributos se reenvia por el espejo y no rehecho a mano:
        // desde V80 la casa lleva dos LISTA_MULTIPLE, y un `new ValorAtributo(
        // clave, valor)` sobre una de ellas manda como escalar el TEXTO DE
        // PRESENTACION pegado por comas --`valores` es lo que queda a null-- y
        // el Core lo rechaza, con razon. Aqui lo que se prueba es que cambiar UN
        // dato fisico no toca los encargos, no como se serializa un multivalor.
        ComandoEdicion espejo = comandoEspejo(antes);
        List<ValorAtributo> atributos = espejo.atributos().stream()
                .map(a -> "banos".equals(a.clave()) ? new ValorAtributo("banos", "4") : a)
                .toList();
        propiedades.editar(id, new ComandoEdicion(null, null, null, espejo.ubicacion(),
                espejo.titulares(), atributos, espejo.operaciones(), null), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        exigirIdentico(retrato(antes), retrato(despues), Set.of("atributo.banos"),
                "Cambiar un dato fisico movio algo de los encargos.");
    }

    @Test
    @DisplayName("venta y alquiler: cambiar el importe de uno no contamina al otro")
    void cambiarUnEncargoNoContaminaAlOtro() {
        long id = registrar(casoDe("CASA"), List.of(venta("320000"), alquiler("2800")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());

        List<OperacionSolicitada> operaciones = new ArrayList<>();
        for (EncargoFicha encargo : antes.encargos()) {
            boolean esVenta = "VENTA".equals(encargo.operacion());
            operaciones.add(new OperacionSolicitada(encargo.operacion(),
                    esVenta ? new BigDecimal("330000") : encargo.importe(), encargo.moneda(),
                    null, null, null, null, null, null, null));
        }
        ComandoEdicion espejo = comandoEspejo(antes);
        propiedades.editar(id, new ComandoEdicion(null, null, null, espejo.ubicacion(),
                espejo.titulares(), espejo.atributos(), operaciones, null), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        Set<String> permitidos = new TreeSet<>();
        for (EncargoFicha encargo : antes.encargos()) {
            if ("VENTA".equals(encargo.operacion())) {
                permitidos.add("encargo." + encargo.idEncargo() + ".importe");
                permitidos.add("encargo." + encargo.idEncargo() + ".hitos");
            }
        }
        exigirIdentico(retrato(antes), retrato(despues), permitidos,
                "Cambiar el importe de la venta movio algo del alquiler.");

        // Y lo permitido no es "cualquier cosa": el historico de la venta tiene
        // que haber CRECIDO en exactamente un hito autorizado con el importe
        // nuevo, con todo lo anterior intacto delante. Sin esta afirmacion, un
        // guardado que sobrescribiera el ultimo hito seguiria pasando en verde.
        EncargoFicha ventaAntes = encargoDe(antes, "VENTA");
        EncargoFicha ventaDespues = encargoDe(despues, "VENTA");
        assertEquals(ventaAntes.historico().size() + 1, ventaDespues.historico().size(),
                "cambiar el importe ANADE un hito; no sustituye el anterior");
        for (int i = 0; i < ventaAntes.historico().size(); i++) {
            assertEquals(ventaAntes.historico().get(i), ventaDespues.historico().get(i),
                    "el hito " + i + " de la venta cambio al anadir uno nuevo");
        }
        HitoFicha nuevo = ventaDespues.historico().get(ventaDespues.historico().size() - 1);
        assertEquals("U", nuevo.hito(), "el importe editado es un hito AUTORIZADO");
        assertEquals(0, new BigDecimal("330000").compareTo(nuevo.monto()));
    }

    /**
     * <b>Lo pactado en un encargo se edita por su id y no toca nada mas</b>
     * (Corte 0C, visto desde el editor).
     *
     * <p>Es el bloque que el gate de aislamiento no recorria: {@code condiciones}
     * entro en {@code ComandoEdicion} despues de escribirse aquel, y ningun caso
     * lo mandaba solo. La clave se toma del catalogo y no se escribe a mano:
     * este test afirma la MECANICA de la edicion por encargo, no que exista una
     * condicion concreta.
     */
    @Test
    @DisplayName("editar lo pactado en el alquiler no toca la venta ni la propiedad")
    void editarLoPactadoDeUnEncargoNoTocaNadaMas() {
        long id = registrar(casoDe("LOCAL"), List.of(venta("250000"), alquiler("3500")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());
        EncargoFicha alquiler = encargoDe(antes, "ALQUILER");
        String clave = jdbc.queryForObject("""
                select c.clave
                  from catalogo_atributo c
                  join catalogo_atributo_operacion o on o.id_catalogo_atributo = c.id_catalogo_atributo
                 where c.sujeto = 'ENCARGO' and c.del_sistema and c.tipo_dato = 'ENTERO'
                   and o.tipo_propiedad = 'L' and o.tipo_operacion = 'A'
                 order by c.clave
                 limit 1
                """, String.class);

        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null, null, null, null,
                List.of(new CondicionesDeEncargo(alquiler.idEncargo(),
                        List.of(new ValorAtributo(clave, "3")), null))), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        // Lo pactado no esta en el retrato a proposito: con cero permitidos, TODO
        // lo demas -- ubicacion, atributos, titulares, los dos encargos con sus
        // importes e historicos -- tiene que volver identico.
        exigirIdentico(retrato(antes), retrato(despues), Set.of(),
                "Editar lo pactado en el alquiler movio algo fuera de su bloque.");
        assertEquals("3", condicionDe(encargoDe(despues, "ALQUILER"), clave),
                "lo pactado quedo escrito en SU encargo");
        assertNull(condicionDe(encargoDe(despues, "VENTA"), clave),
                "y no aparecio en el otro");
    }

    private static EncargoFicha encargoDe(FichaPropiedadUniversal ficha, String operacion) {
        return ficha.encargos().stream()
                .filter(encargo -> operacion.equals(encargo.operacion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("la propiedad no tiene encargo de " + operacion));
    }

    private static String condicionDe(EncargoFicha encargo, String clave) {
        return encargo.condiciones().stream()
                .filter(condicion -> clave.equals(condicion.clave()))
                .map(AtributoFicha::valor)
                .findFirst()
                .orElse(null);
    }

    // ==================================================================
    // El encargo historico: editar hoy no reescribe el pasado
    // ==================================================================

    /**
     * La ficha universal acaba de cerrar la distincion {@code idPropiedad}
     * continuidad / {@code idEncargo} episodio. Este caso la defiende por el
     * lado de la <b>escritura</b>: editar la cosa fisica no puede convertirse en
     * una reconstruccion hecha solo desde los encargos vivos.
     */
    @Test
    @DisplayName("un encargo cerrado sobrevive a una edicion de hoy, con su historico")
    void elEncargoCerradoSobreviveALaEdicion() {
        long id = registrar(casoDe("LOCAL"), List.of(venta("250000"), alquiler("3500")));
        FichaPropiedadUniversal recienCreada = propiedades.consultar(id, actor());
        long idVenta = recienCreada.encargos().stream()
                .filter(e -> "VENTA".equals(e.operacion()))
                .map(EncargoFicha::idEncargo).findFirst().orElseThrow();

        // El encargo de venta se cerro hace meses. COMO se cerro no es lo que se
        // mide aqui —eso tiene su propio recorrido—; lo que se mide es que
        // editar hoy no lo borre. Fecha y motivo van porque `ck_captacion_cierre`
        // los exige: un encargo cerrado sin motivo no existe en este modelo. El
        // motivo es el codigo unitario 'O' -- otro -- con su detalle al lado.
        jdbc.update("""
                update captacion
                   set estado = 'C', fecha_cierre = ?,
                       motivo_cierre = 'O', detalle_motivo_cierre = ?
                 where id_captacion = ?
                """, LocalDate.now().minusMonths(2), "El propietario retiro la venta", idVenta);

        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());
        assertEquals(2, antes.encargos().size(),
                "La ficha tiene que seguir publicando el encargo cerrado.");

        ComandoEdicion espejo = comandoEspejo(antes);
        propiedades.editar(id, new ComandoEdicion(null, null, "Retocada hoy",
                espejo.ubicacion(), espejo.titulares(), espejo.atributos(),
                espejo.operaciones(), null), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        exigirIdentico(retrato(antes), retrato(despues), Set.of(),
                "Editar hoy se llevo por delante el episodio cerrado.");
    }

    // ==================================================================
    // La semantica del borrado: los tres significados, separados
    // ==================================================================

    /**
     * <b>1 de 3.</b> Un valor que existe y no se nombra sigue existiendo. Es la
     * mitad conservadora de la regla, y sin ella las otras dos no significan
     * nada: borrar solo puede ser una intencion si no borrar es el defecto.
     */
    @Test
    @DisplayName("borrado: un valor que no se nombra sobrevive a un PUT que cambia otra cosa")
    void unValorQueNoSeNombraSobrevive() {
        long id = registrar(casoDe("DEPARTAMENTO"), List.of(venta("180000")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());
        assertEquals("4", valorDe(antes, "piso"), "El caso tiene que nacer con piso.");

        propiedades.editar(id, new ComandoEdicion(null, null, "Otra descripcion",
                null, null, null, null, null), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        exigirIdentico(retrato(antes), retrato(despues), Set.of(),
                "Cambiar la descripcion movio un valor que nadie nombro.");
    }

    /**
     * <b>2 de 3.</b> Nombrarla en {@code atributosABorrar} la retira, y no
     * arrastra nada mas consigo.
     */
    @Test
    @DisplayName("borrado: nombrar una clave en atributosABorrar retira su valor")
    void nombrarUnaClaveLaRetira() {
        long id = registrar(casoDe("DEPARTAMENTO"), List.of(venta("180000")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());
        assertNotNull(valorDe(antes, "cuota_mantenimiento"), "El caso tiene que nacer con cuota.");

        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null, null, null,
                List.of("cuota_mantenimiento")), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        assertNull(valorDe(despues, "cuota_mantenimiento"),
                "La clave nombrada en atributosABorrar sigue teniendo valor.");
        exigirIdentico(retrato(antes), retrato(despues), Set.of("atributo.cuota_mantenimiento"),
                "Retirar una clave se llevo por delante algo mas.");
    }

    /**
     * <b>3 de 3.</b> Y {@code ""} no es ninguna de las dos cosas.
     *
     * <p>La cadena vacia puede ser "quitalo", puede ser un campo que la
     * pantalla no relleno y puede ser un espacio de mas. Adivinar cual de los
     * tres es exactamente la reinterpretacion que 0A contiene, asi que no se
     * adivina: se rechaza, y el valor de antes sigue donde estaba.
     */
    @Test
    @DisplayName("borrado: un valor en blanco NO se interpreta como borrado")
    void unValorEnBlancoNoEsUnBorrado() {
        long id = registrar(casoDe("DEPARTAMENTO"), List.of(venta("180000")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());

        assertThrows(ReglaNegocioException.class, () -> propiedades.editar(id,
                new ComandoEdicion(null, null, null, null, null,
                        List.of(new ValorAtributo("cuota_mantenimiento", "")), null, null),
                actor()), "Un valor en blanco tiene que rechazarse, no adivinarse.");

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        exigirIdentico(retrato(antes), retrato(despues), Set.of(),
                "Un PUT rechazado dejo la propiedad tocada.");
    }

    /**
     * El borrado viaja por <b>nombre logico</b>, no por sitio.
     *
     * <p>{@code piso} es hoy un campo canonico del agregado (V67) y
     * {@code interiorUnidad} una columna de la ubicacion; ninguna de las dos es
     * una fila de {@code atributo_propiedad}. El cliente manda los dos nombres
     * en la misma lista y no sabe nada de esa diferencia — ni le hace falta, ni
     * se rompe el dia que una de las dos cambie de sitio.
     */
    @Test
    @DisplayName("borrado: el nombre logico basta, el cliente no sabe donde vive cada clave")
    void elBorradoViajaPorNombreLogico() {
        long id = registrar(casoDe("DEPARTAMENTO"), List.of(venta("180000")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());
        assertNotNull(antes.ubicacion().interiorUnidad(), "El caso tiene que nacer con interior.");

        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null, null, null,
                List.of("piso", "interiorUnidad")), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        assertNull(valorDe(despues, "piso"), "El piso gobernado sigue ahi.");
        assertNull(despues.ubicacion().piso(), "El piso de la ubicacion sigue ahi.");
        assertNull(despues.ubicacion().interiorUnidad(), "El interior sigue ahi.");
        exigirIdentico(retrato(antes), retrato(despues),
                Set.of("atributo.piso", "ubicacion.piso", "ubicacion.interiorUnidad"),
                "Retirar dos claves logicas movio algo mas.");
    }

    /**
     * Una clave con valor <b>y</b> en la lista de borrado son dos ordenes
     * contrarias. No se elige por precedencia —cualquier orden seria una regla
     * inventada que el cliente no conoce— y no se aplica ninguna.
     */
    @Test
    @DisplayName("borrado: valor y borrado de la misma clave se rechaza, no se arbitra")
    void valorYBorradoDeLaMismaClaveSeRechaza() {
        long id = registrar(casoDe("DEPARTAMENTO"), List.of(venta("180000")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());

        assertThrows(ReglaNegocioException.class, () -> propiedades.editar(id,
                new ComandoEdicion(null, null, null, null, null,
                        List.of(new ValorAtributo("piso", "8")), null, List.of("piso")),
                actor()), "Dos ordenes contrarias tienen que avisarse.");

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        exigirIdentico(retrato(antes), retrato(despues), Set.of(),
                "El comando contradictorio se aplico a medias.");
    }

    /**
     * Lo que no se puede retirar lo dice. {@code metraje_total} es NOT NULL
     * porque una propiedad sin metraje no es una propiedad; dejarlo pasar en
     * silencio, o vaciarlo a cero, seria inventar un dato.
     */
    @Test
    @DisplayName("borrado: lo que no se puede retirar se rechaza diciendo por que")
    void loQueNoSePuedeRetirarSeRechaza() {
        long id = registrar(casoDe("DEPARTAMENTO"), List.of(venta("180000")));

        for (String clave : List.of("metraje_total", "direccion", "distrito", "inventada")) {
            assertThrows(ReglaNegocioException.class, () -> propiedades.editar(id,
                    new ComandoEdicion(null, null, null, null, null, null, null, List.of(clave)),
                    actor()), "Retirar \"" + clave + "\" tenia que rechazarse.");
        }
    }

    // ==================================================================
    // Las invariantes de 0A, ancladas en la puerta que queda
    // ==================================================================

    /**
     * <b>El uso no se inventa.</b> Primera invariante del Corte 0A.
     *
     * <p>Sucede a {@code laPuertaHeredadaReescribeElUso}, que media lo mismo
     * sobre {@code PUT /locales} -- alli el uso se fijaba a comercial en cada
     * guardado, sin mirar lo que habia. Lo que muere con aquella prueba es la
     * PUERTA, no el invariante: por eso esto no se borra, se re-ancla.
     *
     * <p>Hoy la puerta universal ni siquiera puede cambiarlo, porque
     * {@code ComandoEdicion} no tiene campo {@code uso}. Eso es mas fuerte que
     * una prueba, y aun asi la prueba hace falta: el dia que alguien anada ese
     * campo, este caso obliga a decidirlo a proposito en vez de heredarlo.
     */
    @Test
    @DisplayName("invariante: editar no reescribe el uso del inmueble")
    void editarNoReescribeElUso() {
        CasoDeTipo base = casoDe("LOCAL");
        long id = registrar(new CasoDeTipo(base.tipo(), "MIXTO", base.atributos(),
                base.familias()), List.of(alquiler("4200")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());
        assertEquals("M", antes.uso(), "El caso necesita nacer con uso mixto.");

        propiedades.editar(id, comandoEspejo(antes), actor());

        assertEquals("M", propiedades.consultar(id, actor()).uso(),
                "Guardar convirtio un inmueble mixto en otra cosa.");
    }

    /**
     * <b>Los tres que tenian tabla espejo sobreviven a una edicion ajena.</b>
     *
     * <p>Hasta V71, {@code rubro_permitido}, {@code apto_licencia_funcionamiento}
     * y {@code carga_electrica_kw} estaban protegidos por la FORMA de
     * {@code detalle_local_comercial}: eran columnas de una tabla que el editor
     * o tocaba entera o no tocaba. Al retirarla, esa proteccion estructural
     * desaparece y pasan a depender de lo mismo que el resto -- de que editar
     * otra cosa no los mueva.
     *
     * <p>Por eso este caso no es "la tabla ya no existe", que no probaria nada
     * sobre el dato: recorre {@code crear -> leer -> editar algo ajeno ->
     * guardar -> releer} y exige que los tres vuelvan con su valor y su tipo.
     * Los tres, ademas, de familias distintas -- TEXTO, BOOLEANO y DECIMAL --
     * porque el enrutador guarda cada una en una columna distinta y perder una
     * no se parece a perder las otras.
     */
    @Test
    @DisplayName("invariante: los tres que tenian tabla espejo sobreviven a una edicion ajena")
    void losTresDelEspejoSobrevivenAUnaEdicionAjena() {
        long id = registrar(casoDe("LOCAL"), List.of(alquiler("3500")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());
        assertEquals("Restaurante", valorDe(antes, "rubro_permitido"),
                "El caso necesita nacer con rubro.");
        assertNotNull(valorDe(antes, "apto_licencia_funcionamiento"),
                "El caso necesita nacer con apto para licencia.");
        assertNotNull(valorDe(antes, "carga_electrica_kw"),
                "El caso necesita nacer con carga electrica.");

        ComandoEdicion espejo = comandoEspejo(antes);
        propiedades.editar(id, new ComandoEdicion(null, null, "Se retoca solo la descripcion",
                espejo.ubicacion(), espejo.titulares(), espejo.atributos(),
                espejo.operaciones(), null), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        exigirIdentico(retrato(antes), retrato(despues), Set.of(),
                "Editar la descripcion movio alguno de los tres que dejaron el espejo.");
    }

    /**
     * Y el mismo recorrido sin devolver los atributos: la clave gobernada que
     * el comando <b>no menciona</b> tampoco se pierde.
     *
     * <p>Es el caso que la tabla espejo hacia imposible de romper y ahora no:
     * un editor que solo manda la descripcion ya no esta "dejando la fila de
     * local comercial como estaba", esta no mencionando tres claves. Que eso
     * las conserve es una decision del contrato, y esto la fija.
     */
    @Test
    @DisplayName("invariante: no mencionar una clave gobernada no la borra")
    void noMencionarUnaClaveNoLaBorra() {
        long id = registrar(casoDe("LOCAL"), List.of(alquiler("3500")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());

        propiedades.editar(id, new ComandoEdicion(null, null, "Solo la descripcion",
                null, null, null, null, null), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        assertEquals("Restaurante", valorDe(despues, "rubro_permitido"),
                "El rubro desaparecio al no mencionarlo.");
        exigirIdentico(retrato(antes), retrato(despues), Set.of(),
                "Una edicion que solo trae descripcion movio algo mas.");
    }

    // ==================================================================
    // El aislamiento entre bloques
    // ==================================================================

    /**
     * <b>Guardar un bloque no toca ningun otro.</b>
     *
     * <p>El editor universal vive dentro del expediente y edita <b>por
     * bloques</b>: cada uno pide su definicion al Core y guarda unicamente los
     * conceptos que posee. La regla que lo sostiene no admite matices:
     *
     * <blockquote>Guardar un bloque que el usuario no esta editando jamas puede
     * modificar, vaciar, completar por defecto ni reinterpretar datos de otro
     * bloque.</blockquote>
     *
     * <p>Esto se fija <b>aqui y no en Angular</b> a proposito. Una pantalla
     * puede cumplirlo hoy y dejar de cumplirlo el dia que alguien anada un
     * campo "para completar"; el contrato no. Cada bloque toca exactamente un
     * campo del comando —{@code ubicacion}, {@code atributos},
     * {@code operaciones}, {@code titulares}— y los demas viajan ausentes, que
     * por contrato significa "no lo toques".
     *
     * <p>Por eso el caso no comprueba el bloque que edita: comprueba
     * <b>todos los demas</b>, y exige que vuelvan identicos hasta el ultimo
     * campo.
     */
    private record Bloque(String nombre, String prefijoPropio) {

        @Override
        public String toString() {
            return nombre;
        }
    }

    private static Stream<Bloque> bloques() {
        return Stream.of(
                new Bloque("ubicacion", "ubicacion."),
                new Bloque("caracteristicas", "atributo."),
                new Bloque("encargo", "encargo."),
                new Bloque("titulares", "titular."));
    }

    @ParameterizedTest(name = "guardar {0} no toca ningun otro bloque")
    @MethodSource("bloques")
    @DisplayName("aislamiento: guardar un bloque no toca ningun otro")
    void guardarUnBloqueNoTocaNingunOtro(Bloque bloque) {
        long id = registrar(casoDe("LOCAL"), List.of(venta("250000"), alquiler("3500")));
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());

        propiedades.editar(id, soloElBloque(bloque, antes), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        Map<String, String> a = retrato(antes);
        Map<String, String> d = retrato(despues);
        // Lo propio del bloque editado se excluye de la comparacion; TODO lo
        // demas tiene que volver igual, incluidos los campos de identidad que
        // ningun bloque posee.
        Set<String> propios = new TreeSet<>();
        a.keySet().stream().filter(k -> k.startsWith(bloque.prefijoPropio())).forEach(propios::add);
        d.keySet().stream().filter(k -> k.startsWith(bloque.prefijoPropio())).forEach(propios::add);

        exigirIdentico(a, d, propios,
                "Guardar el bloque \"" + bloque.nombre() + "\" movio datos de otro bloque.");
    }

    /**
     * El comando que manda <b>un</b> bloque y nada mas, con un cambio real
     * dentro para que el guardado no sea un no-op disfrazado de aislamiento.
     */
    private ComandoEdicion soloElBloque(Bloque bloque, FichaPropiedadUniversal ficha) {
        ComandoEdicion espejo = comandoEspejo(ficha);
        return switch (bloque.nombre()) {
            case "ubicacion" -> new ComandoEdicion(null, null, null,
                    new Ubicacion(ficha.ubicacion().direccion(), ficha.ubicacion().distrito(),
                            "Urb. Cambiada", null, null, null, null, null, null),
                    null, null, null, null);
            case "caracteristicas" -> new ComandoEdicion(null, null, null, null, null,
                    List.of(new ValorAtributo("ambientes", "9")), null, null);
            case "encargo" -> new ComandoEdicion(null, null, null, null, null, null,
                    List.of(new OperacionSolicitada("VENTA", new BigDecimal("260000"), "USD",
                            null, null, null, null, null, null, null)), null);
            case "titulares" -> new ComandoEdicion(null, null, null, null,
                    espejo.titulares(), null, null, null);
            default -> throw new IllegalArgumentException(bloque.nombre());
        };
    }

    // ==================================================================
    // La propiedad que todavia nadie ha encargado
    // ==================================================================

    /**
     * <b>Una propiedad prospectada, con CERO encargos, tambien se conserva</b>
     * (V75).
     *
     * <p>Los ocho escenarios comerciales de arriba tienen todos su encargo, y
     * eso no constituye una invariante: desde V75 una propiedad puede existir
     * sin estar encargada -- registrada en el maestro mientras se intenta
     * captarla -- y el editor tiene que devolverla exactamente igual.
     *
     * <p>El bloque de encargo se queda fuera a proposito y no por comodidad:
     * pedir un cambio de operacion sobre una propiedad que no tiene ninguna no
     * es «no tocar nada», es pedir algo que no existe. Lo que se comprueba aqui
     * es que <b>guardar no le inventa uno</b>.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("tipos")
    @DisplayName("una propiedad sin encargos se guarda igual, y no le nace ninguno")
    void unaPropiedadSinEncargosSeConserva(CasoDeTipo caso) {
        long id = registrar(caso, List.of());
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());
        assertTrue(antes.encargos().isEmpty(), "el caso parte de cero encargos");

        ComandoEdicion espejo = comandoEspejo(antes);
        propiedades.editar(id, espejo, actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        assertTrue(despues.encargos().isEmpty(),
                "guardar le invento un encargo a una propiedad que nadie ha encargado ("
                        + caso.tipo() + ").");
        exigirIdentico(retrato(antes), retrato(despues), Set.of(),
                "Guardar sin tocar nada cambio la propiedad sin encargos (" + caso.tipo() + ").");
    }

    /**
     * Y cambiar UNA cosa tampoco se la inventa. Es el mismo caso que
     * {@link #cambiarUnaSolaCosaConservaElResto}, sobre el estado que hasta V75
     * no podia existir.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("tipos")
    @DisplayName("editar una propiedad sin encargos no la pone en oferta")
    void editarUnaPropiedadSinEncargosNoLaOfrece(CasoDeTipo caso) {
        long id = registrar(caso, List.of());
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());

        ComandoEdicion espejo = comandoEspejo(antes);
        propiedades.editar(id, new ComandoEdicion(null, null, "Otra descripcion",
                espejo.ubicacion(), espejo.titulares(), espejo.atributos(),
                espejo.operaciones(), null), actor());

        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        assertEquals("Otra descripcion", despues.descripcion(), "El cambio pedido no llego.");
        assertNull(despues.disponibilidadComercial(),
                "editar no la pone «disponible»: la oferta la abre el encargo (" + caso.tipo() + ").");
        exigirIdentico(retrato(antes), retrato(despues), Set.of("descripcion"),
                "Editar la descripcion movio algo mas en una propiedad sin encargos ("
                        + caso.tipo() + ").");
    }
    // ==================================================================
    // El espejo: la ficha vuelta comando, como haria una pantalla fiel
    // ==================================================================

    /**
     * El comando que devuelve <b>lo mismo que se leyo</b>. No es un atajo de
     * prueba: es lo que hace un editor que carga el formulario del Core y lo
     * guarda sin que el usuario toque nada.
     *
     * <p>Solo viajan los encargos <b>vivos</b>, que es lo unico que un editor
     * puede pedir cambiar. Que los cerrados sobrevivan a eso es justamente lo
     * que mide {@link #elEncargoCerradoSobreviveALaEdicion}.
     */
    private ComandoEdicion comandoEspejo(FichaPropiedadUniversal ficha) {
        List<Titular> titulares = ficha.titulares().stream()
                .map(t -> new Titular(t.idRolPropietario(), t.cuota(), t.representante()))
                .toList();
        // Un LISTA_MULTIPLE vuelve del Core por DOS caminos a la vez: `valores`
        // con la lista, y `valor` con su TEXTO DE PRESENTACION -- los elementos
        // pegados por comas, que es lo que hace `ValorLogico.comoTexto()`. NO
        // llega null: `deValores` deja texto, numero, fecha y booleano a null, y
        // `comoTexto()` cae en `String.join(", ", valores)`. Comprobado.
        //
        // Reenviar ese `valor` como escalar no es "casi igual": construye un
        // comando que dice otra cosa, y el Core lo rechaza con razon -- "el
        // atributo admite varios valores: se edita con la via de multivalor, no
        // con un valor suelto". Si el espejo no distingue, la ida y vuelta de la
        // familia mas dificil de conservar --N filas y no una-- ni siquiera
        // llega a ejecutarse. (V80.)
        List<ValorAtributo> atributos = ficha.atributos().stream()
                .map(a -> a.valores() != null
                        ? ValorAtributo.multiple(a.clave(), a.valores())
                        : new ValorAtributo(a.clave(), a.valor()))
                .toList();
        List<OperacionSolicitada> operaciones = ficha.encargos().stream()
                .filter(EncargoFicha::vivo)
                .map(e -> new OperacionSolicitada(e.operacion(), e.importe(), e.moneda(),
                        null, null, null, null, e.exclusividad(), e.inicio(), e.fin()))
                .toList();
        return new ComandoEdicion(null, null, ficha.descripcion(), ficha.ubicacion(),
                titulares, atributos, operaciones, null);
    }

    // ==================================================================
    // El retrato: todo lo que tiene que volver igual, con nombre
    // ==================================================================

    /**
     * Un mapa plano {@code campo -> valor} de <b>toda</b> la ficha.
     *
     * <p>Plano y con nombre a proposito: comparar dos objetos grandes dice "no
     * son iguales" y comparar dos mapas dice <b>que campo</b> se movio, que es
     * la unica forma de que el fallo se pueda arreglar sin depurar.
     *
     * <p>Los encargos se indexan por su <b>id</b> y no por su posicion: si uno
     * desaparece o aparece, tiene que verse como tal y no como un reordenamiento.
     */
    private Map<String, String> retrato(FichaPropiedadUniversal f) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("tipoPropiedad", f.tipoPropiedad());
        r.put("uso", f.uso());
        r.put("estadoRegistro", f.estadoRegistro());
        r.put("disponibilidadComercial", f.disponibilidadComercial());

        Ubicacion u = f.ubicacion();
        r.put("ubicacion.direccion", u.direccion());
        r.put("ubicacion.distrito", u.distrito());
        r.put("ubicacion.zonaUrbanizacion", u.zonaUrbanizacion());
        r.put("ubicacion.latitud", texto(u.latitud()));
        r.put("ubicacion.longitud", texto(u.longitud()));
        r.put("ubicacion.interiorUnidad", u.interiorUnidad());
        r.put("ubicacion.piso", u.piso());
        r.put("ubicacion.referenciaInterna", u.referenciaInterna());
        r.put("ubicacion.nombreEdificioGaleria", u.nombreEdificioGaleria());

        for (AtributoFicha a : f.atributos()) {
            // El multivalor se retrata por su LISTA, y en orden. NO es que
            // `valor()` fuera ciego: en un LISTA_MULTIPLE trae los elementos
            // pegados por comas, asi que una perdida real ya cambiaba la cadena y
            // se habria visto. Es PRECISION: esa cadena es presentacion
            // --`ValorLogico.comoTexto()` decide como se pega-- y un retrato que
            // dependa de ella confunde "cambio el formato" con "cambio el dato".
            // Se compara la estructura que el Core publica, no como la escribe.
            //
            // La moneda se retrata aparte por lo mismo: en un IMPORTE viaja
            // dentro del texto --"USD 120.5"-- y tampoco queremos que ver un
            // cambio de moneda dependa de como se formatee. (V80.)
            String clave = "atributo." + a.clave();
            if (a.valores() != null) {
                r.put(clave, String.join("|", a.valores()));
            } else {
                r.put(clave, a.valor());
            }
            if (a.moneda() != null) {
                r.put(clave + ".moneda", a.moneda());
            }
        }
        for (TitularFicha t : f.titulares()) {
            String prefijo = "titular." + t.idRolPropietario() + ".";
            r.put(prefijo + "cuota", texto(t.cuota()));
            r.put(prefijo + "representante", String.valueOf(t.representante()));
            r.put(prefijo + "desde", String.valueOf(t.desde()));
        }
        for (EncargoFicha e : f.encargos()) {
            String prefijo = "encargo." + e.idEncargo() + ".";
            r.put(prefijo + "operacion", e.operacion());
            r.put(prefijo + "estado", e.estado());
            r.put(prefijo + "vivo", String.valueOf(e.vivo()));
            r.put(prefijo + "importe", texto(e.importe()));
            r.put(prefijo + "moneda", e.moneda());
            r.put(prefijo + "exclusividad", String.valueOf(e.exclusividad()));
            r.put(prefijo + "agente", String.valueOf(e.idAgente()));
            r.put(prefijo + "inicio", String.valueOf(e.inicio()));
            r.put(prefijo + "fin", String.valueOf(e.fin()));
            r.put(prefijo + "hitos", hitos(e));
        }
        return r;
    }

    /** El valor de una clave tal como la ficha lo publica, o null si no esta. */
    private static String valorDe(FichaPropiedadUniversal ficha, String clave) {
        return ficha.atributos().stream()
                .filter(a -> clave.equals(a.clave()))
                .map(AtributoFicha::valor)
                .findFirst().orElse(null);
    }

    private static String hitos(EncargoFicha encargo) {
        List<String> serie = new ArrayList<>();
        for (HitoFicha hito : encargo.historico()) {
            serie.add(hito.hito() + " " + hito.moneda() + " " + texto(hito.monto())
                    + " " + hito.fecha());
        }
        return String.join(" | ", serie);
    }

    /**
     * Por VALOR y no por escala: {@code 350} y {@code 350.00} son el mismo
     * importe, y una diferencia de escala no es una perdida de dato. Comparar
     * las cadenas crudas convertiria este gate en un detector de formato.
     */
    private static String texto(BigDecimal valor) {
        return valor == null ? null : valor.stripTrailingZeros().toPlainString();
    }

    /**
     * Compara los dos retratos y falla nombrando <b>cada</b> diferencia.
     *
     * @param permitidos las claves que el caso pidio cambiar de verdad
     */
    private static void exigirIdentico(Map<String, String> antes, Map<String, String> despues,
                                       Set<String> permitidos, String contexto) {
        Set<String> claves = new TreeSet<>(antes.keySet());
        claves.addAll(despues.keySet());
        List<String> diferencias = new ArrayList<>();
        for (String clave : claves) {
            if (permitidos.contains(clave)) {
                continue;
            }
            String a = antes.get(clave);
            String d = despues.get(clave);
            if (!Objects.equals(a, d)) {
                diferencias.add(clave + ": \"" + a + "\"  ->  \"" + d + "\"");
            }
        }
        if (!diferencias.isEmpty()) {
            fail(contexto + "\n  " + diferencias.size() + " dato(s) cambiaron sin que nadie lo "
                    + "pidiera:\n  - " + String.join("\n  - ", diferencias));
        }
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    private static CasoDeTipo casoDe(String tipo) {
        return tipos().filter(c -> c.tipo().equals(tipo)).findFirst().orElseThrow();
    }

    private static String codigoDe(String tipo) {
        return switch (tipo) {
            case "LOCAL" -> "L";
            case "OFICINA" -> "O";
            case "DEPARTAMENTO" -> "D";
            case "CASA" -> "C";
            case "TERRENO" -> "T";
            case "ALMACEN" -> "A";
            default -> "X";
        };
    }

    private static OperacionSolicitada venta(String importe) {
        return new OperacionSolicitada("VENTA", new BigDecimal(importe), "USD",
                null, null, null, null, Boolean.TRUE, LocalDate.now().minusMonths(6), null);
    }

    private static OperacionSolicitada alquiler(String importe) {
        return new OperacionSolicitada("ALQUILER", new BigDecimal(importe), "PEN",
                null, null, null, null, Boolean.FALSE, LocalDate.now().minusMonths(6), null);
    }

    /** El piso que declara el caso, o {@code null} si su tipo no lo gobierna. */
    private static String pisoDeclarado(CasoDeTipo caso) {
        return caso.atributos().stream()
                .filter(atributo -> "piso".equals(atributo.clave()))
                .map(ValorAtributo::valor)
                .findFirst()
                .orElse(null);
    }

    /**
     * Registra el caso con la ubicacion <b>completa</b> —las nueve piezas— y con
     * una titularidad que empezo hace anios.
     *
     * <p>Las dos cosas importan. Una ubicacion a medias no puede demostrar que
     * un guardado borre lo que no trae, y una titularidad de hoy esconde que
     * reemplazarla le cambia la fecha de inicio.
     *
     * <h2>El {@code piso} sale del caso, no de aqui (4.P, segunda vuelta)</h2>
     * Este fixture mandaba {@code "4"} en {@code ubicacion.piso} para los SIETE
     * tipos, <b>a la vez</b> que el caso declaraba su propio {@code piso} entre
     * los atributos —{@code "3"} en LOCAL, {@code "7"} en OFICINA—. O sea: dos
     * valores DISTINTOS para el mismo dato en la misma peticion.
     *
     * <p>El Core lo aceptaba y lo resolvia <b>en silencio por orden de
     * escritura</b>: {@code aplicarUbicacion} escribia el 4 y el enrutador de
     * atributos lo pisaba con el 3. Eso era posible porque {@code piso} viaja
     * por DOS huecos del cable, y solo uno pasaba por el enrutador. Al enrutar
     * los dos, la contradiccion sale a la luz — que es lo que tenia que pasar.
     *
     * <p>Ahora el piso lo pone el caso y llega una sola vez. Y no se manda para
     * los tipos a los que <b>el catalogo dice que no aplica</b> —CASA, TERRENO,
     * ALMACEN, OTRO—: rellenar ahi un hueco que ningun tipo gobierna era
     * ejercitar el agujero, no cubrirlo. Ninguna asercion se ha tocado.
     */
    private long registrar(CasoDeTipo caso, List<OperacionSolicitada> operaciones) {
        Actor actor = actor();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
        assertNotNull(idPropietario, "La base de pruebas necesita al menos un propietario.");

        long sufijo = System.nanoTime() % 1000000;
        var resultado = propiedades.registrar(new ComandoRegistro(
                null, null, null, caso.tipo(), caso.uso(),
                "Caso de conservacion " + caso.tipo(),
                new Ubicacion("Av. Conservacion " + sufijo, "Miraflores",
                        "Urb. San Antonio", new BigDecimal("-12.1214"), new BigDecimal("-77.0297"),
                        "Interior 402", pisoDeclarado(caso), "Frente al parque", "Edificio Roma"),
                List.of(new Titular(idPropietario, null, Boolean.TRUE)),
                caso.atributos(), operaciones, null), actor);

        // La titularidad viene de lejos: sin esto, reemplazarla el mismo dia en
        // que se creo no cambia ninguna fecha y la fuga queda invisible.
        jdbc.update("""
                update titularidad_propiedad set vigente_desde = ?
                 where id_propiedad = ? and vigente_hasta is null
                """, LocalDate.now().minusYears(4), resultado.idPropiedad());

        return resultado.idPropiedad();
    }

    private Actor actor() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 order by a.id_persona_rol limit 1
                """).stream().findFirst().orElseThrow();
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }
}
