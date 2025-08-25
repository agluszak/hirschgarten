package org.jetbrains.bazel.symbols

import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.bazel.info.BspTargetInfo
import org.jetbrains.bazel.label.*
import org.jetbrains.bazel.sync.ProjectSyncHook
import org.jetbrains.bazel.sync.withSubtask

/**
 * Sync hook that populates the BazelTargetIndex from BSP sync data.
 * This ensures we have accurate target information from Bazel itself.
 */
private class TargetIndexSyncHook : ProjectSyncHook {
  
  override suspend fun onSync(environment: ProjectSyncHook.ProjectSyncHookEnvironment) {
    environment.withSubtask("Updating target index") { _ ->
      val targetIndex = BazelTargetIndex.getInstance(environment.project)
      val targetInfos = mutableMapOf<Label, BazelTargetInfo>()
      
      // Convert BSP target info to our BazelTargetInfo format
      for ((label, bspTargetInfo) in environment.buildTargets) {
        val bazelTargetInfo = convertBspTargetInfo(label, bspTargetInfo)
        if (bazelTargetInfo != null) {
          targetInfos[label] = bazelTargetInfo
        }
      }
      
      // Update the target index with the new data
      targetIndex.updateTargets(targetInfos)
    }
  }
  
  /**
   * Convert BSP TargetInfo to our BazelTargetInfo format
   */
  private fun convertBspTargetInfo(label: Label, bspInfo: BspTargetInfo.TargetInfo): BazelTargetInfo? {
    try {
      // Extract package path and target name from label
      val packagePath = when (label) {
        is ResolvedLabel -> label.packagePath.toString()
        else -> ""
      }
      
      val targetName = label.targetName
      
      // Determine target type from BSP info
      val targetType = determineTargetType(bspInfo)
      
      // Extract rule name 
      val ruleName = bspInfo.kind ?: "unknown"
      
      // Extract dependencies
      val dependencies = bspInfo.depsList.map { it.toString() }
      
      // Determine build file path
      val buildFilePath = determineBuildFilePath(packagePath, bspInfo)
      
      return BazelTargetInfo(
        targetName = targetName,
        packagePath = packagePath,
        buildFilePath = buildFilePath,
        targetType = targetType,
        ruleName = ruleName,
        dependencies = dependencies
      )
      
    } catch (e: IllegalArgumentException) {
      thisLogger().debug("Failed to process BSP target info for label '$label': ${e.message}")
      return null
    } catch (e: Exception) {
      thisLogger().warn("Unexpected error converting BSP target info for label '$label'", e)
      return null
    }
  }
  
  /**
   * Determine target type from BSP target info
   */
  private fun determineTargetType(bspInfo: BspTargetInfo.TargetInfo): BazelTargetType {
    val kind = bspInfo.kind?.lowercase() ?: return BazelTargetType.UNKNOWN
    
    return when {
      kind.contains("binary") -> when {
        kind.contains("java") -> BazelTargetType.JAVA_BINARY
        kind.contains("kt") || kind.contains("kotlin") -> BazelTargetType.KOTLIN_BINARY
        kind.contains("cc") || kind.contains("cpp") -> BazelTargetType.CC_BINARY
        kind.contains("py") || kind.contains("python") -> BazelTargetType.PYTHON_BINARY
        kind.contains("go") -> BazelTargetType.GO_BINARY
        kind.contains("sh") -> BazelTargetType.SH_BINARY
        else -> BazelTargetType.GENERIC_BINARY
      }
      
      kind.contains("library") -> when {
        kind.contains("java") -> BazelTargetType.JAVA_LIBRARY
        kind.contains("kt") || kind.contains("kotlin") -> BazelTargetType.KOTLIN_LIBRARY
        kind.contains("cc") || kind.contains("cpp") -> BazelTargetType.CC_LIBRARY
        kind.contains("py") || kind.contains("python") -> BazelTargetType.PYTHON_LIBRARY
        kind.contains("go") -> BazelTargetType.GO_LIBRARY
        kind.contains("sh") -> BazelTargetType.SH_LIBRARY
        else -> BazelTargetType.GENERIC_LIBRARY
      }
      
      kind.contains("test") -> when {
        kind.contains("java") -> BazelTargetType.JAVA_TEST
        kind.contains("kt") || kind.contains("kotlin") -> BazelTargetType.KOTLIN_TEST
        kind.contains("cc") || kind.contains("cpp") -> BazelTargetType.CC_TEST
        kind.contains("py") || kind.contains("python") -> BazelTargetType.PYTHON_TEST
        kind.contains("go") -> BazelTargetType.GO_TEST
        kind.contains("sh") -> BazelTargetType.SH_TEST
        else -> BazelTargetType.GENERIC_TEST
      }
      
      kind == "alias" -> BazelTargetType.ALIAS
      kind == "genrule" -> BazelTargetType.GENRULE
      kind == "filegroup" -> BazelTargetType.FILEGROUP
      kind.contains("proto") -> BazelTargetType.PROTO_LIBRARY
      
      else -> BazelTargetType.fromRuleName(kind)
    }
  }
  
  /**
   * Determine BUILD file path from package path and BSP info
   */
  private fun determineBuildFilePath(packagePath: String, bspInfo: BspTargetInfo.TargetInfo): String {
    // Try to extract from BSP info if available
    val sourceRoot = bspInfo.sourceRootsList.firstOrNull()?.toString()
    
    return if (sourceRoot != null) {
      if (packagePath.isEmpty()) {
        "$sourceRoot/BUILD"
      } else {
        "$sourceRoot/$packagePath/BUILD"
      }
    } else {
      // Fallback construction
      if (packagePath.isEmpty()) {
        "BUILD"
      } else {
        "$packagePath/BUILD"
      }
    }
  }
}