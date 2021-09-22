import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    `java-library`

    id("net.minecrell.plugin-yml.bukkit") version "0.5.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

the<JavaPluginExtension>().toolchain {
    languageVersion.set(JavaLanguageVersion.of(16))
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://repo.mikeprimm.com/") }
    maven { url = uri("https://maven.enginehub.org/repo/") }
    maven { url = uri("https://mvn.intellectualsites.com/content/groups/public/") }
}

dependencies {
    compileOnlyApi("com.plotsquared:PlotSquared-Core:6.1.2") {
        exclude(group = "worldedit-core")
    }
    compileOnlyApi("org.spigotmc:spigot-api:1.17.1-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.0-SNAPSHOT")
    compileOnly("us.dynmap:dynmap-api:3.2-beta-1"){ isTransitive = false }
    compileOnly("us.dynmap:DynmapCore:3.2-beta-1"){ isTransitive = false }
    compileOnly("org.projectlombok:lombok:1.18.20")
    annotationProcessor("org.projectlombok:lombok:1.18.20")
    implementation("org.bstats:bstats-bukkit:2.2.1")
    implementation("org.bstats:bstats-base:2.2.1")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set(null as String?)
    dependencies {
        include(dependency("org.bstats:bstats-bukkit:2.2.1"))
        include(dependency("org.bstats:bstats-base:2.2.1"))
        relocate("org.bstats", "com.plotsquared.metrics")
    }
}

bukkit {
    name = "Plot2Dynmap"
    main = "com.plotsquared.plot2dynmap.Plot2DynmapPlugin"
    authors = listOf("Empire92", "NotMyFault", "dordsor21")
    apiVersion = "1.13"
    description = "This plugin adds a marker around claimed PlotSquared plots in the dynmap interface"
    version = rootProject.version.toString()
    depend = listOf("PlotSquared", "dynmap")
    website = "https://www.spigotmc.org/resources/1292/"
}

tasks.named("build").configure {
    dependsOn("shadowJar")
}
