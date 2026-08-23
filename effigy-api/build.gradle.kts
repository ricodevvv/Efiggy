description = "Public API of the Effigy NPC library: interfaces, value types and events."

dependencies {
  compileOnly(libs.paper)
  compileOnly(libs.annotations)

  testCompileOnly(libs.annotations)
  testImplementation(libs.paper)
  testImplementation(platform(libs.junitBom))
  testImplementation(libs.junitJupiter)
  testRuntimeOnly(libs.junitPlatformLauncher)
}
