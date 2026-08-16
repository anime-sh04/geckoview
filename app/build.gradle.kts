plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.geckobrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.geckobrowser"
        // minSdk intentionally left unchanged -- GeckoView's own minimum
        // requirement and this app's UI don't need to move, and there is
        // no dependency in the graph requiring a higher minSdk.
        minSdk = 23
        // targetSdk raised alongside compileSdk: Google/Play policy expects
        // targetSdk to track compileSdk closely, and leaving targetSdk far
        // behind compileSdk risks silently missing behavior changes for the
        // platform version you're actually compiling against.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
    }

    // GeckoView ships multiple architecture-specific native libraries.
    // Since we depend on the universal "geckoview" artifact (all ABIs bundled),
    // no additional packaging/splits configuration is required to get started.
    // For a smaller production APK, switch to per-ABI artifacts (see README).
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // GeckoView - Firefox's Gecko rendering engine for embedding in Android apps.
    // "geckoview" (no channel/ABI suffix) is the universal release-channel artifact
    // bundling all supported ABIs, published to Mozilla's Maven repository.
    // Check https://maven.mozilla.org/?prefix=maven2/org/mozilla/geckoview/geckoview/
    // for the newest version before you build; GeckoView ships a new build roughly
    // every 4 weeks following the Firefox release train.
    implementation("org.mozilla.geckoview:geckoview:153.0.20260810162159")
}
