plugins {
  `java-library` apply false
  `maven-publish` apply false
}

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
    // Java 17 keeps the library usable on every Paper build from 1.17 upwards, including servers
    // that already run on a Java 21 runtime.
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
    withJavadocJar()
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
  }

  tasks.withType<Javadoc>().configureEach {
    val docletOptions = options as StandardJavadocDocletOptions
    docletOptions.encoding = "UTF-8"
    docletOptions.addStringOption("Xdoclint:all,-missing/private", "-quiet")
    docletOptions.links(
      "https://docs.oracle.com/en/java/javase/17/docs/api/",
      "https://jd.papermc.io/paper/1.21.4/",
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
