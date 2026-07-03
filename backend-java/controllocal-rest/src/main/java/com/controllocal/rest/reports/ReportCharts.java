package com.controllocal.rest.reports;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

final class ReportCharts {

    private static final int SCALE = 3;

    private static final Color NAVY = new Color(0x07, 0x1B, 0x4B);
    private static final Color BLUE = new Color(0x00, 0x67, 0xFF);
    private static final Color GREEN = new Color(0x2F, 0x7D, 0x52);
    private static final Color CYAN = new Color(0x00, 0xAE, 0xEF);
    private static final Color PURPLE = new Color(0x6C, 0x5C, 0xE7);
    private static final Color ORANGE = new Color(0xF2, 0x99, 0x4A);
    private static final Color RED = new Color(0xC0, 0x47, 0x3C);
    private static final Color TEXT = new Color(0x1B, 0x24, 0x35);
    private static final Color MUTED = new Color(0x66, 0x70, 0x85);
    private static final Color GRID = new Color(0xE1, 0xE7, 0xF0);
    private static final Color SOFT = new Color(0xF4, 0xF7, 0xFB);

    private static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    private static final Font LABEL = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font REGULAR = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

    private ReportCharts() {
    }

    static Image propietario(int consultas, int visitas) {
        int conversion = pct(visitas, consultas);
        List<Item> items = List.of(
                new Item("Consultas", Math.max(0, consultas), "interesados", BLUE),
                new Item("Visitas", Math.max(0, visitas), "recorridos", GREEN));
        BufferedImage img = canvas(520, 142);
        Graphics2D g = graphics(img);
        title(g, "Interes generado en el periodo", 18, 24);
        pill(g, "Conversion a visita: " + conversion + "%", 336, 12, 166, 24, conversion >= 30 ? GREEN : ORANGE);
        horizontalBars(g, items, 18, 45, 330, 66);
        drawMetric(g, "Lectura", consultas == 0 && visitas == 0
                ? "Sin actividad registrada"
                : visitas + " de " + consultas + " consultas llegaron a visita",
                370, 55, 132, 48);
        g.dispose();
        return img;
    }

