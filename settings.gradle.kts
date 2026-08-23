// `pluginManagement` has to be the first block of a settings script.
pluginManagement {
  repositories {
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    maven("https://repo.codemc.io/repository/maven-releases/") { name = "codemc" }
  }
}

rootProject.name = "effigy"

include(":effigy-api", ":effigy-bukkit")
