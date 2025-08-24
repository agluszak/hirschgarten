package org.jetbrains.bazel.symbols

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.IncorrectOperationException
import org.jetbrains.bazel.label.*
import org.jetbrains.bazel.languages.starlark.psi.StarlarkStringLiteralExpression

/**
 * Context information about where a reference appears
 */
data class SourceContext(
  val repo: RepoType,
  val packagePath: List<String>
)

/**
 * Reference from a string literal to a Bazel target symbol
 */
class BazelTargetReference(
  element: PsiElement,
  private val targetLabel: String,
  private val rangeInElement: TextRange,
  private val project: Project
) : PsiReferenceBase<PsiElement>(element, rangeInElement), PsiSymbolReference {

  private val resolvedSymbols: Collection<BazelTargetSymbol> by lazy {
    resolveToSymbols()
  }

  override fun resolve(): PsiElement? {
    // For traditional resolve() method, return the first declaration found
    val symbols = resolveToSymbols()
    return symbols.firstOrNull()?.let { findDeclarationElement(it) }
  }

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val symbols = resolveToSymbols()
    return symbols.mapNotNull { symbol ->
      findDeclarationElement(symbol)?.let { element ->
        PsiElementResolveResult(element)
      }
    }.toTypedArray()
  }

  override fun resolveReference(): Collection<Symbol> {
    return resolveToSymbols()
  }

  override fun resolvesTo(symbol: Symbol): Boolean {
    return symbol is BazelTargetSymbol && resolvedSymbols.contains(symbol)
  }

  override fun getVariants(): Array<Any> {
    // Provide completion variants - all available targets
    val allTargets = BazelTargetIndex.findTargetsByName("", project)  // Get all targets
    return allTargets.map { (label, targetInfo) ->
      com.intellij.codeInsight.lookup.LookupElementBuilder.create(targetInfo.targetName)
        .withIcon(org.jetbrains.bazel.assets.BazelPluginIcons.bazel)
        .withTypeText("Bazel Target")
        .withTailText(" (${targetInfo.packagePath})", true)
    }.toTypedArray()
  }

  override fun handleElementRename(newElementName: String): PsiElement {
    if (element is StarlarkStringLiteralExpression) {
      // Parse the current label and replace just the target name part
      try {
        val label = Label.parse(targetLabel)
        val newLabel = when (label) {
          is ResolvedLabel -> 
            label.copy(target = SingleTarget(newElementName))
          is RelativeLabel ->
            label.copy(target = SingleTarget(newElementName))
          else -> label
        }
        
        // Replace the string content
        return element.updateText("\"${newLabel}\"")
      } catch (e: Exception) {
        throw IncorrectOperationException("Cannot rename target reference", e)
      }
    }
    
    return super.handleElementRename(newElementName)
  }

  private fun resolveToSymbols(): Collection<BazelTargetSymbol> {
    try {
      val label = Label.parse(targetLabel)
      
      val resolvedLabel = when (label) {
        is RelativeLabel -> {
          // Resolve relative to source context
          val sourceContext = getSourceContext()
          val base = ResolvedLabel(
            repo = sourceContext.repo,
            packagePath = Package(sourceContext.packagePath),
            target = SingleTarget("")
          )
          label.resolve(base)
        }
        
        is ResolvedLabel -> {
          // Apply repository mapping if needed
          applyRepoMapping(label)
        }
        
        else -> return emptyList()
      }
      
      // Now we can do exact lookup by the resolved label
      val targetInfo = BazelTargetIndex.getTargetByLabel(resolvedLabel, project)
      return if (targetInfo != null) {
        listOf(targetInfo.toSymbol())
      } else {
        emptyList()
      }
      
    } catch (e: Exception) {
      // Invalid label format
      return emptyList()
    }
  }
  
  private fun getSourceContext(): SourceContext {
    // Get the package context where this reference appears
    val containingFile = element.containingFile
    val virtualFile = containingFile.virtualFile
    
    if (virtualFile != null) {
      val packagePath = getPackagePathFromFile(virtualFile)
      // For now assume main workspace - proper repo detection would go here  
      return SourceContext(Main, packagePath.split("/").filter { it.isNotEmpty() })
    }
    
    return SourceContext(Main, emptyList())
  }
  
  private fun applyRepoMapping(label: ResolvedLabel): Label {
    // Use existing repository mapping service if available
    return when (label.repo) {
      is Apparent -> {
        // Convert @rules_java -> @@rules_java~1.2.3 using repo mapping
        // For now, just return the label as-is
        label
      }
      else -> label
    }
  }
  
  private fun getPackagePathFromFile(file: com.intellij.openapi.vfs.VirtualFile): String {
    val filePath = file.path
    
    // Find workspace root
    var currentDir = file.parent
    while (currentDir != null) {
      val children = currentDir.children
      if (children.any { it.name == "WORKSPACE" || it.name == "MODULE.bazel" }) {
        // Found workspace root
        val workspacePath = currentDir.path
        val relativePath = filePath.removePrefix(workspacePath).removePrefix("/")
        val packageDir = relativePath.removeSuffix("/${file.name}")
        
        return packageDir.takeIf { it.isNotEmpty() } ?: ""
      }
      currentDir = currentDir.parent
    }
    
    return ""
  }

  private fun findDeclarationElement(symbol: BazelTargetSymbol): PsiElement? {
    // Find the PSI element that declares this target
    val file = PsiManager.getInstance(project).findFile(
      com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(
        java.io.File(symbol.buildFilePath), false
      ) ?: return null
    ) ?: return null
    
    // Find the target declaration in the file
    // This is a simplified implementation - in production, we'd use the symbol declaration provider
    if (file is org.jetbrains.bazel.languages.starlark.psi.StarlarkFile) {
      val targetCalls = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
        file, 
        org.jetbrains.bazel.languages.starlark.psi.StarlarkCallExpression::class.java
      )
      
      for (call in targetCalls) {
        val nameArg = call.argumentList?.findArgumentByName("name")
        val nameValue = nameArg?.value as? StarlarkStringLiteralExpression
        if (nameValue?.stringValue == symbol.targetName) {
          return nameValue
        }
      }
    }
    
    return null
  }
}

/**
 * Provides references from string literals to Bazel targets
 */
class BazelTargetReferenceProvider : PsiReferenceProvider() {

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if (element !is StarlarkStringLiteralExpression) {
      return PsiReference.EMPTY_ARRAY
    }
    
    val stringValue = element.stringValue
    if (!looksLikeTargetLabel(stringValue)) {
      return PsiReference.EMPTY_ARRAY
    }
    
    val project = element.project
    val range = TextRange(1, element.textLength - 1) // Exclude quotes
    
    return arrayOf(BazelTargetReference(element, stringValue, range, project))
  }

  private fun looksLikeTargetLabel(value: String): Boolean {
    // Simple heuristics to identify target labels
    return value.startsWith("//") || 
           value.startsWith(":") || 
           (value.contains(":") && !value.contains(" ")) ||
           (!value.contains(" ") && value.isNotEmpty()) // No spaces, not empty
  }
}

/**
 * Contributor that registers the Bazel target reference provider
 */
class BazelTargetReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    // Register for string literals in Starlark files
    registrar.registerReferenceProvider(
      com.intellij.patterns.PlatformPatterns.psiElement(StarlarkStringLiteralExpression::class.java)
        .inFile(com.intellij.patterns.PlatformPatterns.psiFile(org.jetbrains.bazel.languages.starlark.psi.StarlarkFile::class.java)),
      BazelTargetReferenceProvider()
    )
  }
}