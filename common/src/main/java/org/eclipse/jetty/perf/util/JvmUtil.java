package org.eclipse.jetty.perf.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JvmUtil
{
    public static Path findCurrentJavaExecutable()
    {
        String javaHome = System.getProperty("java.home");
        return findJavaExecutable(javaHome);
    }

    public static Path findJavaExecutable(String javaHome)
    {
        Path javaHomePath = Paths.get(javaHome);
        Path javaExec = javaHomePath.resolve("bin").resolve("java"); // *nix
        if (!Files.isExecutable(javaExec))
            javaExec = javaHomePath.resolve("Contents").resolve("Home").resolve("bin").resolve("java"); // OSX
        if (!Files.isExecutable(javaExec))
            javaExec = javaHomePath.resolve("bin").resolve("java.exe"); // Windows
        if (!Files.isExecutable(javaExec))
            return null;
        return javaExec;
    }
}
