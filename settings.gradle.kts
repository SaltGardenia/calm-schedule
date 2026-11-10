pluginManagement {
    // CI（GitHub Actions）访问阿里云镜像不稳定，直接走官方源；本地优先国内镜像
    val onCi = System.getenv("CI") != null
    repositories {
        if (!onCi) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("CI") == null) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        // 验证码 OCR（tesseract4android）发布在 JitPack
        maven("https://jitpack.io")
    }
}

rootProject.name = "calm-schedule"
include(":app")
