// Pure Kotlin: domain models, calculators and rules. No Android dependency, so tests run fast on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Bytecode targets Java 17; any JDK 17+ can build it.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
