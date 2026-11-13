plugins {
    id("com.android.application") version "8.9.0"
    id("org.jetbrains.kotlin.android") version "2.1.0"
}

android {
    namespace = "org.jjazzlab.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.jjazzlab.android"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.apache.commons:commons-compress:1.26.0")
}

tasks.register("copyJJazzLabToAssets", Copy::class) {
    from("../app/application/target/jjazzlab")
    into("src/main/assets/jjazzlab")
}

tasks.named("preBuild").configure {
    dependsOn("copyJJazzLabToAssets")
}

