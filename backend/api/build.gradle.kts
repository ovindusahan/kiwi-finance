plugins {
    alias(libs.plugins.spring.boot)
}

configurations.configureEach {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":finance-engine"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.log4j2)
    implementation(libs.springdoc.openapi.webmvc.api)
    implementation(libs.bouncycastle.provider)
    implementation(libs.commons.csv)
    implementation(libs.jspecify)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.awaitility)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    systemProperty("contract.output", rootProject.file("../contracts/openapi.json").absolutePath)
    systemProperty("contract.update", System.getProperty("contract.update") ?: "false")
}
