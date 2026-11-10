pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    /**
     * 本机构建机是离线的（只靠 ~/.gradle 缓存），缓存里没有插件 marker 包
     * （com.android.application:...gradle.plugin 之类的空壳），但插件本体的
     * Java 依赖都在。这里直接把插件 id 映射到本体坐标，避免去网上找 marker。
     */
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application" ->
                    useModule("com.android.tools.build:gradle:${requested.version}")
                "org.jetbrains.kotlin.android" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "StudyClock"
include(":app")
