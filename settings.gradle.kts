pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val githubOwner = "AlejandroHP17"

fun firstNonBlank(vararg values: String?): String =
    values.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull().orEmpty()

val gprUser = firstNonBlank(
    providers.gradleProperty("gpr.user").orNull,
    System.getenv("GITHUB_ACTOR"),
    githubOwner,
)
val gprToken = firstNonBlank(
    providers.gradleProperty("gpr.token").orNull,
    System.getenv("GH_PACKAGES_TOKEN"),
    System.getenv("GITHUB_TOKEN"),
)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven {
            name = "GitHubPackagesMiniJuegosSdk"
            url = uri("https://maven.pkg.github.com/$githubOwner/MiniJuegosSdk")
            credentials {
                username = gprUser
                password = gprToken
            }
        }
    }
}

rootProject.name = "EmuladorNds"
include(":app")
include(":emuladornds")
