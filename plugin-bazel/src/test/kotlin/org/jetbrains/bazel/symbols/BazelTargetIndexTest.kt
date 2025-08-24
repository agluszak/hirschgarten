package org.jetbrains.bazel.symbols

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.bazel.label.*

class BazelTargetIndexTest : BasePlatformTestCase() {

  fun testIndexBuildFile() {
    val buildContent = """
      java_library(
          name = "test_lib",
          srcs = ["Lib.java"],
          deps = ["//base:common"],
      )
      
      java_test(
          name = "test_lib_test",
          srcs = ["LibTest.java"],
          deps = [":test_lib"],
      )
      
      alias(
          name = "lib_alias",
          actual = ":test_lib",
      )
    """.trimIndent()
    
    // Create a BUILD file in the test project
    val buildFile = myFixture.addFileToProject("java/test/BUILD", buildContent)
    
    // Get indexed labels
    val allLabels = BazelTargetIndex.getAllLabels(myFixture.project)
    
    // Verify targets are indexed as proper labels
    val testLibLabel = ResolvedLabel(Main, Package(listOf("java", "test")), SingleTarget("test_lib"))
    val testLibTestLabel = ResolvedLabel(Main, Package(listOf("java", "test")), SingleTarget("test_lib_test"))
    val libAliasLabel = ResolvedLabel(Main, Package(listOf("java", "test")), SingleTarget("lib_alias"))
    
    assertTrue("Should index test_lib", allLabels.any { it.targetName == "test_lib" })
    assertTrue("Should index test_lib_test", allLabels.any { it.targetName == "test_lib_test" })
    assertTrue("Should index lib_alias", allLabels.any { it.targetName == "lib_alias" })
    
    // Verify we can find targets by exact label
    val testLib = BazelTargetIndex.getTargetByLabel(testLibLabel, myFixture.project)
    assertNotNull("Should find test_lib by label", testLib)
    
    assertEquals("test_lib", testLib!!.targetName)
    assertEquals("java/test", testLib.packagePath)
    assertEquals(BazelTargetType.JAVA_LIBRARY, testLib.targetType)
    assertEquals("java_library", testLib.ruleName)
    assertEquals(listOf("//base:common"), testLib.dependencies)
    
    // Verify alias is indexed with correct information
    val alias = BazelTargetIndex.getTargetByLabel(libAliasLabel, myFixture.project)
    assertNotNull("Should find lib_alias by label", alias)
    assertEquals("lib_alias", alias!!.targetName)
    assertEquals(BazelTargetType.ALIAS, alias.targetType)
  }

  fun testIndexMultiplePackages() {
    // Create BUILD files in different packages
    myFixture.addFileToProject("pkg1/BUILD", """
      java_library(name = "lib1", srcs = ["Lib1.java"])
    """.trimIndent())
    
    myFixture.addFileToProject("pkg2/BUILD", """
      java_library(name = "lib2", srcs = ["Lib2.java"])
    """.trimIndent())
    
    myFixture.addFileToProject("pkg1/subpkg/BUILD", """
      java_binary(name = "bin1", main_class = "Main", deps = ["//pkg1:lib1"])
    """.trimIndent())
    
    // Verify targets from different packages
    val targetsInPkg1 = BazelTargetIndex.getTargetsInPackage("pkg1", Main, myFixture.project)
    assertEquals(1, targetsInPkg1.size)
    assertEquals("lib1", targetsInPkg1.first().targetName)
    
    val targetsInPkg2 = BazelTargetIndex.getTargetsInPackage("pkg2", Main, myFixture.project)
    assertEquals(1, targetsInPkg2.size)
    assertEquals("lib2", targetsInPkg2.first().targetName)
    
    val targetsInSubPkg = BazelTargetIndex.getTargetsInPackage("pkg1/subpkg", Main, myFixture.project)
    assertEquals(1, targetsInSubPkg.size)
    assertEquals("bin1", targetsInSubPkg.first().targetName)
    assertEquals(BazelTargetType.JAVA_BINARY, targetsInSubPkg.first().targetType)
  }

  fun testIndexRootPackage() {
    myFixture.addFileToProject("BUILD", """
      filegroup(
          name = "root_config",
          srcs = ["config.yaml"],
      )
    """.trimIndent())
    
    val rootTargets = BazelTargetIndex.getTargetsInPackage("", Main, myFixture.project)
    assertEquals(1, rootTargets.size)
    assertEquals("root_config", rootTargets.first().targetName)
    assertEquals("", rootTargets.first().packagePath)
    assertEquals(BazelTargetType.FILEGROUP, rootTargets.first().targetType)
  }

