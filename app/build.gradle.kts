import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cppmobilestudio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cppmobilestudio.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.3.0-alpha"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false // R8 ficará ativo numa etapa posterior, com regras testadas
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Assinatura: usa segredos de CI se existirem (docs/BUILD.md),
            // caso contrário assina com a chave debug para permitir instalação em testes.
            val ksPath = System.getenv("STUDIO_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && File(ksPath).exists()) {
                signingConfig = signingConfigs.create("ciRelease") {
                    storeFile = File(ksPath)
                    storePassword = System.getenv("STUDIO_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("STUDIO_KEY_ALIAS")
                    keyPassword = System.getenv("STUDIO_KEY_PASSWORD")
                }
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
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
        viewBinding = true
        // BuildConfig.VERSION_NAME usado em SettingsActivity (AGP 8 desativa por defeito)
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    testImplementation("junit:junit:4.13.2")
}
