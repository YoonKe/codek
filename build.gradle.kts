plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.16.0" // 使用与 IDEA 2024.1 兼容的插件版本
    id("org.jetbrains.kotlin.jvm") version "1.9.22" // 显式指定 Kotlin 版本
}

group = "com.steins.codek"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// 使用阿里云镜像仓库
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/central") }
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("org.jetbrains.kotlin:kotlin-stdlib") {
        version {
            strictly("1.9.22") // 显式指定 Kotlin 标准库版本
        }
    }
}

intellij {
    version.set("2024.1")
    type.set("IC") // Community Edition
    plugins.set(listOf("java"))
    downloadSources.set(false)
}

tasks {
    instrumentCode {
        isEnabled = false // 禁用 instrumentCode 任务
    }

    patchPluginXml {
        version.set(project.version.toString())
        sinceBuild.set("241") // 保持 2024.1 的构建号
    }
    
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}