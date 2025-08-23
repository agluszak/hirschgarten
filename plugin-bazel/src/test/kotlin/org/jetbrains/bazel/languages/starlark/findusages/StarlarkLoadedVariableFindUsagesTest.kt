package org.jetbrains.bazel.languages.starlark.findusages

import io.kotest.matchers.collections.shouldHaveSize
import org.jetbrains.bazel.languages.starlark.fixtures.StarlarkFindUsagesTestCase
import org.jetbrains.bazel.languages.starlark.psi.expressions.StarlarkTargetExpression
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StarlarkLoadedVariableFindUsagesTest : StarlarkFindUsagesTestCase() {
  @Test
  fun `should find usage in another file`() {
    myFixture.configureByFiles("LoadedVariable_defs.bzl", "LoadedVariable_main.bzl")
    val variable = myFixture.file.children.filterIsInstance<StarlarkTargetExpression>().first()
    val usages = myFixture.findUsages(variable)
    usages shouldHaveSize 1
  }
}
