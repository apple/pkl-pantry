/**
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import groovy.json.JsonSlurper
import java.io.Serial
import java.io.Serializable
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.net.ssl.HttpsURLConnection
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.math.ceil
import kotlin.math.log10
import org.pkl.core.Version
import org.pkl.formatter.Formatter
import org.pkl.formatter.GrammarVersion
import org.pkl.gradle.task.BasePklTask
import org.pkl.gradle.task.ProjectPackageTask

plugins {
  alias(libs.plugins.kotlin)
  alias(libs.plugins.pkl)
  alias(libs.plugins.spotless)
}

class PklFormatterStep : Serializable {
  companion object {
    @Serial private const val serialVersionUID: Long = 1L
  }

  fun create(): FormatterStep {
    return FormatterStep.createLazy(
      "pkl",
      { PklFormatterStep() },
      { PklFormatterFunc() },
    )
  }
}

class PklFormatterFunc : FormatterFunc, Serializable {
  companion object {
    @Serial private const val serialVersionUID: Long = 1L
  }

  override fun apply(input: String): String {
    return Formatter().format(input, GrammarVersion.V1)
  }
}

val originalRemoteName = System.getenv("PKL_ORIGINAL_REMOTE_NAME") ?: "origin"

val ghaForkedFileLicense =
  """
  //===----------------------------------------------------------------------===//
  // This file contains code originally based off of github.com/StefMa/pkl-gha.
  //
  // Original license:
  //
  // MIT License
  //
  // Copyright (c) 2024 Stefan M.
  //
  // Permission is hereby granted, free of charge, to any person obtaining a copy
  // of this software and associated documentation files (the "Software"), to deal
  // in the Software without restriction, including without limitation the rights
  // to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  // copies of the Software, and to permit persons to whom the Software is
  // furnished to do so, subject to the following conditions:
  //
  // The above copyright notice and this permission notice shall be included in all
  // copies or substantial portions of the Software.
  //
  // THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  // IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  // FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  // AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  // LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  // OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  // SOFTWARE.
  //
  // -----------------------------------------------------------------------------
  //
  // Copyright © ${'$'}YEAR Apple Inc. and the Pkl project authors. All rights reserved.
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
"""
    .trimIndent()

@Suppress("CanConvertToMultiDollarString") // ktfmt can't
val blockHeader =
  """
  /**
   * Copyright © ${'$'}YEAR Apple Inc. and the Pkl project authors. All rights reserved.
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
  """
    .trimIndent()

spotless {
  ratchetFrom = "$originalRemoteName/main"

  kotlin {
    licenseHeader(blockHeader)
    target("src/**/*.kt", "buildSrc/**/*.kt")
    ktfmt().googleStyle()
  }
  kotlinGradle {
    licenseHeader(blockHeader, "([a-zA-Z]|@file|//)")
    ktfmt().googleStyle()
    target("*.kts", "buildSrc/**/*.kts")
  }
  format("pkl") {
    val delimiter = "(/// |/\\*\\*|module |import |amends |(\\w+))"
    licenseHeader(
        """
            //===----------------------------------------------------------------------===//
            // Copyright © ${'$'}YEAR Apple Inc. and the Pkl project authors. All rights reserved.
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
        """
          .trimIndent(),
        delimiter
      )
      .named("base-license-header")
    licenseHeader(ghaForkedFileLicense, delimiter).named("pkl-gha").onlyIfContentMatches("gha-fork")
    target("**/*.pkl", "**/PklProject")
    addStep(PklFormatterStep().create())
  }
}

kotlin { jvmToolchain(21) }

repositories { mavenCentral() }

dependencies {
  testImplementation(libs.pklCore)
  testImplementation(libs.pklParser)
  testImplementation(libs.junitEngine)
  testImplementation(libs.junitParams)
  testRuntimeOnly(libs.junitPlatformLauncher)
}

val repositoryUrl = "https://github.com/apple/pkl-pantry"

val repositoryApiUrl = repositoryUrl.replace(Regex("github.com/"), "api.github.com/repos/")

val projectDirs: List<File> =
  Files.list(Path.of("packages")).filter { it.isDirectory() }.map { it.toFile() }.toList()

val outputDir = layout.buildDirectory

pkl.project {
  resolvers.register("resolveProjects") { projectDirectories.from(projectDirs) }

  packagers.register("createPackages") {
    projectDirectories.from(projectDirs)
    outputPath = outputDir.dir("generated/packages/%{name}/%{version}")
    allowedResources = listOf("file:", "pkl:", "https:", "prop:", "packaage:", "projectpackage:")
    junitReportsDir = outputDir.dir("test-results")
  }
}

tasks.withType<BasePklTask> { color = true }

val resolveProjects =
  tasks.named("resolveProjects") {
    group = "build"
    inputs.files(fileTree("packages/") { include("PklProject") })
    outputs.files(fileTree("packages/") { include("PklProject.deps.json") })
  }

val createPackages = tasks.named<ProjectPackageTask>("createPackages") { group = "build" }

/** Return the set of packages that are dependents of the input package. */
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

val decorateFailureWithDependentPackageInformation by
  tasks.registering {
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
      val dirName =
        packageName
          .drop("package://pkg.pkl-lang.org/pkl-pantry/".length)
          .dropLastWhile { it != '@' }
          .dropLast(1)
      val dependentPackages = gatherDependentPackages(dirName)
      if (dependentPackages.isEmpty()) {
        return@doLast
      }
      val msg = buildString {
        appendLine(
          "The following packages depend on this package, and also need to have their version updated:"
        )
        for (pkgName in dependentPackages) {
          appendLine("* $pkgName")
        }
      }
      createPackagesTask.state.addFailure(
        TaskExecutionException(createPackagesTask, RuntimeException(msg))
      )
    }
  }

createPackages.get().finalizedBy(decorateFailureWithDependentPackageInformation)

repositories { mavenCentral() }

val prepareReleases by
  tasks.registering {
    group = "build"
    dependsOn(createPackages)
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
        val conn =
          URI("${repositoryUrl}/releases/tag/${dir.name}@$latestVersion").toURL().openConnection()
            as HttpsURLConnection
        if (conn.responseCode == 200) {
          println("⏩")
          continue
        }
        val execProvider = providers.exec { commandLine("git", "tag", "-l", pkg) }
        execProvider.result.get()
        if (execProvider.standardOutput.asText.get().contains(pkg)) {
          println("☑️")
          continue
        }
        for (artifact in
          file(outputDir.dir("generated/packages/${dir.name}/$latestVersion")).listFiles()!!) {
          artifact.copyTo(releaseDir.resolve("$pkg/${artifact.name}"), true)
        }
        println("✅")
      }
    }
  }

val testTarExamples by
  tasks.registering(Exec::class) {
    executable = file("packages/pkl.tar/examples/test_examples.sh").absolutePath

    inputs.files(fileTree("packages/pkl.tar/"))

    val outputFile = layout.buildDirectory.file("testTarExample.txt")
    outputs.file(outputFile)

    doLast { outputFile.get().asFile.writeText("OK") }
  }

tasks.test {
  useJUnitPlatform()
  dependsOn(createPackages)
  dependsOn(testTarExamples)
}

tasks.build { dependsOn(prepareReleases) }
