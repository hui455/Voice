plugins {
  id("voice.library")
}

dependencies {
  implementation(projects.core.data.api)
  implementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
}
