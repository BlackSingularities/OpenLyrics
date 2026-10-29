import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0.0-dev"
// jpackage wymaga wersji w postaci liczb X.Y.Z.
val packageVersion = appVersionName.split('.', '-').mapNotNull { it.toIntOrNull() }.take(3).let { (it + listOf(0, 0, 0)).take(3) }.joinToString(".")

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    // Okno przepuszczające kliknięcia (WS_EX_TRANSPARENT) i rejestr – przez Win32.
    implementation("net.java.dev.jna:jna-platform:5.17.0")
}

val generateVersion by tasks.registering {
    val out = layout.buildDirectory.dir("generated/version")
    val v = appVersionName
    inputs.property("version", v)
    outputs.dir(out)
    doLast {
        val f = out.get().file("com/blacksingularities/openlyrics/desktop/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package com.blacksingularities.openlyrics.desktop\n\nobject BuildInfo {\n    const val VERSION = \"$v\"\n}\n")
    }
}
kotlin.sourceSets["main"].kotlin.srcDir(generateVersion)

compose.desktop {
    application {
        mainClass = "com.blacksingularities.openlyrics.desktop.MainKt"
        jvmArgs += listOf("-Xmx384m", "-Dfile.encoding=UTF-8", "-Dsun.java2d.d3d=true")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "OpenLyrics"
            packageVersion = packageVersion
            description = "OpenLyrics – tekst odtwarzanej piosenki nad oknami"
            vendor = "Adam Rędzikowski"
            copyright = "© 2026 Adam Rędzikowski"
            // Mostek do sesji multimediów Windows (bridge/) trafia do resources/windows.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            modules("java.naming", "jdk.unsupported", "java.net.http")
            windows {
                iconFile.set(project.file("icons/OpenLyrics.ico"))
                menuGroup = "OpenLyrics"
                upgradeUuid = "6c1d9b3e-5a4f-4f0e-9a7d-0b2a8d4e1f71"
            }
        }
    }
}
