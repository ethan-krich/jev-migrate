package transpiler.cobol2java.worker;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Entry point of the persistent translator process.
 *
 * <p>stdout carries protocol messages only. {@link System#out} is redirected to stderr before
 * any library code runs so stray prints can never corrupt the protocol stream.
 */
public final class WorkerMain {
    private WorkerMain() {}

    public static void main(String[] args) throws IOException {
        OutputStream protocolOut = new FileOutputStream(FileDescriptor.out);
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

        var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        var channel = new MessageChannel(protocolOut);
        int exit = new Worker(channel).run(reader);
        System.exit(exit);
    }
}
