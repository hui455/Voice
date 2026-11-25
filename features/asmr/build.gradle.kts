plugins {
  id("voice.feature")
}

dependencies {
  implementation(projects.core.playback.api)
  implementation(libs.lifecycle.compose)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.datasource.okhttp)
  implementation(libs.navigation3.ui)
  implementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
}
