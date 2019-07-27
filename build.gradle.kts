import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

allprojects {
    group = "de.hanno.companionvals"
    version = "0.0.1"
}

buildscript {
    repositories {
        mavenCentral()
        google()
        jcenter()
    }
}

plugins {
    maven
    java
    kotlin("jvm") version "1.3.41"
    kotlin("kapt") version "1.3.41"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":annotation"))
    implementation("com.squareup:kotlinpoet:1.3.0")

    implementation("com.google.auto.service:auto-service:1.0-rc4")
    implementation("me.eugeniomarletti.kotlin.metadata:kotlin-metadata:1.4.0")
    kapt("com.google.auto.service:auto-service:1.0-rc4")

    testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
