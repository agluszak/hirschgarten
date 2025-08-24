package org.jetbrains.bazel.stubs

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.bazel.symbols.BazelTarget

class BazelTargetIndex : StringStubIndexExtension<BazelTarget>() {
  override fun getKey(): StubIndexKey<String, BazelTarget> = KEY

  companion object {
    val KEY = StubIndexKey.createIndexKey<String, BazelTarget>("bazel.target.index")
  }
}
