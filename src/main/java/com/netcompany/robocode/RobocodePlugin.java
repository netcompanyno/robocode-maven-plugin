package com.netcompany.robocode;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;

import java.io.*;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * @author Knut Esten Melandsø Nekså
 * <p>
 * Hva den gjør:
 * 1. Spør om author og description
 * 2. Pakker til "Robocode-jar-format"
 * <p>
 * Begrensning:
 * 1. Roboten må ligge i pakken com.netcompany.robocode.
 * 2. Man kan kun ha én klasse i prosjektet.
 */
@Mojo(name = "package")
public class RobocodePlugin extends AbstractMojo {
    @Component
    private MavenProject project;

    @Component
    private Prompter prompter;

    @Parameter(property = "project.build.directory", readonly = true)
    private String outputDirectory;

    public void execute() throws MojoExecutionException {
        try {
            final String author = prompter.prompt("Enter name of author");
            final String description = prompter.prompt("Enter description");

            final Path classesDir = Paths.get(outputDirectory, "classes");

            final List<Path> classFiles = Files.walk(classesDir)
                    .filter(path -> path.toFile().isFile())
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .collect(Collectors.toList());

            if (classFiles.size() != 1) {
                throw new MojoExecutionException("Only one class is allowed, you have multiple: "
                        + classFiles.stream().map(path -> path.toFile().getName()).collect(Collectors.joining(", ")));
            }

            final Path robotClass = classFiles.get(0);

            final Path correctPackage = Paths.get("com", "netcompany", "robocode");
            if (!robotClass.getParent().endsWith(correctPackage)) {
                throw new MojoExecutionException("Robots should be placed in the following package: com.netcompany.robocode");
            }

            final Path propertyFile = createRobocodePropertyFile(robotClass, author, description);

            final Path manifestFile = createManifestFile(robotClass);

            final String className = classNameForPath(robotClass);
            final String jarName = className + "-filen_du_skal_legge_i_google_drive-" + Math.round(Math.random() * 10000) + ".jar";

            final Path zipfile = Paths.get(outputDirectory + "/" + jarName);
            final URI uri = URI.create("jar:file:" + zipfile.toAbsolutePath());
            final Map<String, String> env = new HashMap<>();
            env.put("create", "true");

            try (final FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
                Files.createDirectories(zipfs.getPath("com", "netcompany", "robocode"));
                Files.createDirectories(zipfs.getPath("META-INF"));
                Files.copy(manifestFile, zipfs.getPath("META-INF", "MANIFEST.mf"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(propertyFile, zipfs.getPath("com", "netcompany", "robocode", propertyFile.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(robotClass, zipfs.getPath("com", "netcompany", "robocode", robotClass.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | PrompterException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Something wrong happened: " + e.getMessage());
        }
    }

    private Path createManifestFile(final Path robotClass) throws IOException {
        final String className = classNameForPath(robotClass);
        final String robots = "robots: com.netcompany.robocode." + className;
        final File tempFile = File.createTempFile("MANIFEST", ".mf");


        try (final PrintWriter printWriter = new PrintWriter(tempFile)) {
            printWriter.println("Manifest-Version: 1.0");
            printWriter.println(robots);
        }

        return tempFile.toPath();
    }

    private String classNameForPath(final Path path) {
        return path.getFileName().toString().substring(0, path.getFileName().toString().length() - 6);
    }

    private Path createRobocodePropertyFile(final Path robotClass, final String author, final String description) throws IOException {
        final Properties robocodeProperties = new Properties();
        final String className = classNameForPath(robotClass);
        robocodeProperties.put("robot.classname", "com.netcompany.robocode." + className);
        robocodeProperties.put("robot.author", author);
        robocodeProperties.put("robocode.version", "1.9.2.1");
        robocodeProperties.put("robot.description", description);


        final String propertyFilePath = robotClass.getParent().resolve(className + ".properties").toString();
        final OutputStream outputStream = new FileOutputStream(propertyFilePath);
        robocodeProperties.store(outputStream, "");

        return Paths.get(propertyFilePath);
    }
}
