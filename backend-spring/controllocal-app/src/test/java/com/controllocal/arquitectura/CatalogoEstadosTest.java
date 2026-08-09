package com.controllocal.arquitectura;

import com.controllocal.domain.comun.EstadosDominio;
import com.controllocal.domain.comun.EstadosDominio.Codigo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Impide agregar un codigo o enum de estado sin documentarlo. */
class CatalogoEstadosTest {

    @Test
    void todosLosEnumsSonUnitariosEstrictosYEstanDocumentados() throws Exception {
        String documento = Files.readString(localizarMatriz());
        List<Class<?>> enums = Arrays.stream(EstadosDominio.class.getDeclaredClasses())
                .filter(Class::isEnum)
                .filter(Codigo.class::isAssignableFrom)
                .toList();

        assertTrue(enums.size() >= 17, "Se perdieron catalogos de estado centralizados");
        for (Class<?> tipo : enums) {
            Method desde = tipo.getMethod("desde", String.class);
            for (Object constante : tipo.getEnumConstants()) {
                Codigo estado = (Codigo) constante;
                assertEquals(1, estado.codigo().length(), tipo.getSimpleName());
                assertEquals(constante, desde.invoke(null, estado.codigo()));
                assertTrue(documento.contains("| `" + tipo.getSimpleName() + "` | `"
                                + estado.codigo() + "` |"),
                        tipo.getSimpleName() + " " + estado.codigo() + " no esta documentado");
            }
            InvocationTargetException error = assertThrows(InvocationTargetException.class,
                    () -> desde.invoke(null, "?"), tipo.getSimpleName());
            assertTrue(error.getCause() instanceof IllegalArgumentException);
        }
    }

    private static Path localizarMatriz() {
        return List.of(
                        Path.of("docs", "ai", "matriz-codigos-estado.md"),
                        Path.of("..", "docs", "ai", "matriz-codigos-estado.md"),
                        Path.of("..", "..", "docs", "ai", "matriz-codigos-estado.md"))
                .stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontro docs/ai/matriz-codigos-estado.md"));
    }
}
