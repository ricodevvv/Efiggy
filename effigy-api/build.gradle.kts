description = "Public API of the Effigy NPC library: interfaces, value types and events."

dependencies {
  compileOnly(libs.spigot)
  compileOnly(libs.annotations)

  testCompileOnly(libs.annotations)
  testImplementation(libs.spigot)
  testImplementation(platform(libs.junitBom))
  testImplementation(libs.junitJupiter)
  testRuntimeOnly(libs.junitPlatformLauncher)
}
