# jfx-sss-fxml

This Maven Archetype is a JavaFX template for a non-modular FXML project.
https://central.sonatype.com/artifact/io.github.sosuisen/jfx-sss-fxml

Designed for Java beginners, this archetype simplifies the development process by excluding the Java module system.

To use it, run `mvn archetype:generate` and search for **jfx-sss-fxml**.


# Build this Maven archetype project

You can build this archetype project and install it to your local Maven repository.

```
java -Dgpg=false MavenArchetypeRunner.java 
cd project/target/generated-sources/archetype
mvn install
```

## Generate a new project from the local archetype
```
mkdir /tmp/project
cd /tmp/project
mvn archetype:generate -DarchetypeCatalog=local
```

## Run the project
```
cd /tmp/project/myapp
mvn javafx:run
```
