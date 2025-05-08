rootProject.name = "myHelper" // 设置项目名称，可以根据你的项目实际名称修改 

// 配置插件仓库和依赖仓库使用阿里云镜像
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        mavenCentral()
        google()
    }
} 