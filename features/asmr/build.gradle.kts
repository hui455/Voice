plugins {
  id("voice.feature")
}

dependencies {
  implementation(projects.core.asmr)
  implementation(libs.navigation3.ui)
  implementation(libs.okhttp)
}
