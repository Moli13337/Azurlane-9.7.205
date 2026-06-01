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

include("azurlane-proto")
include("azurlane-core")
include("azurlane-data")
include("azurlane-infra")
include("azurlane-server")
include("azurlane-admin")
include("azurlane-sdk")
include("azurlane-app")
include("azurlane-test")
