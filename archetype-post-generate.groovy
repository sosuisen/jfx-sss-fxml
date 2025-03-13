import java.nio.file.*

def basedir = "${request.outputDirectory}/${request.artifactId}"
println "Rename .gitignore.txt -> .gitignore. Basedir: ${basedir}"
def gitignore = Paths.get("${basedir}/.gitignore.txt")
if (Files.exists(gitignore)) {
    Files.move(gitignore, Paths.get("${basedir}/.gitignore"))
}
