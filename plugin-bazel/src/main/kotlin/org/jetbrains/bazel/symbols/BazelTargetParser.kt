package org.jetbrains.bazel.symbols

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.bazel.languages.starlark.psi.*

/**
 * Utility for parsing Bazel target information from BUILD file content
 */
object BazelTargetParser {


  /**
   * Parse targets from a Starlark file using PSI
   */
  fun parseTargetsFromStarlarkFile(file: StarlarkFile): List<BazelTargetInfo> {
    val targets = mutableListOf<BazelTargetInfo>()
    
    val packagePath = getPackagePathFromPsiFile(file)
    val buildFilePath = file.virtualFile?.path ?: return emptyList()
    
    val targetCalls = PsiTreeUtil.findChildrenOfType(file, StarlarkCallExpression::class.java)
      .filter { isTargetRuleCall(it) }
    
    for (call in targetCalls) {
      val target = createTargetInfoFromCall(call, packagePath, buildFilePath)
      if (target != null) {
        targets.add(target)
      }
    }
    
    return targets
  }


  private fun isTargetRuleCall(call: StarlarkCallExpression): Boolean {
    // A call is likely a target rule if it has a 'name' parameter
    // This is the most reliable indicator
    return hasNameParameter(call)
  }

  private fun hasNameParameter(call: StarlarkCallExpression): Boolean {
    return call.argumentList?.findArgumentByName("name") != null
  }

  private fun createTargetInfoFromCall(
    call: StarlarkCallExpression,
    packagePath: String,
    buildFilePath: String
  ): BazelTargetInfo? {
    val ruleName = call.callee?.text ?: return null
    val targetName = extractTargetName(call) ?: return null
    
    val targetType = BazelTargetType.fromRuleName(ruleName)
    val dependencies = extractDependenciesFromCall(call)
    
    return BazelTargetInfo(
      targetName = targetName,
      packagePath = packagePath,
      buildFilePath = buildFilePath,
      targetType = targetType,
      ruleName = ruleName,
      dependencies = dependencies
    )
  }

  private fun extractTargetName(call: StarlarkCallExpression): String? {
    val nameArg = call.argumentList?.findArgumentByName("name") ?: return null
    val nameValue = nameArg.value as? StarlarkStringLiteralExpression ?: return null
    return nameValue.stringValue
  }

  private fun extractDependenciesFromCall(call: StarlarkCallExpression): List<String> {
    val dependencies = mutableListOf<String>()
    
    // Look for 'deps' parameter
    val depsArg = call.argumentList?.findArgumentByName("deps")
    if (depsArg?.value is StarlarkListLiteralExpression) {
      val depsList = depsArg.value as StarlarkListLiteralExpression
      for (element in depsList.elements) {
        if (element is StarlarkStringLiteralExpression) {
          dependencies.add(element.stringValue)
        }
      }
    }
    
    return dependencies
  }




  private fun getPackagePathFromFile(file: VirtualFile): String {
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

  private fun getPackagePathFromPsiFile(file: StarlarkFile): String {
    val virtualFile = file.virtualFile ?: return ""
    return getPackagePathFromFile(virtualFile)
  }
}