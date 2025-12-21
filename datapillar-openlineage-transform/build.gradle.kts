plugins {
    java
}

allprojects {
    group = "io.datapillar"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    dependencies {
        implementation("io.openlineage:openlineage-java:1.41.0")
    }
}