    static Image tendencia(List<String> labels, List<Integer> captaciones, List<Integer> cierres,
            List<Integer> conversion) {
        Series series = compact(labels, captaciones, cierres, conversion, 12);
        BufferedImage img = canvas(520, 150);
        Graphics2D g = graphics(img);
        title(g, "Tendencia comercial", 18, 24);
        legend(g, 300, 14, BLUE, "Captaciones", GREEN, "Cierres", ORANGE, "Conv.");

        int x = 38, y = 42, w = 444, h = 82;
        axis(g, x, y, w, h);
        int max = Math.max(1, Math.max(max(series.a()), max(series.b())));
        int n = series.labels().size();
        if (n == 0) {
            empty(g, "Sin datos de tendencia", x, y + 28, w);
            g.dispose();
            return img;
        }
        int step = Math.max(10, w / n);
        int barW = Math.max(4, step / 3);
        List<Integer> lineX = new ArrayList<>();
        List<Integer> lineY = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int cx = x + i * step + step / 2;
            int capH = (int) Math.round(series.a().get(i) * (h - 12.0) / max);
            int cierreH = (int) Math.round(series.b().get(i) * (h - 12.0) / max);
            g.setColor(BLUE);
            g.fillRoundRect(cx - barW - 1, y + h - capH, barW, capH, 4, 4);
            g.setColor(GREEN);
            g.fillRoundRect(cx + 1, y + h - cierreH, barW, cierreH, 4, 4);
            int conv = series.c().isEmpty() ? 0 : series.c().get(i);
            lineX.add(cx);
            lineY.add(y + h - (int) Math.round(conv * (h - 12.0) / 100.0));
        }
        line(g, lineX, lineY, ORANGE);
        labelTicks(g, series.labels(), x, y + h + 14, step, 6);
        g.dispose();
        return img;
    }

    static Image pie(String title, List<Item> items) {
        BufferedImage img = canvas(246, 142);
        Graphics2D g = graphics(img);
        title(g, title, 14, 22);
        int total = items.stream().mapToInt(Item::value).sum();
        int cx = 62, cy = 76, size = 76;
        if (total <= 0) {
            g.setColor(SOFT);
            g.fillOval(cx - size / 2, cy - size / 2, size, size);
            g.setColor(GRID);
            g.drawOval(cx - size / 2, cy - size / 2, size, size);
        } else {
            int start = 90;
            for (Item item : items) {
                int angle = (int) Math.round(item.value() * 360.0 / total);
                g.setColor(item.color());
                g.fillArc(cx - size / 2, cy - size / 2, size, size, start, -angle);
                start -= angle;
            }
        }
        g.setStroke(new BasicStroke(3f));
        g.setColor(Color.WHITE);
        g.drawOval(cx - size / 2 + 20, cy - size / 2 + 20, size - 40, size - 40);
        g.setStroke(new BasicStroke(1f));
        int ly = 50;
        for (Item item : items) {
            g.setColor(item.color());
            g.fillRoundRect(126, ly - 8, 10, 10, 3, 3);
            g.setColor(TEXT);
            g.setFont(SMALL);
            g.drawString(shorten(item.label(), 16), 142, ly);
            g.setColor(MUTED);
            g.drawString(item.value() + " (" + pct(item.value(), total) + "%)", 142, ly + 13);
            ly += 30;
            if (ly > 132) break;
        }
        g.dispose();
        return img;
    }

    static Image funnel(List<Item> items) {
        BufferedImage img = canvas(246, 142);
        Graphics2D g = graphics(img);
        title(g, "Embudo de conversion", 14, 22);
        int max = Math.max(1, max(items.stream().map(Item::value).toList()));
        int y = 42;
        for (Item item : items) {
            int w = Math.max(4, (int) Math.round(item.value() * 152.0 / max));
            g.setColor(new Color(0xEA, 0xF1, 0xFF));
            g.fillRoundRect(76, y - 10, 152, 13, 7, 7);
            g.setColor(item.color());
            g.fillRoundRect(76, y - 10, w, 13, 7, 7);
            g.setFont(SMALL);
            g.setColor(TEXT);
            g.drawString(shorten(item.label(), 12), 14, y);
            g.setColor(MUTED);
            g.drawString(item.value() + " " + item.detail(), 76, y + 15);
            y += 29;
            if (y > 132) break;
        }
        g.dispose();
        return img;
    }

    static Image performance(List<Item> items) {
        BufferedImage img = canvas(246, 68);
        Graphics2D g = graphics(img);
        g.setFont(LABEL);
        g.setColor(NAVY);
        g.drawString("Desempeno por responsable", 8, 14);
        int max = Math.max(1, max(items.stream().map(Item::value).toList()));
        int y = 34;
        for (Item item : items.stream().limit(2).toList()) {
            g.setFont(SMALL);
            g.setColor(TEXT);
            g.drawString(shorten(item.label(), 15), 8, y);
            g.setColor(SOFT);
            g.fillRoundRect(84, y - 10, 116, 11, 6, 6);
            g.setColor(item.color());
            g.fillRoundRect(84, y - 10, Math.max(4, (int) Math.round(item.value() * 116.0 / max)), 11, 6, 6);
            g.setColor(MUTED);
            g.drawString(shorten(item.detail(), 14), 206, y);
            y += 18;
        }
        if (items.isEmpty()) {
            empty(g, "Sin actividad registrada", 8, 42, 220);
        }
        g.dispose();
        return img;
    }

    static Item item(String label, int value, String detail, int color) {
        return new Item(label, Math.max(0, value), detail == null ? "" : detail, new Color(color));
    }

    static int blue() { return BLUE.getRGB() & 0xFFFFFF; }
    static int green() { return GREEN.getRGB() & 0xFFFFFF; }
    static int cyan() { return CYAN.getRGB() & 0xFFFFFF; }
    static int purple() { return PURPLE.getRGB() & 0xFFFFFF; }
    static int orange() { return ORANGE.getRGB() & 0xFFFFFF; }
    static int red() { return RED.getRGB() & 0xFFFFFF; }

    private static BufferedImage canvas(int width, int height) {
        BufferedImage img = new BufferedImage(width * SCALE, height * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width * SCALE, height * SCALE);
        g.dispose();
        return img;
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        prepare(g);
        g.scale(SCALE, SCALE);
        return g;
    }

    private static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static void title(Graphics2D g, String text, int x, int y) {
        g.setFont(TITLE);
        g.setColor(NAVY);
        g.drawString(text, x, y);
    }

    private static void horizontalBars(Graphics2D g, List<Item> items, int x, int y, int w, int h) {
        int max = Math.max(1, max(items.stream().map(Item::value).toList()));
        int row = h / Math.max(1, items.size());
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int yy = y + i * row + 12;
            g.setFont(LABEL);
            g.setColor(TEXT);
            g.drawString(item.label(), x, yy);
            g.setColor(SOFT);
            g.fillRoundRect(x + 92, yy - 11, w - 122, 12, 6, 6);
            g.setColor(item.color());
            int fill = Math.max(4, (int) Math.round(item.value() * (w - 122.0) / max));
            g.fillRoundRect(x + 92, yy - 11, fill, 12, 6, 6);
            g.setFont(SMALL);
            String value = item.value() + " " + item.detail();
            FontMetrics fm = g.getFontMetrics();
            if (fill > fm.stringWidth(value) + 12) {
                g.setColor(Color.WHITE);
                g.drawString(value, x + 92 + fill - fm.stringWidth(value) - 8, yy);
            } else {
                g.setColor(MUTED);
                g.drawString(value, x + 92 + fill + 8, yy);
            }
        }
    }

    private static void drawMetric(Graphics2D g, String label, String value, int x, int y, int w, int h) {
        g.setColor(SOFT);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setFont(SMALL);
        g.setColor(MUTED);
        g.drawString(label, x + 12, y + 16);
        g.setFont(REGULAR);
        g.setColor(TEXT);
        drawWrapped(g, value, x + 12, y + 33, w - 24, 13);
    }

    private static void pill(Graphics2D g, String text, int x, int y, int w, int h, Color color) {
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 35));
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(color);
        g.setFont(SMALL.deriveFont(Font.BOLD));
        center(g, text, x, y + 16, w);
    }

    private static void axis(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(GRID);
        for (int i = 0; i <= 3; i++) {
            int yy = y + i * h / 3;
            g.drawLine(x, yy, x + w, yy);
        }
        g.setColor(MUTED);
        g.drawLine(x, y + h, x + w, y + h);
    }

    private static void legend(Graphics2D g, int x, int y, Color c1, String l1, Color c2, String l2, Color c3, String l3) {
        legendItem(g, x, y, c1, l1);
        legendItem(g, x + 74, y, c2, l2);
        legendItem(g, x + 126, y, c3, l3);
    }

    private static void legendItem(Graphics2D g, int x, int y, Color c, String text) {
        g.setColor(c);
        g.fillRoundRect(x, y, 10, 10, 3, 3);
        g.setColor(MUTED);
        g.setFont(SMALL);
        g.drawString(text, x + 14, y + 9);
    }

    private static void line(Graphics2D g, List<Integer> xs, List<Integer> ys, Color color) {
        if (xs.size() < 2) return;
        Path2D path = new Path2D.Double();
        path.moveTo(xs.get(0), ys.get(0));
        for (int i = 1; i < xs.size(); i++) {
            path.lineTo(xs.get(i), ys.get(i));
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(2.2f));
        g.draw(path);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < xs.size(); i++) {
            g.fillOval(xs.get(i) - 3, ys.get(i) - 3, 6, 6);
        }
    }

    private static void labelTicks(Graphics2D g, List<String> labels, int x, int y, int step, int maxLabels) {
        int every = Math.max(1, (int) Math.ceil(labels.size() / (double) maxLabels));
        g.setFont(SMALL);
        g.setColor(MUTED);
        for (int i = 0; i < labels.size(); i += every) {
            g.drawString(shorten(labels.get(i), 8), x + i * step + 2, y);
        }
    }

    private static void empty(Graphics2D g, String text, int x, int y, int w) {
        g.setColor(MUTED);
        g.setFont(REGULAR);
        center(g, text, x, y, w);
    }

    private static void center(Graphics2D g, String text, int x, int y, int w) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x + Math.max(0, (w - fm.stringWidth(text)) / 2), y);
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        int yy = y;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                g.drawString(line.toString(), x, yy);
                yy += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            g.drawString(line.toString(), x, yy);
        }
    }

    private static Series compact(List<String> labels, List<Integer> a, List<Integer> b, List<Integer> c, int maxPoints) {
        int n = Math.min(safeSize(labels), Math.min(safeSize(a), safeSize(b)));
        if (n <= maxPoints) {
            return new Series(safeLabels(labels, n), safeValues(a, n), safeValues(b, n), safeValues(c, n));
        }
        List<String> outLabels = new ArrayList<>();
        List<Integer> outA = new ArrayList<>();
        List<Integer> outB = new ArrayList<>();
        List<Integer> outC = new ArrayList<>();
        int chunk = Math.max(1, (int) Math.ceil(n / (double) maxPoints));
        for (int start = 0; start < n; start += chunk) {
            int end = Math.min(n, start + chunk);
            outLabels.add(labels.get(start) + "-" + labels.get(end - 1));
            int sumA = 0, sumB = 0, sumC = 0;
            for (int i = start; i < end; i++) {
                sumA += value(a, i);
                sumB += value(b, i);
                sumC += value(c, i);
            }
            outA.add(sumA);
            outB.add(sumB);
            outC.add(Math.round(sumC / (float) (end - start)));
        }
        return new Series(outLabels, outA, outB, outC);
    }

    private static int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static List<String> safeLabels(List<String> labels, int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(labels.get(i));
        return out;
    }

    private static List<Integer> safeValues(List<Integer> values, int n) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(value(values, i));
        return out;
    }

    private static int value(List<Integer> values, int i) {
        if (values == null || i < 0 || i >= values.size() || values.get(i) == null) return 0;
        return Math.max(0, values.get(i));
    }

    private static int max(List<Integer> values) {
        return values == null ? 0 : values.stream().mapToInt(v -> v == null ? 0 : v).max().orElse(0);
    }

    private static int pct(int part, int total) {
        return total <= 0 ? 0 : Math.min(100, (int) Math.round(part * 100.0 / total));
    }

    private static String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)) + "...";
    }

    record Item(String label, int value, String detail, Color color) {
    }

    private record Series(List<String> labels, List<Integer> a, List<Integer> b, List<Integer> c) {
    }
}
