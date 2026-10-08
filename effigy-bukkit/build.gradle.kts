description = "Paper and Spigot implementation of the Effigy NPC library, backed by PacketEvents."

dependencies {
  api(project(":effigy-api"))
  compileOnly(libs.spigot)
  compileOnly(libs.packetevents)
  compileOnly(libs.annotations)

  // Hologram text is a component on the wire. Both libraries are already on the server classpath,
  // shipped by PacketEvents and, on Paper, by the server itself.
  compileOnly(libs.adventureApi)
  compileOnly(libs.adventureLegacy)

  testCompileOnly(libs.annotations)
  testImplementation(libs.spigot)
  testImplementation(libs.packetevents)
  testImplementation(platform(libs.junitBom))
  testImplementation(libs.junitJupiter)
  testRuntimeOnly(libs.junitPlatformLauncher)
}
