package transpiler.cobol2java.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Checks that the project the output goes into declares a library's dependency. Build files are
 * read as data, never run: a Maven {@code pom.xml}'s direct dependencies with literal versions,
 * and Gradle's {@code gradle.lockfile} (the project's and its subprojects', one level down;
 * {@code build.gradle.kts} is Kotlin code and is not read). A declaration the check cannot
 * confirm (a version from a property, a parent POM or a BOM; no lockfile) is reported as
 * unconfirmed, never as missing, and never fails the run.
 */
public final class TargetProject {
    /** {@code declared}: true, false, or null when the build files cannot confirm it; {@code note} says why. */
    public record Check(Boolean declared, String note) {}

    private TargetProject() {}

    public static Check check(Path project, String coordinates) {
        if (coordinates == null) {
            return new Check(null, "a library read from a source path has no coordinates to look for");
        }
        String[] gav = coordinates.split(":");
        Path pom = project.resolve("pom.xml");
        Check maven = Files.isRegularFile(pom) ? maven(pom, gav[0], gav[1], gav[2]) : null;
        Check gradle = gradle(project, coordinates);
        for (Check c : java.util.Arrays.asList(maven, gradle)) {
            if (c != null && Boolean.TRUE.equals(c.declared())) {
                return c;
            }
        }
        // Neither confirms it: an unconfirmed verdict outranks "not declared" (the check could have missed it).
        for (Check c : java.util.Arrays.asList(maven, gradle)) {
            if (c != null && c.declared() == null) {
                return c;
            }
        }
        if (maven != null || gradle != null) {
            return new Check(false, String.join("; ", Stream.of(maven, gradle).filter(c -> c != null).map(Check::note).toList()));
        }
        return new Check(null, "no pom.xml or gradle.lockfile in " + project);
    }

    private static Check maven(Path pom, String group, String artifact, String version) {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            doc = f.newDocumentBuilder().parse(pom.toFile());
        } catch (Exception e) {
            return new Check(null, "pom.xml could not be read: " + e.getMessage());
        }
        Element root = doc.getDocumentElement();
        boolean inherited = child(root, "parent") != null;
        Element dependencies = child(root, "dependencies");
        if (dependencies != null) {
            NodeList list = dependencies.getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                if (list.item(i) instanceof Element d && d.getTagName().equals("dependency")
                        && group.equals(text(d, "groupId")) && artifact.equals(text(d, "artifactId"))) {
                    String v = text(d, "version");
                    if (v == null || v.contains("${")) {
                        return new Check(null, "pom.xml declares " + group + ":" + artifact + " with "
                                + (v == null ? "no version (managed by a parent or BOM)" : "the version " + v) + ", which cannot be confirmed as " + version);
                    }
                    return v.equals(version) ? new Check(true, "declared in pom.xml")
                            : new Check(false, "pom.xml declares " + group + ":" + artifact + ":" + v + ", not " + version);
                }
            }
        }
        return inherited ? new Check(null, "not a direct dependency in pom.xml; it may come from the parent POM")
                : new Check(false, "not a dependency in pom.xml");
    }

    /** The lockfiles' verdict; null when the project is not a Gradle build. */
    private static Check gradle(Path project, String coordinates) {
        List<Path> lockfiles = new ArrayList<>();
        if (Files.isRegularFile(project.resolve("gradle.lockfile"))) {
            lockfiles.add(project.resolve("gradle.lockfile"));
        }
        try (Stream<Path> dirs = Files.list(project)) {
            dirs.filter(Files::isDirectory).sorted().map(d -> d.resolve("gradle.lockfile")).filter(Files::isRegularFile).forEach(lockfiles::add);
        } catch (IOException e) {
            // An unreadable project directory: no lockfile verdict.
        }
        if (lockfiles.isEmpty()) {
            boolean gradleBuild = Files.exists(project.resolve("build.gradle")) || Files.exists(project.resolve("build.gradle.kts"));
            return gradleBuild
                    ? new Check(null, "a Gradle build without gradle.lockfile (run `gradle dependencies --write-locks` to make it checkable)")
                    : null;
        }
        for (Path lock : lockfiles) {
            try {
                for (String line : Files.readAllLines(lock)) {
                    if (line.startsWith(coordinates + "=")) {
                        return new Check(true, "locked in " + project.relativize(lock));
                    }
                }
            } catch (IOException e) {
                return new Check(null, lock + " could not be read");
            }
        }
        return new Check(false, "not in " + String.join(", ", lockfiles.stream().map(l -> project.relativize(l).toString()).toList()));
    }

    private static Element child(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                return e;
            }
        }
        return null;
    }

    private static String text(Element parent, String tag) {
        Element e = child(parent, tag);
        return e == null ? null : e.getTextContent().strip();
    }
}
