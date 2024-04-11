import groovy.json.JsonSlurper
import org.jetbrains.kotlin.utils.sure
import org.pkl.core.Version
import org.pkl.gradle.task.ProjectPackageTask
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.net.ssl.HttpsURLConnection
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.math.ceil
import kotlin.math.log10

plugins {
    kotlin("jvm").version(libs.versions.kotlin)
    alias(libs.plugins.pkl)
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        licenseHeader("""
            /**
             * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
             *
             * Licensed under the Apache License, Version 2.0 (the "License");
             * you may not use this file except in compliance with the License.
             * You may obtain a copy of the License at
             *
             *     https://www.apache.org/licenses/LICENSE-2.0
             *
             * Unless required by applicable law or agreed to in writing, software
             * distributed under the License is distributed on an "AS IS" BASIS,
             * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
             * See the License for the specific language governing permissions and
             * limitations under the License.
             */
        """.trimIndent())
    }
    format("pkl") {
        licenseHeader("""
            //===----------------------------------------------------------------------===//
            // Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
            //
            // Licensed under the Apache License, Version 2.0 (the "License");
            // you may not use this file except in compliance with the License.
            // You may obtain a copy of the License at
            //
            //     https://www.apache.org/licenses/LICENSE-2.0
            //
            // Unless required by applicable law or agreed to in writing, software
            // distributed under the License is distributed on an "AS IS" BASIS,
            // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            // See the License for the specific language governing permissions and
            // limitations under the License.
            //===----------------------------------------------------------------------===//
        """.trimIndent(), "(/// |/\\*\\*|module |import |amends |(\\w+))")
        target("**/*.pkl", "**/PklProject")
    }
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.pklCore)
    testImplementation(libs.junitEngine)
    testImplementation(libs.junitParams)
}

val repositoryUrl = "https://github.com/apple/pkl-pantry"

val repositoryApiUrl = repositoryUrl.replace(Regex("github.com/"), "api.github.com/repos/")

val projectDirs: List<File> =
    Files.list(Path.of("packages"))
        .filter { it.isDirectory() }
        .map { it.toFile() }
        .toList()

val outputDir = layout.buildDirectory

pkl {
    project {
        resolvers {
            register("resolveProjects") {
                projectDirectories.from(projectDirs)
            }
        }
        packagers {
            register("createPackages") {
                projectDirectories.from(projectDirs)
                outputPath.set(outputDir.dir("generated/packages/%{name}/%{version}"))
                junitReportsDir.set(outputDir.dir("test-results"))
            }
        }
    }
}

val resolveProjects = tasks.named("resolveProjects") {
    group = "build"
    inputs.files(fileTree("packages/") { include("PklProject") })
    outputs.files(fileTree("packages/") { include("PklProject.deps.json") })
}

val createPackages = tasks.named<ProjectPackageTask>("createPackages") {
    group = "build"
}

/**
 * Return the set of packages that are dependents of the input package.
 */
@Suppress("UNCHECKED_CAST")
fun gatherDependentPackages(packageDirName: String): List<String> {
    return buildList {
        for (pkg in Files.list(file("packages/").toPath())) {
            if (pkg.name == packageDirName || !pkg.isDirectory()) {
                continue
            }
            val jsonFile = pkg.resolve("PklProject.deps.json")
            val json = JsonSlurper().parse(jsonFile) as Map<String, Any>
            val dependencies = json["resolvedDependencies"] as Map<String, Map<String, String>>
            for ((_, value) in dependencies) {
                if (value["path"] == "../$packageDirName") {
                    add(pkg.name)
                }
            }
        }
    }
}

val decorateFailureWithDependentPackageInformation by tasks.registering {
    onlyIf {
        @Suppress("SENSELESS_COMPARISON")
        createPackages.get().state.failure != null
    }
    doLast {
        val createPackagesTask = createPackages.get()
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        val failureMessage = createPackagesTask.state.failure!!.cause?.message ?: return@doLast
        if (!failureMessage.contains("was already published with different contents")) {
            return@doLast
        }
        val packageName = Regex("`(.+)`").find(failureMessage)?.groups?.get(1)?.value ?: return@doLast
        val dirName = packageName.drop("package://pkg.pkl-lang.org/pkl-pantry/".length)
            .dropLastWhile { it != '@' }
            .dropLast(1)
        val dependentPackages = gatherDependentPackages(dirName)
        if (dependentPackages.isEmpty()) {
            return@doLast
        }
        val msg = buildString {
            appendLine("The following packages depend on this package, and also need to have their version updated:")
            for (pkgName in dependentPackages) {
                appendLine("* $pkgName")
            }
        }
        createPackagesTask.state.addFailure(TaskExecutionException(createPackagesTask, RuntimeException(msg)))
    }
}

createPackages.get().finalizedBy(decorateFailureWithDependentPackageInformation)

val isInCircleCi = System.getenv("CIRCLE_PROJECT_REPONAME") != null

val prepareCiGit by tasks.registering {
    if (isInCircleCi) {
        exec {
            commandLine("git", "config", "user.email", "pkl-oss@groups.apple.com")
        }
        exec {
            commandLine("git", "config", "user.name", "The Pkl Team (automation)")
        }
    }
}

repositories {
    mavenCentral()
}

val prepareReleases by tasks.registering {
    group = "build"
    dependsOn(createPackages, prepareCiGit)
    inputs.files(projectDirs)

    doLast {
        val releaseDir = file(outputDir.dir("releases"))
        releaseDir.deleteRecursively()
        val count = projectDirs.count()
        val fmt = "%${ceil(log10(count.toDouble())).toInt()}d"
        for (i in projectDirs.indices) {
            val dir = projectDirs[i]
            print(" [${fmt.format(i + 1)}/$count] $dir: ")
            val allVersions = file(outputDir.dir("generated/packages/${dir.name}")).list()
            if (allVersions == null) {
                println("∅")
                continue
            }
            val latestVersion = allVersions.map(Version::parse).sortedWith(Version.comparator()).last()
            val pkg = "${dir.name}@$latestVersion"
            print("$pkg: ")
            val conn = URI("${repositoryUrl}/releases/tag/${dir.name}@$latestVersion")
                .toURL()
                .openConnection() as HttpsURLConnection
            if (conn.responseCode == 200) {
                println("⏩")
                continue
            }
            val taskOutput = StringBuilder()
            exec {
                commandLine("git", "tag", "-l", pkg)
                logging.addStandardOutputListener { taskOutput.append(it) }
                standardOutput = OutputStream.nullOutputStream()
            }
            if (taskOutput.contains(pkg)) {
                println("☑️")
                continue
            }
            for (artifact in file(outputDir.dir("generated/packages/${dir.name}/$latestVersion")).listFiles()!!) {
                artifact.copyTo(releaseDir.resolve("$pkg/${artifact.name}"), true)
            }
            println("✅")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(createPackages)
}

tasks.build {
    dependsOn(prepareReleases)
}
