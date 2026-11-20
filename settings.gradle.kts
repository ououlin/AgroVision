// ============================================================
// 仓库源策略
// ------------------------------------------------------------
// 国内镜像（maven.aliyun.com）默认关闭，原因：
//   1. 公共仓库的 CI 跑在境外 runner 上，aliyun 镜像不可达 / 拒绝；
//   2. 该镜像的 public 仓库只代理 Maven Central，Google 系制品需走 /repository/google，
//      混用会让插件解析出现大量 404 干扰。
// 需要加速时显式开启（二选一）：
//   环境变量   AGROVISION_CN_MIRROR=true
//   Gradle 参数 -PcnMirror=true
//
// 注意：pluginManagement {} 必须是 settings 文件的第一个块，
// 所以开关判断在两个块内各算一次，不能提到顶层。
// ============================================================

pluginManagement {
    val cnMirror: Boolean =
        System.getenv("AGROVISION_CN_MIRROR")?.toBoolean() == true ||
            (gradle.startParameter.projectProperties["cnMirror"]?.toBoolean() == true)

    repositories {
        if (cnMirror) {
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val cnMirror: Boolean =
        System.getenv("AGROVISION_CN_MIRROR")?.toBoolean() == true ||
            (gradle.startParameter.projectProperties["cnMirror"]?.toBoolean() == true)

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (cnMirror) {
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "AgroVision"
include(":app")
