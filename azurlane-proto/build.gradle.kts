plugins {
    kotlin("jvm")
    id("com.google.protobuf")
}

dependencies {
    api("com.google.protobuf:protobuf-java:${property("protobufVersion")}")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${property("protobufVersion")}"
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
