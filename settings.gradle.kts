rootProject.name = "AzurLaneServer"

pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
        mavenCentral()
    }
}

include("proto")
include("core")
include("data")
include("infra")
include("server")
include("admin")
include("sdk")
include("app")
include("test")
