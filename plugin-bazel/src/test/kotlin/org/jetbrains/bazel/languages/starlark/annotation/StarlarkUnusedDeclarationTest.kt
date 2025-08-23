package org.jetbrains.bazel.languages.starlark.annotation

import org.jetbrains.bazel.languages.starlark.fixtures.StarlarkAnnotatorTestCase

class StarlarkUnusedDeclarationTest : StarlarkAnnotatorTestCase() {
  fun testUnusedDeclaration() {
    myFixture.configureByFile("UnusedDeclarationTestData.bzl")
    myFixture.checkHighlighting(false, false, true)
  }

  fun testLoadedSymbolUsedInAnotherFile() {
    myFixture.configureByFiles("LoadedSymbol_defs.bzl", "LoadedSymbol_main.bzl")
    myFixture.checkHighlighting(false, false, true)
  }
}
