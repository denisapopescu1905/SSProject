import com.android.build.api.dsl.JacocoOptions

plugins {
    id("com.android.application")
    id("jacoco")
    id("org.sonarqube") version "6.2.0.5505"
}

android {
    namespace = "com.example.ss_final_java"
    compileSdk = 35
    android.buildFeatures.buildConfig =  true

    defaultConfig {
        applicationId = "com.example.ss_final_java"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
            }
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("String", "DEBUG_LOG", "\"true\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

sonar {
    properties {
        property("sonar.projectKey", "denisapopescu1905_SSProject")
        property("sonar.organization", "denisapopescu1905")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest") // rulează mai întâi testele

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/R*Test*.*",  // Exclude tests starting with R
        "android/**/*.*"
    )

    val javaClasses = fileTree("${buildDir}/intermediates/javac/debug") {
        exclude(fileFilter)
    }

    val kotlinClasses = fileTree("${buildDir}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }

    val sourceDirs = files(
        "${project.projectDir}/src/main/java",
        "${project.projectDir}/src/main/kotlin"
    )

    classDirectories.setFrom(files(javaClasses, kotlinClasses))
    sourceDirectories.setFrom(sourceDirs)
    executionData.setFrom(fileTree(buildDir) {
        include("jacoco/testDebugUnitTest.exec")
    })

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")

    implementation ("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")


    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation ("androidx.camera:camera-lifecycle:1.4.1")
    implementation ("androidx.camera:camera-view:1.5.0-alpha06")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.59")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("androidx.test:core:1.5.0")
    //testImplementation ("org.conscrypt:conscrypt-android:2.5.2")  // versiune recomandată (poți verifica ultima pe Maven Central)

    testImplementation ("junit:junit:4.13.2")
    testImplementation  ("org.mockito:mockito-core:5.2.0")
    testImplementation  ("org.mockito:mockito-inline:5.2.0")
    testImplementation  ("org.mockito:mockito-android:5.2.0")
    testImplementation ("org.robolectric:robolectric:4.10.3")
    testImplementation ("androidx.test:core:1.4.0")



}
