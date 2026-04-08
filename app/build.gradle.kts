import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appReleaseVersionCode = 9
val appReleaseVersionName = "2.2"
val releaseAbiSplits = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

abstract class RenameReleaseApksTask : DefaultTask() {
    @get:Internal
    abstract val primaryMetadataFile: RegularFileProperty

    @get:Internal
    abstract val ideRedirectDirectory: DirectoryProperty

    @get:Internal
    abstract val fallbackMetadataFile: RegularFileProperty

    @get:Input
    abstract val appVersionName: Property<String>

    @get:Input
    abstract val abiSplits: ListProperty<String>

    @TaskAction
    fun renameOutputs() {
        val metadata = resolveMetadataFile() ?: return
        val outputDir = metadata.parentFile ?: return
        if (!outputDir.isDirectory) return

        @Suppress("UNCHECKED_CAST")
        val parsedMetadata = JsonSlurper().parse(metadata) as? MutableMap<String, Any?> ?: return
        @Suppress("UNCHECKED_CAST")
        val elements = parsedMetadata["elements"] as? List<MutableMap<String, Any?>> ?: return
        val expectedFileNames = linkedSetOf<String>()
        val abiSplitSet = abiSplits.get().toSet()

        elements.forEach { element ->
            val sourceFileName = element["outputFile"]?.toString().orEmpty()
            if (sourceFileName.isBlank()) return@forEach

            val outputType = element["type"]?.toString().orEmpty()
            @Suppress("UNCHECKED_CAST")
            val filters = element["filters"] as? List<Map<String, Any?>> ?: emptyList()
            val abiLabel = when (outputType) {
                "UNIVERSAL" -> "universal"
                else -> filters.firstOrNull { it["filterType"]?.toString() == "ABI" }
                    ?.let { filter ->
                        filter["value"]?.toString() ?: filter["identifier"]?.toString()
                    }
            } ?: return@forEach

            if (abiLabel !in abiSplitSet && abiLabel != "universal") {
                return@forEach
            }

            val targetFileName = "BiliTools-v${appVersionName.get()}-$abiLabel.apk"
            expectedFileNames += targetFileName

            moveIfNeeded(
                sourceFile = resolveOutputFile(outputDir, sourceFileName),
                targetFile = resolveOutputFile(outputDir, targetFileName),
            )
            renameBaselineProfiles(
                parsedMetadata = parsedMetadata,
                outputDir = outputDir,
                sourceFileName = sourceFileName,
                targetFileName = targetFileName,
            )
            element["outputFile"] = targetFileName
        }

        if (expectedFileNames.isEmpty()) {
            return
        }

        outputDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.extension.equals("apk", ignoreCase = true) &&
                    file.name !in expectedFileNames
            }
            ?.forEach { staleApk ->
                staleApk.delete()
            }

