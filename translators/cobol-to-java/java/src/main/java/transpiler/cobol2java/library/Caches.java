package transpiler.cobol2java.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds an installed dependency's sources jar in the local caches. Nothing is downloaded: the
 * dependency is already installed in the target project, and Maven and Gradle keep installed jars
 * in a shared cache rather than in the project.
 *
 * <ul>
 *   <li>Maven: {@code <repository>/<group as dirs>/<artifact>/<version>/<artifact>-<version>-sources.jar}</li>
 *   <li>Gradle: {@code <files-2.1>/<group>/<artifact>/<version>/<hash>/<artifact>-<version>-sources.jar}, found by
 *       scanning the hash folders (sorted, so the result is deterministic)</li>
 * </ul>
 *
 * @param maven the Maven local repository (null: not searched)
 * @param gradle Gradle's {@code caches/modules-2/files-2.1} (null: not searched)
 */
public record Caches(Path maven, Path gradle) {
    /** Why a coordinate could not be used: the message names the cache paths searched and how to fetch the sources jar. */
    public static final class Unavailable extends Exception {
        public Unavailable(String message) {
            super(message);
        }
    }

    /** The sources jar of {@code group:artifact:version}. */
    public Path sourcesJar(String coordinates) throws Unavailable {
        String[] gav = coordinates.split(":");
        if (gav.length != 3 || Stream.of(gav).anyMatch(String::isBlank)) {
            throw new Unavailable("Maven coordinates must be 'group:artifact:version', not '" + coordinates + "'");
        }
        String group = gav[0], artifact = gav[1], version = gav[2];
        String sources = artifact + "-" + version + "-sources.jar";
        String jar = artifact + "-" + version + ".jar";
        boolean binaryOnly = false;
        if (maven != null) {
            Path dir = maven.resolve(group.replace('.', '/')).resolve(artifact).resolve(version);
            if (Files.isRegularFile(dir.resolve(sources))) {
                return dir.resolve(sources);
            }
            binaryOnly |= Files.isRegularFile(dir.resolve(jar));
        }
        if (gradle != null) {
            Path dir = gradle.resolve(group).resolve(artifact).resolve(version);
            Optional<Path> found = inHashFolders(dir, sources);
            if (found.isPresent()) {
                return found.get();
            }
            binaryOnly |= inHashFolders(dir, jar).isPresent();
        }
        String searched = String.join(" and ", Stream.of(maven, gradle).filter(p -> p != null).map(Path::toString).toList());
        String fetch = "fetch it in the target project with `mvn dependency:sources` (Maven) or "
                + "`idea { module { downloadSources = true } }` (Gradle), then translate again";
        throw new Unavailable(binaryOnly
                ? coordinates + " is installed without its sources jar (" + sources + "), which the library index reads; " + fetch
                : coordinates + " is not installed in the local caches (" + searched + "); it must be a dependency of the target "
                        + "project, with its sources jar; " + fetch);
    }

    private static Optional<Path> inHashFolders(Path dir, String file) {
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> hashes = Files.list(dir)) {
            List<Path> sorted = hashes.filter(Files::isDirectory).sorted().toList();
            return sorted.stream().map(h -> h.resolve(file)).filter(Files::isRegularFile).findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
