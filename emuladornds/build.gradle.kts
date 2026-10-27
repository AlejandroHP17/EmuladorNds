plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

val githubOwner = "AlejandroHP17"
val githubRepo = "EmuladorNds"
val publishVersion =
    providers.environmentVariable("PUBLISH_VERSION").orElse("1.0.0-SNAPSHOT").get()

android {
    namespace = "pelkidev.com.mx.emuladornds"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
    }

    resourcePrefix = "emuladornds_"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api("pelkidev.com.mx.minijuegos:sdk:1.0.0")

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "pelkidev.com.mx.minijuegos"
            artifactId = "emuladornds"
            version = publishVersion

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/$githubOwner/$githubRepo")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orElse(githubOwner)
                    .get()
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GH_PACKAGES_TOKEN"))
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orElse("")
                    .get()
            }
        }
    }
}
