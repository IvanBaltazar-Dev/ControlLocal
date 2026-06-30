package com.controllocal.rest.reports;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.design.JRCompiler;

public final class ReportTemplateCompiler {

    private ReportTemplateCompiler() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Uso: ReportTemplateCompiler <directorio-jrxml> <directorio-salida>");
        }
        Path fuente = Path.of(args[0]);
        Path salida = Path.of(args[1]);
        if (!Files.isDirectory(fuente)) {
            return;
        }
        Files.createDirectories(salida);
        String compilerClasspath = compilerClasspath();
        if (!compilerClasspath.isBlank()) {
            System.setProperty("net.sf.jasperreports.compiler.classpath", compilerClasspath);
            DefaultJasperReportsContext.getInstance().setProperty(JRCompiler.COMPILER_CLASSPATH, compilerClasspath);
        }
        List<Path> plantillas;
        try (var stream = Files.list(fuente)) {
            plantillas = stream
                    .filter(path -> path.getFileName().toString().endsWith(".jrxml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        for (Path jrxml : plantillas) {
            String nombre = jrxml.getFileName().toString().replaceFirst("\\.jrxml$", ".jasper");
            try {
                JasperCompileManager.compileReportToFile(jrxml.toString(), salida.resolve(nombre).toString());
            } catch (Exception e) {
                throw new IllegalStateException(
                        "No se pudo compilar la plantilla Jasper " + jrxml + ". " + detalle(e),
                        e);
            }
        }
    }

    private static String detalle(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable actual = error;
        for (int i = 0; actual != null && i < 10; i++) {
            if (i > 0) {
                sb.append(" Causa ").append(i).append(": ");
            }
            sb.append(actual.getClass().getName());
            if (actual.getMessage() != null && !actual.getMessage().isBlank()) {
                sb.append(" - ").append(actual.getMessage());
            }
            for (Throwable suppressed : actual.getSuppressed()) {
                sb.append(" Suprimida: ").append(suppressed.getClass().getName());
                if (suppressed.getMessage() != null && !suppressed.getMessage().isBlank()) {
                    sb.append(" - ").append(suppressed.getMessage());
                }
            }
            actual = actual.getCause();
        }
        return sb.toString();
    }

    private static String compilerClasspath() {
        Set<String> entries = new LinkedHashSet<>();
        String javaClasspath = System.getProperty("java.class.path");
        if (javaClasspath != null && !javaClasspath.isBlank()) {
            for (String item : javaClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (!item.isBlank()) {
                    entries.add(item);
                }
            }
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        while (loader != null) {
            if (loader instanceof URLClassLoader urlClassLoader) {
                for (URL url : urlClassLoader.getURLs()) {
                    if ("file".equalsIgnoreCase(url.getProtocol())) {
                        try {
                            entries.add(Path.of(url.toURI()).toString());
                        } catch (Exception ignored) {
                            // Si una URL no se puede convertir a Path, el resto del classpath sigue siendo util.
                        }
                    }
                }
            }
            loader = loader.getParent();
        }
        addCodeSource(entries, JasperCompileManager.class);
        addCodeSource(entries, JRCompiler.class);
        return String.join(File.pathSeparator, entries);
    }

    private static void addCodeSource(Set<String> entries, Class<?> type) {
        var source = type.getProtectionDomain() != null ? type.getProtectionDomain().getCodeSource() : null;
        if (source != null && source.getLocation() != null && "file".equalsIgnoreCase(source.getLocation().getProtocol())) {
            try {
                entries.add(Path.of(source.getLocation().toURI()).toString());
            } catch (Exception ignored) {
                // If a URL cannot be converted to Path, the remaining classpath is still useful.
            }
        }
    }
}
