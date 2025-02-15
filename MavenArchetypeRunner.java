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

public class MavenArchetypeRunner {
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

            // 生成結果を修正
            // project/target/generated-sources/archetype/pom.xml
            // アーキタイプ名の末尾に -archetype と付くのを削除
            File archetypePomFile = new File(projectDir,
                    "target/generated-sources/archetype/pom.xml");
            if (archetypePomFile.exists()) {
                String content = Files.readString(archetypePomFile.toPath());
                content = content.replaceAll("<artifactId>([^<]+)-archetype</artifactId>",
                        "<artifactId>$1</artifactId>");
                content = content.replaceAll("<name>([^<]+)-archetype</name>",
                        "<name>$1</name>");
                Files.writeString(archetypePomFile.toPath(), content);
                System.out.println("Replaced archetype pom.xml");
            }

            // project/target/generated-sources/archetype/main/archetype-resources/pom.xml
            // の内容を置換
            File pomFile = new File(projectDir,
                    "target/generated-sources/archetype/src/main/resources/archetype-resources/pom.xml");
            if (pomFile.exists()) {
                String content = Files.readString(pomFile.toPath());
                content = content.replaceAll("<javafx\\.version>.+</javafx\\.version>",
                        "<javafx.version>\\${javaFxVersion}</javafx.version>");
                content = content.replaceAll("<maven\\.compiler\\.release>.+</maven\\.compiler\\.release>",
                        "<maven.compiler.release>\\${javaVersion}</maven.compiler.release>");
                content = content.replaceAll("<main\\.class>.+\\.App</main\\.class>",
                        "<main.class>\\${package}.App</main.class>");
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
            processFxmlFiles(fxmlStartDir);

            // project\target\generated-sources\archetype\src\main\resources\META-INF\maven\archetype-metadata.xml
            // の内容を置換
            File archetypeMetadataFile = new File(projectDir,
                    "target/generated-sources/archetype/src/main/resources/META-INF/maven/archetype-metadata.xml");
            if (archetypeMetadataFile.exists()) {
                String content = Files.readString(archetypeMetadataFile.toPath());
                content = content.replaceAll(
                        "(<fileSet encoding=\"UTF-8\">\\s*<directory>src/main/resources</directory>\\s*<includes>\\s*<include>\\*\\*/\\*\\.fxml</include>\\s*</includes>\\s*</fileSet>)",
                        "<fileSet filtered=\"true\" packaged=\"true\" encoding=\"UTF-8\"><directory>src/main/resources</directory><includes><include>**/*.fxml</include></includes></fileSet>");
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

    static private void processFxmlFiles(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> path.toString().endsWith(".fxml"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            content = content.replaceAll("xmlns=\"http://javafx.com/javafx/[^\"]+\"",
                                    "xmlns=\"http://javafx.com/javafx/\\${javaFxVersion}\"");
                            content = content.replaceAll("fx:controller=\"[^\"]+\\.([^\"]+)\"",
                                    "fx:controller=\"\\${package}.$1\"");

                            // 新しい保存先のパスを作成
                            Path newPath = directory.resolve(path.getFileName());

                            // ファイルを新しい場所に書き込み
                            Files.writeString(newPath, content);
                            // 元のファイルを削除
                            Files.delete(path);
                            System.out.println("Moved and replaced " + path.getFileName());

                        } catch (IOException e) {
                            throw new RuntimeException("FXMLファイルの処理中にエラーが発生しました: " + path, e);
                        }
                    });
        }

        // 空のディレクトリを削除
        try (Stream<Path> paths = Files.walk(directory, Integer.MAX_VALUE)) {
            paths.sorted((a, b) -> b.compareTo(a)) // 逆順にソート（子から親の順）
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
