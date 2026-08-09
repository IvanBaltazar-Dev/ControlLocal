package com.controllocal.app.almacen;

import com.controllocal.web.almacen.AlmacenDisco;
import com.controllocal.web.almacen.AlmacenDocumentos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Concilia y migra los binarios del almacen. Es la segunda mitad del punto 4.3
 * del plan maestro: {@code AlmacenS3} dice donde <b>iran</b> los archivos
 * nuevos; esto mueve los que ya existen y comprueba que no falte ninguno.
 *
 * <h3>Como se ejecuta</h3>
 *
 * No arranca nunca por si sola: hace falta {@code controllocal.almacen.migracion.modo}.
 * <pre>
 *   java -jar app.jar --spring.main.web-application-type=none \
 *        --controllocal.almacen.migracion.modo=conciliar
 *   java -jar app.jar --spring.main.web-application-type=none \
 *        --controllocal.almacen.migracion.modo=migrar
 * </pre>
 *
 * <p><b>Termina sola y con codigo de salida</b>: {@code 0} si no hay averias,
 * {@code 1} si las hay. Sin eso levantaria el API y se quedaria viva
 * indefinidamente —una herramienta de linea de comandos que no vuelve al
 * prompt es una herramienta que nadie puede meter en un script—, y el codigo
 * distinto de cero es lo que deja encadenarla: conciliar, y solo migrar si el
 * informe salio limpio. {@code web-application-type=none} ahorra ademas
 * levantar un servidor que no va a atender a nadie.
 *
 * <p>El origen es <b>siempre el disco</b> —es de donde se viene— y el destino
 * es el proveedor configurado. Con {@code ALMACEN_PROVEEDOR=DISCO} el destino
 * seria el propio origen, asi que se niega a correr en vez de copiar archivos
 * sobre si mismos.
 *
 * <h3>Que hace, y que NO hace</h3>
 *
 * <p><b>No borra el origen.</b> Terminar la migracion no apaga el disco: los
 * archivos se quedan donde estan hasta que el corte este verificado, porque el
 * unico plan de vuelta atras que funciona de verdad es que los datos viejos
 * sigan ahi. Vaciarlo es una decision aparte y posterior.
 *
 * <p><b>Es idempotente.</b> Escribe en la clave exacta, asi que repetirla tras
 * un corte de red reescribe los mismos bytes en el mismo sitio. Y salta lo que
 * ya esta con el mismo tamano, de modo que la segunda pasada es barata.
 *
 * <p><b>Verifica lo que copia.</b> Despues de escribir vuelve a leer del
 * destino y compara el contenido. Una migracion que solo cuenta ficheros
 * copiados no distingue "se copio" de "se copio bien", y el dia que se apague
 * el origen esa diferencia ya no se puede investigar.
 *
 * <h3>Los dos hallazgos que importan</h3>
 *
 * <ul>
 *   <li><b>Referencias rotas</b>: una fila de PostgreSQL que apunta a un
 *       binario que no esta. El usuario ve un hueco donde deberia haber su DNI.
 *       <b>Migrar no las arregla</b> y por eso se informan aparte: lo que se
 *       perdio no aparece por cambiar de almacen.</li>
 *   <li><b>Huerfanos</b>: binarios que ya no referencia nadie. Ocupan, y si
 *       contienen datos personales no deberian seguir ahi. <b>No se migran</b>
 *       a proposito: copiarlos seria llevarse la basura a la casa nueva.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "controllocal.almacen.migracion.modo")
