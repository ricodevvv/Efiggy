allprojects {
  group = rootProject.property("group") as String
  version = rootProject.property("version") as String
}

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "maven-publish")

  // Accessors such as `java { }` and `publishing { }` are not generated for plugins applied inside
  // this block, so the extensions are configured explicitly.
  configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-options"))
  }

  val modernApiClasspath = configurations.create("modernApiClasspath") {
    isCanBeConsumed = false
    extendsFrom(configurations["api"], configurations["implementation"], configurations["compileOnly"])
    exclude(group = "org.spigotmc")
    attributes {
      attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
      attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
      attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
      attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
  }

  dependencies {
    modernApiClasspath(rootProject.libs.paper)
  }

  val mainSources = the<SourceSetContainer>()["main"]
  val toolchains = the<JavaToolchainService>()
  val verifyModernApi = tasks.register<JavaCompile>("verifyModernApi") {
    description = "Compiles the main sources against the newest Paper API to catch calls it no longer has."
    group = "verification"
    source = mainSources.java
    classpath = modernApiClasspath
    destinationDirectory.set(layout.buildDirectory.dir("verify/modern-api"))
    javaCompiler.set(toolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    options.release.set(21)
    options.compilerArgs = mutableListOf("-Xlint:none", "-nowarn")
  }

  tasks.named("check") {
    dependsOn(verifyModernApi)
  }

  tasks.withType<Javadoc>().configureEach {
    val docletOptions = options as StandardJavadocDocletOptions
    docletOptions.encoding = "UTF-8"
    docletOptions.addStringOption("Xdoclint:all", "-quiet")
    docletOptions.links(
      "https://docs.oracle.com/en/java/javase/17/docs/api/",
      "https://jd.papermc.io/paper/1.21.11/",
    )
    // Undocumented public API is a build failure, not a warning.
    docletOptions.addBooleanOption("Werror", true)
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
  }

  configure<PublishingExtension> {
    publications.create<MavenPublication>("maven") {
      from(components["java"])
      pom {
        name.set(project.name)
        description.set(provider { project.description ?: "Part of the Effigy NPC library." })
        url.set("https://github.com/ricodevvv/Efiggy")
        licenses {
          license {
            name.set("MIT License")
            url.set("https://opensource.org/licenses/MIT")
          }
        }
        developers {
          developer {
            id.set("ricodevvv")
          }
        }
        scm {
          url.set("https://github.com/ricodevvv/Efiggy")
          connection.set("scm:git:https://github.com/ricodevvv/Efiggy.git")
        }
      }
    }
  }
}
