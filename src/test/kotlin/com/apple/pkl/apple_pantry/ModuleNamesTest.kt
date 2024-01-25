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
package com.apple.pkl.apple_pantry

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.api.condition.DisabledIf
import org.pkl.core.EvaluatorBuilder
import org.pkl.core.ModuleSource
import org.pkl.core.parser.Lexer
import org.pkl.core.project.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.relativeTo

class ModuleNamesTest {
    companion object {
        private val currentWorkingDir: Path
            get() = Path.of(System.getProperty("user.dir"))

        private val rootProjectDir: Path by lazy {
            val workingDir = currentWorkingDir
            workingDir.takeIf { it.resolve("settings.gradle.kts").exists() }
                ?: workingDir.parent.takeIf { it.resolve("settings.gradle.kts").exists() }
                ?: workingDir.parent.parent.takeIf { it.resolve("settings.gradle.kts").exists() }
                ?: throw AssertionError("Failed to locate root project directory.")
        }
        private val packagesDir = rootProjectDir.resolve("packages")

        private val packageDirs by lazy {
            Files.list(packagesDir).filter(Path::isDirectory).toList()
        }

        private val evaluators by lazy {
            packageDirs.associate { dir ->
                val project = Project.loadFromPath(dir.resolve("PklProject"))
                dir.name to EvaluatorBuilder.preconfigured().applyFromProject(project).build()
            }
        }

        @JvmStatic
        fun discoverPklModules(): List<Pair<Path, Path>> {
            return packageDirs
                .flatMap { packageDir ->
                    val pklModules = Files.walk(packageDir)
                        .filter { it.name.endsWith(".pkl") }
                        .filter { !it.contains(Path.of("fixtures")) }
                        .filter { !it.contains(Path.of("examples")) }
                        .toList()
                    pklModules.map { packageDir to it }
                }
        }
    }

    @ParameterizedTest
    @MethodSource("discoverPklModules")
    fun testModuleName(packageAndModule: Pair<Path, Path>) {
        val (packagePath, modulePath) = packageAndModule
        val packageName = packagePath.name
        val evaluator = evaluators[packageName]!!
        val schema = evaluator.evaluateSchema(ModuleSource.path(modulePath))
        val relativePath = modulePath.relativeTo(packagePath.toAbsolutePath())
        val expectedName = packageName + "." + relativePath.toString().dropLast(4).replace('/', '.')

        val exceptionList = listOf("S008wrong")

        if (schema.moduleName != expectedName && !exceptionList.contains(schema.moduleName)) {
            val expectedNameQuoted = expectedName
                .split(".")
                .joinToString(".", transform = Lexer::maybeQuoteIdentifier)
            throw AssertionError(
                """
                Expected module name $expectedName, but was ${schema.moduleName} in file ${modulePath.toUri()}

                To fix, replace the following:

                - module ${schema.moduleName}
                + module $expectedNameQuoted
                """
            )
        }
    }
}
