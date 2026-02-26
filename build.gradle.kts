plugins {
	kotlin("jvm") version "2.3.10"
	kotlin("plugin.spring") version "2.3.10"
	id("org.springframework.boot") version "4.1.0-SNAPSHOT"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.eknath"
version = "0.0.1-SNAPSHOT"
description = "A simple Gym Journal Backend"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	// Jackson 2.x for Catalyst SDK compatibility (Catalyst SDK uses com.fasterxml.jackson.* at runtime;
	// Spring Boot 4.x uses Jackson 3.x with tools.jackson.* — different namespace, safe to coexist)
	implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
	// Catalyst Java SDK 2.2.0 and its bundled dependencies
	// Excluding: servlet-api (Spring Boot provides Jakarta Servlet), slf4j (Spring Boot manages it),
	//            jackson-* (Catalyst bundles old Jackson 2.7.x; we provide a newer 2.x above instead)
	implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"), "exclude" to listOf("servlet-api*.jar", "slf4j-api*.jar", "jackson-*.jar"))))
	
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
