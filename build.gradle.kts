plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

group = "io.github.copecone"
version = "0.0.1-INDEV"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    mingwX64("native") {
        compilations.getByName("main") {
            cinterops {
                val openal by creating {
                    defFile("src/nativeInterop/cinterop/openal.def")
                    packageName("openal")
                }
            }
        }

        val openALPath = "C:/Program Files (x86)/OpenAL 1.1 SDK/libs/Win64"
        binaries.executable {
            entryPoint = "$group.raycastaudioengine.main"

            linkerOpts.add("-L${openALPath}")
            linkerOpts.add("-lOpenAL32")
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(libs.kotlinxSerializationJson)
        }
    }
}
