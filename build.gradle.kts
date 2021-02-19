import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("java-library")
    id("net.minecrell.plugin-yml.bukkit") version "0.3.0"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = sourceCompatibility
}

version = "5.2"

repositories {
    jcenter()
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://repo.mikeprimm.com/") }
    maven { url = uri("https://maven.enginehub.org/repo/") }
    maven { url = uri("https://mvn.intellectualsites.com/content/repositories/releases/") }
}

dependencies {
    compileOnlyApi("com.plotsquared:PlotSquared-Core:5.13.3") {
        exclude(group = "worldedit-core")
    }
    compileOnlyApi("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.2")
    compileOnly("us.dynmap:dynmap-api:3.0-SNAPSHOT"){ isTransitive = false }
    compileOnly("org.projectlombok:lombok:1.18.18")
    annotationProcessor("org.projectlombok:lombok:1.18.16")
    implementation("org.bstats:bstats-bukkit:2.2.1")
    implementation("org.bstats:bstats-base:2.2.1")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set(null as String?)
    dependencies {
        include(dependency("org.bstats:bstats-bukkit:2.2.1"))
        include(dependency("org.bstats:bstats-base:2.2.1"))
        relocate("org.bstats", "com.empcraft.metrics")
    }
}

bukkit {
    name = "Plot2Dynmap"
    main = "com.empcraft.plot2dynmap.Main"
    authors = listOf("Empire92", "NotMyFault")
    apiVersion = "1.13"
    description = "his plugin adds a marker around claimed PlotSquared plots in the dynmap interface"
    version = rootProject.version.toString()
    depend = listOf("PlotSquared", "dynmap")
    website = "https://www.spigotmc.org/resources/1292/"
}

tasks.named("build").configure {
    dependsOn("shadowJar")
}
