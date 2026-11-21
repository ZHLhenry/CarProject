plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/central") }
}

dependencies {
    // 读取 class 文件（API 面 + 方法引用），不依赖 Android Gradle Plugin
    implementation("org.ow2.asm:asm:9.11.1")
}