  fun testIndexWithAliases() {
    myFixture.addFileToProject("tools/BUILD", """
      java_binary(
          name = "tool",
          main_class = "Tool",
      )
      
      alias(
          name = "my_tool",
          actual = ":tool",
      )
      
      alias(
          name = "tool_alias",
          actual = ":tool", 
      )
    """.trimIndent())
    
    // Original target should be indexed
    val toolLabel = ResolvedLabel(Main, Package(listOf("tools")), SingleTarget("tool"))
    val toolTarget = BazelTargetIndex.getTargetByLabel(toolLabel, myFixture.project)
    assertNotNull("Should find tool by label", toolTarget)
    assertEquals(BazelTargetType.JAVA_BINARY, toolTarget!!.targetType)
    
    // Aliases should be indexed separately as their own targets
    val myToolLabel = ResolvedLabel(Main, Package(listOf("tools")), SingleTarget("my_tool"))
    val myToolTarget = BazelTargetIndex.getTargetByLabel(myToolLabel, myFixture.project)
    assertNotNull("Should find my_tool by label", myToolTarget)
    assertEquals(BazelTargetType.ALIAS, myToolTarget!!.targetType)
    
    val toolAliasLabel = ResolvedLabel(Main, Package(listOf("tools")), SingleTarget("tool_alias"))
    val toolAliasTarget = BazelTargetIndex.getTargetByLabel(toolAliasLabel, myFixture.project)
    assertNotNull("Should find tool_alias by label", toolAliasTarget)
    assertEquals(BazelTargetType.ALIAS, toolAliasTarget!!.targetType)
  }

  fun testBazelTargetInfoSerialization() {
    val targetInfo = BazelTargetInfo(
      targetName = "test_target",
      packagePath = "java/com/example",
      buildFilePath = "/workspace/java/com/example/BUILD",
      targetType = BazelTargetType.JAVA_LIBRARY,
      ruleName = "java_library",
      dependencies = listOf("//base:common", ":util")
    )
    
    // Test conversion to symbol
    val symbol = targetInfo.toSymbol()
    assertEquals("test_target", symbol.targetName)
    assertEquals("java/com/example", symbol.packagePath)
    assertEquals(BazelTargetType.JAVA_LIBRARY, symbol.targetType)
  }

  fun testIndexIgnoresNonBuildFiles() {
    // Create various files - only BUILD files should be indexed
    myFixture.addFileToProject("src/Main.java", "public class Main {}")
    myFixture.addFileToProject("README.md", "# Project")
    myFixture.addFileToProject("config.yaml", "setting: value")
    
    // These should not be indexed for targets
    val allLabels = BazelTargetIndex.getAllLabels(myFixture.project)
    assertTrue(allLabels.isEmpty())
    
    // Add a BUILD file - this should be indexed
    myFixture.addFileToProject("BUILD", """
      filegroup(name = "files", srcs = ["**/*"])
    """.trimIndent())
    
    val labelsAfterBuild = BazelTargetIndex.getAllLabels(myFixture.project)
    assertTrue(labelsAfterBuild.any { it.targetName == "files" })
  }

  fun testIndexHandlesComplexDependencies() {
    myFixture.addFileToProject("complex/BUILD", """
      java_library(
          name = "complex_lib",
          srcs = ["Lib.java"],
          deps = [
              "//base:foundation",
              "//util:strings", 
              "//util:collections",
              "@maven//:junit",
              "@external_repo//pkg:target",
          ],
      )
    """.trimIndent())
    
    val complexLabel = ResolvedLabel(Main, Package(listOf("complex")), SingleTarget("complex_lib"))
    val complexLib = BazelTargetIndex.getTargetByLabel(complexLabel, myFixture.project)
    assertNotNull("Should find complex_lib by label", complexLib)
    
    assertEquals(5, complexLib!!.dependencies.size)
    assertTrue(complexLib.dependencies.contains("//base:foundation"))
    assertTrue(complexLib.dependencies.contains("@maven//:junit"))
    assertTrue(complexLib.dependencies.contains("@external_repo//pkg:target"))
  }
}