package me.itzg.helpers.sync;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;

@Command(name = "sync-and-interpolate",
        description = "Synchronizes the contents of one directory to another with conditional variable interpolation.")
@ToString
@Slf4j
public class SyncAndInterpolate implements Callable<Integer> {
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this usage and exit")
    @ToString.Exclude
    boolean showHelp;

    @Option(names = "--skip-newer-in-destination",
            // this is same as rsync's --update option
            description = "Skip any files that exist in the destination and have a newer modification time than the source.")
    boolean skipNewerInDestination;

    @ArgGroup(multiplicity = "1", exclusive = false)
    ReplaceEnvOptions replaceEnv = new ReplaceEnvOptions();

    @Parameters(index = "0", description = "source directory")
    Path src;

    @Parameters(index = "1", description = "destination directory")
    Path dest;

    @Override
    public Integer call() throws Exception {
        log.debug("Configured with {}", this);

        try {
            Files.walkFileTree(src, new InterpolatingFileVisitor(src, dest, skipNewerInDestination,
                    new Interpolator(new StandardEnvironmentVariablesProvider(), replaceEnv.prefix)));
        } catch (IOException e) {
            log.error("Failed to sync and interpolate {} into {} : {}", src, dest, e.getMessage());
            log.debug("Details", e);
            return 1;
        }

        return 0;
    }

    class InterpolatingFileVisitor extends SynchronizingFileVisitor {
        private final Interpolator interpolator;

        public InterpolatingFileVisitor(Path src, Path dest, boolean skipNewerInDestination, Interpolator interpolator) {
            super(src, dest, skipNewerInDestination);
            this.interpolator = interpolator;
        }

        @Override
        protected void processFile(Path srcFile, Path destFile) throws IOException {
            if (replaceEnv.matches(destFile)) {
                log.info("Interpolating {} -> {}", srcFile, destFile);

                final byte[] content = Files.readAllBytes(srcFile);

                final Interpolator.Result<byte[]> result;
                try {
                    result = interpolator.interpolate(content);
                } catch (IOException e) {
                    log.warn("Failed to interpolate {}, using copy instead: {}", srcFile, e.getMessage());
                    log.debug("Details", e);
                    copyFile(srcFile, destFile);
                    return;
                }
                if (result.getReplacementCount() > 0) {
                    log.debug("Replaced {} variable(s) in {}", result.getReplacementCount(), destFile);
                }
                try (OutputStream out = Files.newOutputStream(destFile, StandardOpenOption.CREATE)) {
                    out.write(result.getContent());
                }
                Files.setLastModifiedTime(destFile, Files.getLastModifiedTime(srcFile));

            } else {
                super.processFile(srcFile, destFile);
            }
        }
    }
}
