plugins {
    java
    application
}

group = "org.braniik"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "StackTrace"
    applicationDefaultJvmArgs = listOf("-Dsun.java2d.opengl=true")
}

tasks.withType<JavaCompile>().configureEach {
    options.isDebug = false
}