package org.jetbrains.bazel.symbols

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.bazel.label.Label
import org.jetbrains.bazel.languages.starlark.psi.expressions.StarlarkStringLiteralExpression

class BazelLabelSymbolReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiElement): Collection<PsiSymbolReference> {
    if (element !is StarlarkStringLiteralExpression) return emptyList()

    val label = Label.parseOrNull(element.stringValue) ?: return emptyList()
    return listOf(BazelLabelReference(element, label))
  }
}

private class BazelLabelReference(
  private val element: StarlarkStringLiteralExpression,
  private val label: Label
) : PsiSymbolReference {
  override fun getElement(): PsiElement = element

  override fun getRangeInElement() = element.textRange

  override fun resolveReference(): Collection<Symbol> {
    val project = element.project
    val normalizedLabel = normalizeLabel(project, label)
    return PsiSymbolService.getInstance()
      .getSymbols(BazelTarget::class.java, project)
      .filter { it.label == normalizedLabel }
  }

  private fun normalizeLabel(project: Project, label: Label): Label {
    if (label.isAbsolute) return label
    val containingFile = element.containingFile.originalFile.virtualFile
    val packagePath = project.basePath?.let { VfsUtil.getRelativePath(containingFile.parent, VfsUtil.findFileByIoFile(File(it), true)!!) }
    return label.toAbsolute(packagePath ?: "")
  }
}
