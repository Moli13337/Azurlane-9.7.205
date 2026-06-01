plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":azurlane-core"))
    implementation(project(":azurlane-data"))

    implementation("org.jetbrains.exposed:exposed-core:${property("exposedVersion")}")
    implementation("org.jetbrains.exposed:exposed-dao:${property("exposedVersion")}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${property("exposedVersion")}")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:${property("exposedVersion")}")
    implementation("org.xerial:sqlite-jdbc:${property("sqliteJdbcVersion")}")
    implementation("com.zaxxer:HikariCP:${property("hikariVersion")}")

    implementation("io.netty:netty-all:${property("nettyVersion")}")
    implementation("com.google.protobuf:protobuf-kotlin:${property("protobufVersion")}")
    implementation("com.google.protobuf:protobuf-java:${property("protobufVersion")}")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("coroutinesVersion")}")
    implementation("ch.qos.logback:logback-classic:${property("logbackVersion")}")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")
    implementation("com.typesafe:config:${property("typesafeConfigVersion")}")
    implementation("de.mkammerer:argon2-jvm:${property("argon2Version")}")
    implementation("org.yaml:snakeyaml:2.2")
}

kotlin {
    jvmToolchain(17)
}
