import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath("com.gradleup.shadow:shadow-gradle-plugin:8.3.9")
        // ASM 9.8 reads Java 25 (class file major 69); Shadow's bundled ASM cannot.
        classpath("org.ow2.asm:asm:9.8")
        classpath("org.ow2.asm:asm-commons:9.8")
    }
    configurations.classpath {
        resolutionStrategy {
            force(
                "org.ow2.asm:asm:9.8",
                "org.ow2.asm:asm-commons:9.8",
                "org.ow2.asm:asm-tree:9.8",
                "org.ow2.asm:asm-analysis:9.8",
            )
        }
    }
}

plugins {
    kotlin("jvm") version "2.3.20"
}

apply(plugin = "com.gradleup.shadow")

group = "dev.willram"
version = "2.1.0-SNAPSHOT"

val paperVersion = "26.1.2.build.60-stable"
val junitVersion = "5.11.4"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://jitpack.io")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    // Provided by the server or by RamCore at runtime (declared in paper-plugin.yml). Never shaded.
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("dev.willram:ramcore-api:2.0.1")
    compileOnly("dev.willram:ramcore-protocol:2.0.1")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("io.lumine:Mythic-Dist:5.6.1")

    // Shaded into the plugin jar, relocated below.
    implementation("org.bstats:bstats-bukkit:3.0.2")

    // compileOnly is not on the test classpath in Gradle; the existing tests import paper-api and
    // RamCore types, so they must be added back for the test compilation. ramcore-test declares
    // api(ramcore-api), so it puts the whole ramcore-api on the test classpath transitively;
    // ramcore-protocol carries the dev.willram.ramcore.packet package the render tests use.
    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation("dev.willram:ramcore-protocol:2.0.1")
    testImplementation("dev.willram:ramcore-test:2.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    // Gradle 9 no longer puts the JUnit Platform launcher on the test runtime classpath automatically.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Filter the plugin version into paper-plugin.yml from the Gradle version. Scoped to that one file
// so Groovy templating never touches lang/*.json or content/*.conf (which contain literal '$'/'{').
tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("project" to project)
    }
}

// The shaded jar is the deliverable; disable the thin jar so both do not claim the same filename.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    relocate("org.bstats", "dev.willram.ramrpg.libs.bstats")
}

tasks.named("build") {
    dependsOn("shadowJar")
}
