package transpiler.cobol2java.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaIndexTest {
    /** A small library with every kind of member the index reads. */
    static final Map<String, String> LIBRARY = Map.of(
            "com/acme/dates/Dates.java", """
                    package com.acme.dates;

                    import java.math.BigDecimal;
                    import java.util.List;

                    /** Date arithmetic on YYYYMMDD numbers. */
                    public final class Dates {
                        private Dates() {}

                        /**
                         * Formats a date given as a number {@code YYYYMMDD} with a pattern.
                         *
                         * <p>Second paragraph.
                         */
                        public static String format(int yyyymmdd, String pattern) { return ""; }

                        /** The days from one date to another. */
                        public static long daysBetween(int from, int to) { return 0; }

                        public static BigDecimal rate(BigDecimal amount) { return amount; }

                        public static List<String> names() { return List.of(); }

                        static int hidden() { return 0; }

                        public static <T> T generic(T t) { return t; }
                    }
                    """,
            "com/acme/dates/SimpleDate.java", """
                    package com.acme.dates;

                    /** A calendar date. */
                    public record SimpleDate(int year, int month, int day) {
                        /** Parses YYYYMMDD. */
                        public static SimpleDate ofNumber(int yyyymmdd) { return null; }

                        /** The date some days later. */
                        public SimpleDate plusDays(long days) { return this; }

                        /** This date as YYYYMMDD. */
                        public int toNumber() { return 0; }

                        public Format.Style style() { return null; }
                    }
                    """,
            "com/acme/dates/Format.java", """
                    package com.acme.dates;

                    /** Formats dates. */
                    public class Format {
                        public enum Style { SHORT, LONG }

                        /** A formatter for one pattern. */
                        public Format(String pattern) {}

                        public String apply(SimpleDate d) { return ""; }

                        private Format() {}
                    }
                    """,
            "com/acme/dates/internal/Helper.java", """
                    package com.acme.dates.internal;

                    public class Helper {
                        public static int help(String s) { return 0; }
                    }

                    class Hidden {
                        public static int no() { return 0; }
                    }
                    """,
            "module-info.java", "module acme.dates { exports com.acme.dates; }",
            "com/acme/dates/Broken.java", "package com.acme.dates; public class Broken {");

    @Test
    void readsConstructorsStaticAndInstanceMembersWithResolvedTypes() {
        JavaIndex index = JavaIndex.of(LIBRARY, List.of());
        List<String> ids = index.members().stream().map(JavaMember::id).toList();
        assertEquals(List.of(
                "Dates.format(int,String)",
                "Dates.daysBetween(int,int)", "Dates.rate(BigDecimal)", "Dates.names()",
                "Format.new(String)", "Format.apply(SimpleDate)",
                "SimpleDate.new(int,int,int)", "SimpleDate.year()", "SimpleDate.month()", "SimpleDate.day()",
                "SimpleDate.ofNumber(int)", "SimpleDate.plusDays(long)", "SimpleDate.toNumber()", "SimpleDate.style()",
                "Helper.new()", "Helper.help(String)"), ids);
        JavaMember format = index.of("com.acme.dates.Dates").getFirst();
        assertEquals(JavaMember.Kind.STATIC, format.kind());
        assertEquals("String", format.returnType());
        assertEquals("Formats a date given as a number YYYYMMDD with a pattern.", format.doc().summary());
        assertEquals("String Dates.format(int yyyymmdd, String pattern)", format.doc().signature());
        assertTrue(format.matches("com.acme.dates.Dates.format(int, String)") && format.matches("Dates.format(int,String)"));
        assertEquals("?List<String>", index.members().get(3).returnType());
        assertFalse(index.members().get(3).usable());

        JavaMember plusDays = index.members().stream().filter(m -> m.name().equals("plusDays")).findFirst().orElseThrow();
        assertEquals(JavaMember.Kind.INSTANCE, plusDays.kind());
        assertEquals("com.acme.dates.SimpleDate", plusDays.returnType());
        JavaMember style = index.members().stream().filter(m -> m.name().equals("style")).findFirst().orElseThrow();
        assertEquals("com.acme.dates.Format.Style", style.returnType());
        assertEquals("com.acme.dates.Format", index.of("com.acme.dates.Format").getFirst().importName());
        assertEquals("A formatter for one pattern.", index.of("com.acme.dates.Format").getFirst().doc().summary());
        assertEquals("A calendar date.", index.of("com.acme.dates.SimpleDate").getFirst().doc().summary());

        assertEquals(1, index.skipped().size());
        assertTrue(index.skipped().getFirst().startsWith("com/acme/dates/Broken.java"));
        assertEquals(5, index.classes()); // Dates, Format, Format.Style, SimpleDate, Helper
    }

    @Test
    void exposeLimitsTheTypesOffered() {
        JavaIndex index = JavaIndex.of(LIBRARY, List.of("com.acme.dates.*"));
        assertTrue(index.members().stream().noneMatch(m -> m.owner().contains("internal")));
        assertTrue(index.members().stream().anyMatch(m -> m.owner().equals("com.acme.dates.Format.Style") || m.owner().equals("com.acme.dates.Format")));
        JavaIndex one = JavaIndex.of(LIBRARY, List.of("com.acme.dates.Dates"));
        assertTrue(one.members().stream().allMatch(m -> m.owner().equals("com.acme.dates.Dates")));
    }

    @Test
    void sharedSimpleNamesAreQualified() {
        JavaIndex index = JavaIndex.of(Map.of(
                "a/Util.java", "package a; public class Util { public static int one() { return 1; } }",
                "b/Util.java", "package b; public class Util { public static int two() { return 2; } }"), List.of());
        assertEquals(List.of("a.Util.new()", "a.Util.one()", "b.Util.new()", "b.Util.two()"), index.members().stream().map(JavaMember::id).toList());
    }

    @Test
    void readsDirectoriesFilesAndSourcesJarsWithTheSameHash(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("src");
        Files.createDirectories(dir.resolve("p"));
        Files.writeString(dir.resolve("p/A.java"), "package p; public class A { public static int a() { return 1; } }");
        Path jar = tmp.resolve("a-sources.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("p/A.java"));
            zip.write(Files.readAllBytes(dir.resolve("p/A.java")));
            zip.closeEntry();
        }
        var fromDir = JavaIndex.of(JavaIndex.read(dir), List.of());
        var fromJar = JavaIndex.of(JavaIndex.read(jar), List.of());
        assertEquals(fromDir.hash(), fromJar.hash());
        assertEquals(List.of("A.new()", "A.a()"), fromJar.members().stream().map(JavaMember::id).toList());
        assertEquals(1, JavaIndex.read(dir.resolve("p/A.java")).size());
        Files.writeString(dir.resolve("p/A.java"), "package p; public class A { public static int a() { return 2; } }");
        assertNotEquals(fromDir.hash(), JavaIndex.of(JavaIndex.read(dir), List.of()).hash());
    }
}
