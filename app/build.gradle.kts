plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dsh.studyclock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dsh.studyclock"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.5"

        // 内置云端备份端点（来自 gradle.properties，不进源码；缺省则 App 里显示"未配置"）
        val syncUrl = (project.findProperty("studysync.url") as String?) ?: ""
        val syncToken = (project.findProperty("studysync.token") as String?) ?: ""
        buildConfigField("String", "SYNC_URL", "\"$syncUrl\"")
        buildConfigField("String", "SYNC_TOKEN", "\"$syncToken\"")

        // 应用内更新：latest.json 地址（可用 -Papp.update.url=... 覆盖）
        val updateUrl = (project.findProperty("app.update.url") as String?)
            ?: "https://eregion.cn/studyclock/app/latest.json"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateUrl\"")
    }

    signingConfigs {
        create("personal") {
            storeFile = rootProject.file("keystore/studyclock.jks")
            storePassword = "studyclock"
            keyAlias = "studyclock"
            keyPassword = "studyclock"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-graphics:1.6.8")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-core:1.6.8")
}
