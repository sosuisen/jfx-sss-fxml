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
            </plugins>
                """;

    public static void main(String[] args) {
        // カレントディレクトリの "project" フォルダを対象にする
        File projectDir = new File(System.getProperty("user.dir"), "project");
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            System.err.println("project フォルダが存在しません: " + projectDir.getAbsolutePath());
            System.exit(1);
        }

        // Maven コマンドと引数をリストに格納
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");

        // まず mvn clean を実行
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

        // アーキタイプ生成コマンドを準備
        List<String> command = new ArrayList<>();
        command.add(isWindows ? "mvn.cmd" : "mvn");
        command.add("archetype:create-from-project");
        command.add("-Darchetype.properties=../archetype.properties");

        // ProcessBuilder を作成し、作業ディレクトリを project フォルダに設定
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectDir);
        pb.redirectErrorStream(true); // 標準出力と標準エラーを統合

        try {
            Process process = pb.start();
            // プロセスの出力を読み込む
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // プロセス終了コードを待つ
            int exitCode = process.waitFor();
            System.out.println(command.toString() + " has been done. Exit code: " + exitCode);

            // アーキタイププロジェクトの生成結果を修正
            // project/target/generated-sources/archetype/pom.xml
            // アーキタイプ名の末尾に -archetype と付くのを削除
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
                content = content.replaceAll("</pluginManagement>", "</pluginManagement>\n" + PUBLISH_PLUGIN);
                Files.writeString(archetypePomFile.toPath(), content);
                System.out.println("Replaced archetype pom.xml");
            }

            // project/target/generated-sources/archetype/main/archetype-resources/pom.xml
            // の内容を置換
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
                content = content.replaceAll("<main\\.class>.+\\.App</main\\.class>",
                        "<main.class>\\${package}.App</main.class>");
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
             * project/target/generated-sources/archetype/src/main/resources/archetype-
             * resources/src/main/resources/以下にある
             * すべてのfxmlファイルの内容を置換
             */
            Path fxmlStartDir = projectDir.toPath()
                    .resolve(
                            "target/generated-sources/archetype/src/main/resources/archetype-resources/src/main/resources");
            processResourceFiles(fxmlStartDir);

            // project\target\generated-sources\archetype\src\main\resources\META-INF\maven\archetype-metadata.xml
            // の内容を置換
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

            // project\target\generated-sources\archetype\target\classes\archetype-resources\src\main\resources
            // この場所にも.fxmlファイルが残っているので、再帰的に削除
            Path targetClassesDir = projectDir.toPath()
                    .resolve(
                            "target/generated-sources/archetype/target/classes/archetype-resources/src/main/resources");
            if (Files.exists(targetClassesDir)) {
                try (Stream<Path> paths = Files.walk(targetClassesDir)) {
                    paths.sorted((a, b) -> b.compareTo(a)) // 逆順にソート（子から親の順）
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                    System.out.println("Deleted: " + path);
                                } catch (IOException e) {
                                    System.err.println("ファイルの削除中にエラーが発生しました: " + path);
                                }
                            });
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
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

                            // 同じ場所に書き戻す
                            Files.writeString(path, content);
                            System.out.println("Replaced " + path.getFileName());
                        } catch (IOException e) {
                            throw new RuntimeException("FXMLファイルの処理中にエラーが発生しました: " + path, e);
                        }
                    });
        }

        // すべての置換が終わった後でファイルを移動
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            // 新しい保存先のパスを作成
                            Path newPath = directory.resolve(path.getFileName());
                            // 移動先と同じ名前の場合はスキップ
                            if (newPath.equals(path)) {
                                return;
                            }
                            // ファイルが既にある場合は削除
                            if (Files.exists(newPath)) {
                                Files.delete(newPath);
                            }
                            // ファイルを新しい場所に移動
                            Files.move(path, newPath);
                            System.out.println("Moved " + path.getFileName());
                        } catch (IOException e) {
                            throw new RuntimeException("ファイルの移動中にエラーが発生しました: " + path, e);
                        }
                    });
        }

        // 空のディレクトリを削除
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
                            System.err.println("空ディレクトリの削除中にエラーが発生しました: " + path);
                        }
                    });
        }
    }
}
