plugins {
    id("com.android.application")
}

android {
    namespace = "be.mygod.datasimtile"
    compileSdk = 37

    defaultConfig {
        applicationId = "be.mygod.datasimtile"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "1.2.0"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += "kotlin/**"
        }
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.10.0")
    compileOnly("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    testImplementation("junit:junit:4.13.2")
}
