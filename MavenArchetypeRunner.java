import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class MavenArchetypeRunner {
    static final String GITHUB_ACCOUNT = "sosuisen";
    static final String LICENSES = """
            <licenses>
              <license>
                <name>The Apache License, Version 2.0</name>
                <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
              </license>
            </licenses>""";
    static final String DEVELOPERS = """
            <developers>
               <developer>
                  <name>Hidekazu Kubota</name>
                  <email>hidekazu.kubota@gmail.com</email>
                  <organization>Sosuisha</organization>
                  <organizationUrl>https://sosuisha.com</organizationUrl>
               </developer>
            </developers>""";
    static final String PUBLISH_PLUGIN = """
            <plugins>
                <plugin>
                    <groupId>org.sonatype.central</groupId>
                    <artifactId>central-publishing-maven-plugin</artifactId>
                    <version>0.7.0</version>
                    <extensions>true</extensions>
                    <configuration>
                        <publishingServerId>central</publishingServerId>
                    </configuration>
                </plugin>
                <plugin>
                    <artifactId>maven-source-plugin</artifactId>
                    <version>3.3.0</version>
                    <executions>
                        <execution>
                            <id>attach-sources</id>
                            <phase>package</phase>
                            <goals><goal>jar</goal></goals>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <artifactId>maven-javadoc-plugin</artifactId>
                 <version>3.3.0</version>
                 <configuration>
                        <additionalOptions>
                         <!-- skip strict check java doc-->
                            <additionalOption>-Xdoclint:none</additionalOption>
                        </additionalOptions>
                    </configuration>
                 <executions>
                  <execution>
                      <id>attach-javadocs</id>
                      <phase>package</phase>
                      <goals><goal>jar</goal></goals>
                  </execution>
                 </executions>
                </plugin>
                %s
            </plugins>
                """;
    static final String GPG_PLUGIN = """
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-gpg-plugin</artifactId>
                <version>1.6</version>
                <executions>
                    <execution>
                        <id>sign-artifacts</id>
                        <phase>verify</phase>
                        <goals>
                            <goal>sign</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            """;

    public static void main(String[] args) {
        File projectDir = new File(System.getProperty("user.dir"), "project");
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            System.err.println("project directory does not exist: " + projectDir.getAbsolutePath());
            System.exit(1);
        }

        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");

        // Run "mvn clean" first.
        List<String> cleanCommand = new ArrayList<>();
        cleanCommand.add(isWindows ? "mvn.cmd" : "mvn");
        cleanCommand.add("clean");

        ProcessBuilder cleanPb = new ProcessBuilder(cleanCommand);
        cleanPb.directory(projectDir);
        cleanPb.redirectErrorStream(true);

        try {
            Process cleanProcess = cleanPb.start();
            BufferedReader cleanReader = new BufferedReader(new InputStreamReader(cleanProcess.getInputStream()));
            String line;
            while ((line = cleanReader.readLine()) != null) {
                System.out.println(line);
            }

            int cleanExitCode = cleanProcess.waitFor();
            System.out.println(cleanCommand.toString() + " has been done. Exit code: " + cleanExitCode);

            if (cleanExitCode != 0) {
                System.err.println("mvn clean failed with exit code: " + cleanExitCode);
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("mvn clean failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // Prepare the command for archetype generation.
        List<String> command = new ArrayList<>();
        command.add(isWindows ? "mvn.cmd" : "mvn");
        command.add("archetype:create-from-project");
        command.add("-Darchetype.properties=../archetype.properties");

        // Create the ProcessBuilder and set the working directory to the project
        // folder.
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectDir);
        pb.redirectErrorStream(true); // Merge standard output and standard error.

        try {
            Process process = pb.start();
            // Read the output of the process.
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // Wait for the process to finish.
            int exitCode = process.waitFor();
            System.out.println(command.toString() + " has been done. Exit code: " + exitCode);

            // Modify the result of archetype project generation.
            // project/target/generated-sources/archetype/pom.xml
            // Remove "-archetype" from the end of the archetype name.
            File archetypePomFile = new File(projectDir,
                    "target/generated-sources/archetype/pom.xml");
            String artifactId = null;
            if (archetypePomFile.exists()) {
                String content = Files.readString(archetypePomFile.toPath());
                Pattern pattern = Pattern.compile("<artifactId>([^<]+)-archetype</artifactId>");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    artifactId = matcher.group(1);
                    content = content.replaceAll("<artifactId>([^<]+)-archetype</artifactId>",
                            "<artifactId>" + artifactId + "</artifactId>");
                }
                content = content.replaceAll("<name>([^<]+)-archetype</name>",
                        "<name>$1</name>");
                // (?s) means DOTALL mode
                content = content.replaceAll("(?s)<licenses>.+?</licenses>", LICENSES);
                content = content.replaceAll("(?s)<developers>.+?</developers>", DEVELOPERS);
                content = content.replaceAll("<scm />",
                        "<scm>\n<connection>scm:git:git://github.com/" + GITHUB_ACCOUNT + "/" + artifactId
                                + ".git</connection>\n<developerConnection>scm:git:ssh://github.com:" + GITHUB_ACCOUNT
                                + "/" + artifactId + ".git</developerConnection>\n<url>https://github.com/"
                                + GITHUB_ACCOUNT + "/" + artifactId + "/tree/main</url>\n</scm>");
                String gpgPlugin = "";
                if (System.getProperty("gpg") == null || !System.getProperty("gpg").equals("false")) {
                    gpgPlugin = GPG_PLUGIN;
                }
                String publishPluginContent = String.format(PUBLISH_PLUGIN, gpgPlugin);
                content = content.replaceAll("</extensions>", "</extensions>\n" + publishPluginContent);
                Files.writeString(archetypePomFile.toPath(), content);
                System.out.println("Replaced archetype pom.xml");
            }

            // Replace the content in
            // project/target/generated-sources/archetype/main/archetype-resources/pom.xml
            File pomFile = new File(projectDir,
                    "target/generated-sources/archetype/src/main/resources/archetype-resources/pom.xml");
            if (pomFile.exists()) {
                String content = Files.readString(pomFile.toPath());
                if (artifactId != null) {
                    content = content.replaceAll("<description>.+</description>",
                            "<description>Generated from " + artifactId + " archetype</description>");
                }
                content = content.replaceAll("<javafx\\.version>.+</javafx\\.version>",
                        "<javafx.version>\\${javaFxVersion}</javafx.version>");
                content = content.replaceAll("<maven\\.compiler\\.release>.+</maven\\.compiler\\.release>",
                        "<maven.compiler.release>\\${javaVersion}</maven.compiler.release>");
                content = content.replaceAll("<main\\.class>.+\\.Launcher</main\\.class>",
                        "<main.class>\\${package}.Launcher</main.class>");
                content = content.replaceAll("(?s)\s+?<url>.+?</url>\r\n", "");
                content = content.replaceAll("(?s)\s+?<licenses>.+?</licenses>\r\n", "");
                content = content.replaceAll("(?s)\s+?<developers>.+?</developers>\r\n", "");
                content = content.replaceAll("(?s)\s+?<scm />\r\n", "");

                Files.writeString(pomFile.toPath(), content);
                System.out.println("Replaced pom.xml");
            } else {
                System.out.println("pom.xml not found: " + pomFile.getAbsolutePath());
            }
            /*
             * Replace the content in all fxml files under
             * project/target/generated-sources/archetype/src/main/resources/archetype-
             * resources/src/main/resources/
             */
            Path fxmlStartDir = projectDir.toPath()
                    .resolve(
                            "target/generated-sources/archetype/src/main/resources/archetype-resources/src/main/resources");
            processResourceFiles(fxmlStartDir);

            // Replace the content in
            // project\target\generated-sources\archetype\src\main\resources\META-INF\maven\archetype-metadata.xml
            File archetypeMetadataFile = new File(projectDir,
                    "target/generated-sources/archetype/src/main/resources/META-INF/maven/archetype-metadata.xml");
            if (archetypeMetadataFile.exists()) {
                String content = Files.readString(archetypeMetadataFile.toPath());
                content = content.replaceAll(
                        "<fileSet encoding=\"UTF-8\">\\s*<directory>src/main/resources</directory>",
                        "<fileSet filtered=\"true\" packaged=\"true\" encoding=\"UTF-8\"><directory>src/main/resources</directory>");
                content = content.replaceAll(
                        "<fileSet encoding=\"UTF-8\">\\s*<directory>.vscode</directory>",
                        "<fileSet filtered=\"true\" encoding=\"UTF-8\"><directory>.vscode</directory>");
                Files.writeString(archetypeMetadataFile.toPath(), content);
                System.out.println("Replaced archetype-metadata.xml");
            }

            // There are still .fxml files in this location, so delete them recursively.
            // project\target\generated-sources\archetype\target\classes\archetype-resources\src\main\resources
            Path targetClassesDir = projectDir.toPath()
                    .resolve(
                            "target/generated-sources/archetype/target/classes/archetype-resources/src/main/resources");
            if (Files.exists(targetClassesDir)) {
                try (Stream<Path> paths = Files.walk(targetClassesDir)) {
                    paths.sorted((a, b) -> b.compareTo(a))
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                    System.out.println("Deleted: " + path);
                                } catch (IOException e) {
                                    System.err.println("Failed to delete: " + path);
                                }
                            });
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Copy archetype-post-generate.groovy to
        // target/generated-sources/archetype/src/main/resources/archetype-resources/META-INF
        Path groovySource = new File(System.getProperty("user.dir"), "archetype-post-generate.groovy").toPath();
        Path groovyDestination = projectDir.toPath().resolve(
                "target/generated-sources/archetype/src/main/resources/META-INF/archetype-post-generate.groovy");
        try {
            Files.copy(groovySource, groovyDestination);
            System.out.println("Copied archetype-post-generate.groovy to " + groovyDestination);
        } catch (IOException e) {
            System.err.println("Failed to copy archetype-post-generate.groovy: " + e.getMessage());
        }
    }

    static private void processResourceFiles(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> path.toString().endsWith(".fxml"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            content = content.replaceAll("xmlns=\"http://javafx.com/javafx/[^\"]+\"",
                                    "xmlns=\"http://javafx.com/javafx/\\${javaFxVersion}\"");
                            content = content.replaceAll("fx:controller=\"[^\"]+\\.([^\"]+)\"",
                                    "fx:controller=\"\\${package}.$1\"");

                            // Write back to the same location.
                            Files.writeString(path, content);
                            System.out.println("Replaced " + path.getFileName());
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to process FXML file: " + path, e);
                        }
                    });
        }

        // After all replacements, move the files.
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            // Create the new destination path.
                            Path newPath = directory.resolve(path.getFileName());
                            // Skip if the new path is the same as the original path.
                            if (newPath.equals(path)) {
                                return;
                            }
                            // Delete the file if it already exists.
                            if (Files.exists(newPath)) {
                                Files.delete(newPath);
                            }
                            // Move the file to the new location.
                            Files.move(path, newPath);
                            System.out.println("Moved " + path.getFileName());
                        } catch (IOException e) {
                            throw new RuntimeException("Error occurred while moving the file: " + path, e);
                        }
                    });
        }

        // Delete empty directories.
        try (Stream<Path> paths = Files.walk(directory, Integer.MAX_VALUE)) {
            paths.sorted((a, b) -> b.compareTo(a))
                    .filter(path -> {
                        try {
                            return Files.isDirectory(path) && Files.list(path).count() == 0;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            System.out.println("Deleted empty directory: " + path);
                        } catch (IOException e) {
                            System.err.println("Failed to delete empty directory: " + path);
                        }
                    });
        }
    }
}
