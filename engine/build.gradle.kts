plugins { id("org.jetbrains.kotlin.jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
}

tasks.test { useJUnitPlatform() }
