import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    `java-library`

    alias(libs.plugins.pluginyml)
    alias(libs.plugins.shadow)
}

the<JavaPluginExtension>().toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
}

configurations.all {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
}

tasks.compileJava.configure {
    options.release.set(17)
}

version = "7.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://repo.mikeprimm.com/") }
    maven { url = uri("https://maven.enginehub.org/repo/") }
}

dependencies {
    implementation(platform("com.intellectualsites.bom:bom-newest:1.32"))
    compileOnly("com.intellectualsites.plotsquared:plotsquared-core") {
        exclude(group = "worldedit-core")
    }
    compileOnly("io.papermc.paper:paper-api")
    compileOnly(libs.worldedit)
    compileOnly(libs.dynmapCore) { isTransitive = false }
    compileOnly(libs.dynmapApi) { isTransitive = false }
    implementation("org.bstats:bstats-bukkit")
    implementation("org.bstats:bstats-base")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set(null as String?)
    dependencies {
        relocate("org.bstats", "com.plotsquared.plot2dynmap.metrics")
    }
}

bukkit {
    name = "Plot2Dynmap"
    main = "com.plotsquared.plot2dynmap.Plot2DynmapPlugin"
    authors = listOf("Empire92", "NotMyFault", "dordsor21")
    apiVersion = "1.20"
    description = "This plugin adds a marker around claimed PlotSquared plots in the dynmap interface"
    version = rootProject.version.toString()
    depend = listOf("PlotSquared", "dynmap")
    website = "https://www.spigotmc.org/resources/1292/"
}

tasks.named("build").configure {
    dependsOn("shadowJar")
}