public class MigracionAlmacen implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigracionAlmacen.class);

    /** Sin averias. */
    public static final int CODIGO_LIMPIO = 0;
    /** Hay referencias rotas o fallos de copia. Distinto de 1, que es "no arranco". */
    public static final int CODIGO_CON_AVERIAS = 2;

    private final InventarioDeClaves inventario;
    private final AlmacenDocumentos destino;
    private final String directorioOrigen;
    private final String modo;
    /** Nulo en los tests: ver {@link #terminar(int)}. */
    private final org.springframework.context.ConfigurableApplicationContext contexto;

    // @Autowired explicito: al haber dos constructores, Spring deja de deducir
    // cual usar y busca uno sin argumentos, que no existe. El fallo llega en
    // el arranque y no en compilacion.
    @org.springframework.beans.factory.annotation.Autowired
    public MigracionAlmacen(InventarioDeClaves inventario,
                            AlmacenDocumentos destino,
                            @Value("${controllocal.almacen.directorio:}") String directorioOrigen,
                            @Value("${controllocal.almacen.migracion.modo}") String modo,
                            org.springframework.context.ConfigurableApplicationContext contexto) {
        this.inventario = inventario;
        this.destino = destino;
        this.directorioOrigen = directorioOrigen;
        this.modo = modo == null ? "" : modo.trim().toLowerCase();
        this.contexto = contexto;
    }

    /** Para pruebas: sin contexto, {@link #terminar(int)} no mata la JVM. */
    MigracionAlmacen(InventarioDeClaves inventario, AlmacenDocumentos destino,
                     String directorioOrigen, String modo) {
        this(inventario, destino, directorioOrigen, modo, null);
    }

    /** Lo que encontro la conciliacion. Publico para poder probarlo sin arrancar nada. */
    public record Informe(
            int fuentesMiradas,
            int referenciadas,
            List<String> referenciasRotas,
            List<String> huerfanos,
            int yaEnDestino,
            int copiados,
            List<String> fallidos) {

        public boolean sinAverias() {
            return referenciasRotas.isEmpty() && fallidos.isEmpty();
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!modo.equals("conciliar") && !modo.equals("migrar")) {
            throw new IllegalArgumentException("controllocal.almacen.migracion.modo debe ser "
                    + "'conciliar' o 'migrar', y llego '" + modo + "'.");
        }
        if ("DISCO".equals(destino.proveedor())) {
            throw new IllegalStateException("""
                    El destino de la migracion es DISCO, que es tambien el origen.
                    Arranque con ALMACEN_PROVEEDOR=S3 (y su bucket) para migrar hacia el bucket.""");
        }
        if (directorioOrigen == null || directorioOrigen.isBlank()) {
            throw new IllegalStateException("controllocal.almacen.directorio esta vacio: no hay "
                    + "origen desde el que migrar. Es el directorio donde el proveedor DISCO "
                    + "dejo los binarios.");
        }

        Informe informe = ejecutar(new AlmacenDisco(directorioOrigen), modo.equals("migrar"));
        log.info(informeLegible(informe));
        // 2 y no 1 para las averias: un arranque fallido ya sale con 1, y un
        // script que trate los dos igual no sabria distinguir "el informe
        // encontro roturas" de "la herramienta ni siquiera levanto".
        terminar(informe.sinAverias() ? CODIGO_LIMPIO : CODIGO_CON_AVERIAS);
    }

    /**
     * Cierra el contexto y devuelve el codigo al sistema.
     *
     * <p>Se separa en un metodo para poder <b>probar todo lo demas sin que el
     * test se suicide</b>: {@code System.exit} en mitad de una suite mata la
     * JVM de surefire y el resto de pruebas no llega a correr.
     */
    void terminar(int codigo) {
        if (contexto != null) {
            System.exit(org.springframework.boot.SpringApplication.exit(contexto, () -> codigo));
        }
    }

    /**
     * @param origen  el almacen del que se viene
     * @param copiar  false = solo mirar; true = ademas copiar lo que falte
     */
    public Informe ejecutar(AlmacenDocumentos origen, boolean copiar) {
        List<InventarioDeClaves.Referencia> referencias = inventario.referencias();
        Set<String> enOrigen = origen.listarClaves();
        Set<String> enDestino = destino.listarClaves();

        List<String> rotas = new ArrayList<>();
        List<String> fallidos = new ArrayList<>();
        int yaEstaban = 0;
        int copiados = 0;

        for (InventarioDeClaves.Referencia referencia : referencias) {
            String clave = referencia.clave();
            if (enDestino.contains(clave)) {
                yaEstaban++;
                continue;
            }
            if (!enOrigen.contains(clave)) {
                // Ni en el destino ni en el origen: el binario no existe en
                // ninguna parte y la fila apunta al vacio.
                rotas.add(clave + "  (" + referencia.fuente() + ")");
                continue;
            }
            if (!copiar) {
                continue;
            }
            try {
                copiarVerificando(origen, clave);
                copiados++;
            } catch (RuntimeException error) {
                fallidos.add(clave + "  -> " + error.getMessage());
            }
        }

        // Huerfanos: esta el binario y no lo referencia nadie.
        Set<String> referenciadas = new LinkedHashSet<>();
        referencias.forEach(r -> referenciadas.add(r.clave()));
        List<String> huerfanos = enOrigen.stream()
                .filter(clave -> !referenciadas.contains(clave))
                .toList();

        return new Informe(inventario.cuantasFuentes(), referencias.size(),
                rotas, huerfanos, yaEstaban, copiados, fallidos);
    }

    /**
     * Copia y <b>vuelve a leer del destino para comparar</b>. Si la relectura
     * no cuadra se deja constancia como fallo en vez de contarlo como copiado:
     * un informe que dice "500 copiados" y esconde uno truncado es peor que no
     * tener informe, porque autoriza a apagar el origen.
     */
    private void copiarVerificando(AlmacenDocumentos origen, String clave) {
        var archivo = origen.abrir(clave).orElseThrow(
                () -> new IllegalStateException("desaparecio del origen mientras se migraba"));

        destino.guardarEnClave(clave, archivo.contenido(), archivo.contentType());

        Optional<AlmacenDocumentos.ArchivoDescargado> releido = destino.abrir(clave);
        if (releido.isEmpty()) {
            throw new IllegalStateException("se escribio pero no se puede releer del destino");
        }
        if (!java.util.Arrays.equals(archivo.contenido(), releido.get().contenido())) {
            throw new IllegalStateException("el contenido releido NO coincide con el original");
        }
    }

    static String informeLegible(Informe i) {
        StringBuilder sb = new StringBuilder("\n=== Conciliacion del almacen ===\n");
        sb.append("Columnas de clave miradas: ").append(i.fuentesMiradas()).append('\n');
        sb.append("Claves referenciadas por PostgreSQL: ").append(i.referenciadas()).append('\n');
        sb.append("Ya estaban en el destino: ").append(i.yaEnDestino()).append('\n');
        sb.append("Copiadas y verificadas ahora: ").append(i.copiados()).append('\n');

        sb.append("\nReferencias ROTAS (fila sin binario): ").append(i.referenciasRotas().size());
        if (!i.referenciasRotas().isEmpty()) {
            sb.append("  <-- migrar NO las arregla; lo perdido no vuelve por cambiar de almacen");
            i.referenciasRotas().forEach(r -> sb.append("\n  - ").append(r));
        }

        sb.append("\n\nHuerfanos en el origen (binario sin fila): ").append(i.huerfanos().size());
        if (!i.huerfanos().isEmpty()) {
            sb.append("  <-- NO se migran a proposito");
            i.huerfanos().forEach(h -> sb.append("\n  - ").append(h));
        }

        if (!i.fallidos().isEmpty()) {
            sb.append("\n\nFALLOS al copiar: ").append(i.fallidos().size());
            i.fallidos().forEach(f -> sb.append("\n  - ").append(f));
        }

        sb.append("\n\n").append(i.sinAverias()
                ? "Sin averias: cada fila con binario lo tiene en el destino."
                : "HAY AVERIAS. No apague el origen hasta resolverlas.");
        return sb.append('\n').toString();
    }
}
