package org.jetbrains.bazel.symbols

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bazel.label.Label

data class BazelTarget(
  val label: Label,
  val kind: String,
  val virtualFile: VirtualFile,
) : Symbol {
  override fun createPointer(): Pointer<out Symbol> = BazelTargetPointer(this)
}

private class BazelTargetPointer(private val target: BazelTarget) : Pointer<BazelTarget> {
  override fun dereference(project: Project): BazelTarget? {
    val file = VfsUtil.findFileByIoFile(target.virtualFile.toNioPath().toFile(), true)
    return if (file != null) {
      target.copy(virtualFile = file)
    } else {
      null
    }
  }
}