        metadata.writeText(
            JsonOutput.prettyPrint(JsonOutput.toJson(parsedMetadata)),
            Charsets.UTF_8,
        )
    }

    private fun resolveMetadataFile(): File? {
        val candidates = linkedSetOf<File>()
        resolveIdeRedirectMetadataFile()?.let(candidates::add)
        primaryMetadataFile.orNull?.asFile?.let(candidates::add)
        fallbackMetadataFile.orNull?.asFile?.let(candidates::add)
        return candidates
            .filter(File::isFile)
            .maxByOrNull(File::lastModified)
    }

    private fun resolveIdeRedirectMetadataFile(): File? {
        val redirectRoot = ideRedirectDirectory.orNull?.asFile ?: return null
        if (!redirectRoot.isDirectory) return null

        return redirectRoot.walkTopDown()
            .maxDepth(6)
            .filter { file ->
                file.isFile && file.name.equals("redirect.txt", ignoreCase = true)
            }
            .mapNotNull(::parseRedirectedMetadataFile)
            .firstOrNull(File::isFile)
    }

    private fun parseRedirectedMetadataFile(redirectFile: File): File? {
        val listingFile = redirectFile.readLines(Charsets.UTF_8)
            .firstOrNull { line -> line.startsWith("listingFile=") }
            ?.substringAfter("listingFile=")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return redirectFile.parentFile.resolve(listingFile)
            .toPath()
            .normalize()
            .toFile()
    }

    private fun renameBaselineProfiles(
        parsedMetadata: MutableMap<String, Any?>,
        outputDir: File,
        sourceFileName: String,
        targetFileName: String,
    ) {
        val sourceDmName = sourceFileName.substringBeforeLast('.', sourceFileName) + ".dm"
        val targetDmName = targetFileName.substringBeforeLast('.', targetFileName) + ".dm"

        @Suppress("UNCHECKED_CAST")
        val baselineProfiles =
            parsedMetadata["baselineProfiles"] as? List<MutableMap<String, Any?>> ?: return

        baselineProfiles.forEach { baselineProfile ->
            @Suppress("UNCHECKED_CAST")
            val profileFiles =
                baselineProfile["baselineProfiles"] as? MutableList<Any?> ?: return@forEach

            for (index in profileFiles.indices) {
                val relativePath = profileFiles[index]?.toString().orEmpty()
                if (relativePath.isBlank()) continue
                if (relativePath.substringAfterLast('/') != sourceDmName) continue

                val renamedRelativePath = replaceRelativeFileName(relativePath, targetDmName)
                moveIfNeeded(
                    sourceFile = resolveOutputFile(outputDir, relativePath),
                    targetFile = resolveOutputFile(outputDir, renamedRelativePath),
                )
                profileFiles[index] = renamedRelativePath
            }
        }
    }

    private fun replaceRelativeFileName(relativePath: String, fileName: String): String {
        val parentPath = relativePath.substringBeforeLast('/', "")
        return if (parentPath.isBlank()) {
            fileName
        } else {
            "$parentPath/$fileName"
        }
    }

    private fun resolveOutputFile(outputDir: File, relativePath: String): File =
        outputDir.resolve(relativePath.replace('/', File.separatorChar))
            .toPath()
            .normalize()
            .toFile()

    private fun moveIfNeeded(sourceFile: File, targetFile: File) {
        if (!sourceFile.exists() || sourceFile.name == targetFile.name) return
        targetFile.parentFile?.mkdirs()
        Files.move(
            sourceFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=first-only")
    }
}

android {
    namespace = "com.happycola233.bilitools"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.happycola233.bilitools"
        minSdk = 29
        targetSdk = 36
        versionCode = appReleaseVersionCode
        versionName = appReleaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    splits {
        abi {
            isEnable = true
            reset()
            include(*releaseAbiSplits.toTypedArray())
            isUniversalApk = true
        }
    }
}

val renameReleaseApks by tasks.registering(RenameReleaseApksTask::class) {
    primaryMetadataFile.set(layout.buildDirectory.file("outputs/apk/release/output-metadata.json"))
    ideRedirectDirectory.set(layout.buildDirectory.dir("intermediates/apk_ide_redirect_file/release"))
    fallbackMetadataFile.set(layout.projectDirectory.file("release/output-metadata.json"))
    appVersionName.set(appReleaseVersionName)
    abiSplits.set(releaseAbiSplits)
}

val releaseApkTaskNames = setOf(
    "assembleRelease",
    "packageRelease",
    "packageReleaseUniversalApk",
    "createReleaseApkListingFileRedirect",
)

renameReleaseApks.configure {
    mustRunAfter(tasks.matching { it.name in releaseApkTaskNames })
}

tasks.matching { it.name in releaseApkTaskNames }.configureEach {
    finalizedBy(renameReleaseApks)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.backdrop)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.zxing.core)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.markwon.core)
    implementation(libs.jaudiotagger)
    implementation(libs.ffmpeg.kit.main.android16kb)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
