plugins {
    id("com.google.devtools.ksp") version "2.2.21-2.0.4" apply false
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}

// Optional machine-local output path, e.g. outside a synchronised Documents folder.
val localBuildProperties = java.util.Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
localBuildProperties.getProperty("mpos.buildRoot")?.takeIf { it.isNotBlank() }?.let { outputRoot ->
    layout.buildDirectory.set(file(outputRoot).resolve("root"))
    subprojects {
        layout.buildDirectory.set(rootProject.file(outputRoot).resolve(name))
    }
}
