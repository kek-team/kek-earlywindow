plugins {
    `java-library`
}

group = "team.kek"
version = "0.1.0"

// Код окна и GL перенесён из этой версии earlydisplay. Лаунчер включает провайдер,
// только если в версии NeoForge та же библиотека (атрибут манифеста ниже).
val earlydisplayVersion = "4.0.45"
val lwjglVersion = "3.3.3"

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    maven("https://maven.neoforged.net/releases")
    maven("https://libraries.minecraft.net") // com.mojang:logging у loader
    mavenCentral()
}

dependencies {
    compileOnly("net.neoforged.fancymodloader:loader:$earlydisplayVersion")
    // Только запасной DisplayWindow: при сбое своего окна все вызовы уходят ему.
    compileOnly("net.neoforged.fancymodloader:earlydisplay:$earlydisplayVersion")
    for (m in listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb")) {
        compileOnly("org.lwjgl:$m:$lwjglVersion")
    }
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    compileOnly("org.jetbrains:annotations:24.1.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Тесты собирают классы, ссылающиеся на типы FML, — им нужен тот же classpath.
    testImplementation("net.neoforged.fancymodloader:loader:$earlydisplayVersion")
    testImplementation("net.neoforged.fancymodloader:earlydisplay:$earlydisplayVersion")
    testImplementation("org.slf4j:slf4j-api:2.0.9")
}

tasks.jar {
    manifest {
        attributes(
            "Kek-Earlydisplay-Version" to earlydisplayVersion,
            "Implementation-Version" to project.version,
        )
    }
    from("LICENSE")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar)
    systemProperty("kek.test.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
}
