plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.nuitcode.daytesk.server.ApplicationKt")
}

dependencies {
    implementation("io.ktor:ktor-server-netty:3.2.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.2.3")
    implementation("io.ktor:ktor-server-cors:3.2.3")
    implementation("io.ktor:ktor-server-auth:3.2.3")
    implementation("io.ktor:ktor-server-status-pages:3.2.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("com.h2database:h2:2.3.232")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("com.auth0:java-jwt:4.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}
