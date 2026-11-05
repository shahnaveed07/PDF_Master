// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

tasks.register("detekt") {
  group = "verification"
  description = "Runs Detekt static code analysis across modules"
  dependsOn(":app:detekt")
}

tasks.register("ktlintCheck") {
  group = "verification"
  description = "Runs Kotlin code style verification across modules"
  dependsOn(":app:ktlintCheck")
}

