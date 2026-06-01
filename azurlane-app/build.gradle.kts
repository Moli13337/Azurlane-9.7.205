plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":azurlane-core"))
    implementation(project(":azurlane-proto"))
    implementation(project(":azurlane-data"))
    implementation(project(":azurlane-infra"))
    implementation(project(":azurlane-server"))
    implementation(project(":azurlane-admin"))
    implementation(project(":azurlane-sdk"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("coroutinesVersion")}")
    implementation("org.jetbrains.exposed:exposed-core:${property("exposedVersion")}")
    implementation("ch.qos.logback:logback-classic:${property("logbackVersion")}")
    implementation("org.fusesource.jansi:jansi:2.4.1")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.yaml:snakeyaml:2.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.azurlane.app.ApplicationKt")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("server")
    archiveClassifier.set("")
    archiveVersion.set("")

    manifest {
        attributes(
            "Main-Class" to "com.azurlane.app.ApplicationKt",
            "Implementation-Title" to "AzurLaneServer",
            "Implementation-Version" to "1.0.0"
        )
    }

    // 合并 META-INF 文件以避免冲突
    mergeServiceFiles()

    // 排除重复签名文件
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}
