plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":azurlane-core"))
    implementation(project(":azurlane-infra"))

    implementation("io.ktor:ktor-server-core-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-netty-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-cors-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-status-pages-jvm:${property("ktorVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
}

kotlin {
    jvmToolchain(17)
}
