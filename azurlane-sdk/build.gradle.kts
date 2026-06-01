plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":azurlane-infra"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    implementation("org.jetbrains.exposed:exposed-core:${property("exposedVersion")}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${property("exposedVersion")}")
    implementation("org.xerial:sqlite-jdbc:${property("sqliteJdbcVersion")}")
    implementation("com.zaxxer:HikariCP:${property("hikariVersion")}")
}

kotlin {
    jvmToolchain(17)
}
