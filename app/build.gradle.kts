import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

abstract class CompileRootLauncher : DefaultTask() {
    @get:InputFile abstract val sourceFile: RegularFileProperty
    @get:Internal abstract val ndkDirectory: DirectoryProperty
    @get:Input abstract val ndkVersion: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:Inject abstract val execOperations: ExecOperations
    @TaskAction fun compile() {
        val host = when {
            System.getProperty("os.name").startsWith("Windows") -> "windows-x86_64"
            System.getProperty("os.name").startsWith("Mac") -> "darwin-x86_64"
            else -> "linux-x86_64"
        }
        val suffix = if (host.startsWith("windows")) ".exe" else ""
        val clang = ndkDirectory.file("toolchains/llvm/prebuilt/$host/bin/clang$suffix").get().asFile
        val targets = mapOf("arm64-v8a" to "aarch64-linux-android28", "armeabi-v7a" to "armv7a-linux-androideabi28",
            "x86_64" to "x86_64-linux-android28", "x86" to "i686-linux-android28")
        targets.forEach { (abi, target) ->
            val output = outputDirectory.file("$abi/libwallc_launcher.so").get().asFile
            output.parentFile.mkdirs()
            execOperations.exec {
                commandLine(clang.absolutePath, "--target=$target", "-O2", "-Wall", "-Wextra", "-Werror",
                    "-fPIE", "-pie", "-Wl,-z,max-page-size=16384", sourceFile.get().asFile.absolutePath,
                    "-o", output.absolutePath)
            }
        }
    }
}

val rootLauncher = tasks.register<CompileRootLauncher>("compileRootLauncher") {
    sourceFile.set(layout.projectDirectory.file("src/main/cpp/root_launcher.c"))
    ndkDirectory.set(androidComponents.sdkComponents.sdkDirectory.map { it.dir("ndk/28.2.13676358") })
    ndkVersion.set("28.2.13676358")
    outputDirectory.set(layout.buildDirectory.dir("generated/rootLauncher"))
}

android {
    namespace = "org.eu.suisei.wallc"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.eu.suisei.wallc"
        minSdk = 28
        targetSdk = 37
        versionCode = 6
        versionName = "1.2.1"
    }
    buildFeatures { buildConfig = true; compose = true }
    ndkVersion = "28.2.13676358"
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
androidComponents.onVariants { variant ->
    variant.sources.jniLibs?.addGeneratedSourceDirectory(rootLauncher) { it.outputDirectory }
}
dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui:1.12.0")
    implementation("androidx.compose.foundation:foundation:1.12.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
    testImplementation("junit:junit:4.13.2")
}
tasks.named("preBuild").configure { dependsOn(rootLauncher) }
