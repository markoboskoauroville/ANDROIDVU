import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ONE SOURCE FOR THE NUMBER. gradle.properties holds it; nothing here and nothing in the workflow
// carries a second copy that could disagree. versioning.md §3 records the trap this avoids: TTT mini
// says build 127 in every document while the phone's own app list says 1.0, because the number the
// person can see was the one that went stale.
val appVersion = (project.findProperty("appVersion") as String? ?: "1").toInt()

// The keystore is restored by CI from an encrypted repository secret into a properties file that is
// never committed — .gitignore lists both. When it is absent the release build is left unsigned
// rather than silently signed with the debug key, because a debug-signed release installs fine once
// and then refuses to upgrade to a properly signed one, and Android reports that as "app not
// installed" with nothing about signatures in it.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.mantraproductions.androidvu"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mantraproductions.androidvu"
        // 26 is not a preference. getActivePlaybackConfigurations — the one call this whole build
        // exists to test — arrived in API 26, and so did TYPE_APPLICATION_OVERLAY, which the real
        // app will need. Below 26 there is no version of this app worth installing.
        minSdk = 26
        targetSdk = 34
        versionCode = appVersion
        versionName = appVersion.toString()
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.containsKey("storeFile")) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 off, deliberately, and this is the one place a probe should differ from the app it
            // grows into. delivery-gate.md §9.3 is right that a benchmark on a debug build measures
            // a program you are not shipping — but this build is not measuring itself, it is
            // measuring ANDROID, and a shrinker that renames and inlines is one more variable
            // between the reading and the truth. It goes on at the first version that draws a meter.
            isMinifyEnabled = false
            if (keystoreProperties.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        // BuildConfig.VERSION_NAME is printed at the top of every report, so the text he pastes back
        // says which build produced it. AGP 8 turns this off by default.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // G3 wants lint fatal on release. It is NOT switched on blind here: delivery-gate.md §5.2 is
        // explicit that a check producing noise gets narrowed or deleted in the session it produced
        // it, and nobody has yet seen how loud lint is on this tree. The workflow measures the count
        // first and prints it. Turning this to true is a decision for v2, with a number behind it.
        abortOnError = false
    }
}

dependencies {
    // No AndroidX, no Compose, no Material library. The screen is five buttons and a TextView on
    // android.app.Activity with the platform Material theme, which minSdk 26 already has. Nothing is
    // gained by pulling in a dependency graph to measure an audio API, and delivery-gate.md §9.2
    // asks for the dependency count per release — this one is one line long.
    testImplementation("junit:junit:4.13.2")
}
