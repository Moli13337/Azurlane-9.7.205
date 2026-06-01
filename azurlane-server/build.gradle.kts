plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":azurlane-core"))
    implementation(project(":azurlane-proto"))
    implementation(project(":azurlane-data"))
    implementation(project(":azurlane-infra"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("coroutinesVersion")}")
    implementation("com.google.protobuf:protobuf-kotlin:${property("protobufVersion")}")
    implementation("com.google.protobuf:protobuf-java:${property("protobufVersion")}")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")
    implementation("org.jetbrains.exposed:exposed-core:${property("exposedVersion")}")
}

kotlin {
    jvmToolchain(17)
}
