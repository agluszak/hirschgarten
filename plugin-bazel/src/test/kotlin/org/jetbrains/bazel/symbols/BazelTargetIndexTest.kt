package org.jetbrains.bazel.symbols

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.bazel.label.*

class BazelTargetIndexTest : BasePlatformTestCase() {

  fun testTargetInfoCreation() {
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

  fun testTargetIndexService() {
    val targetIndex = BazelTargetIndex.getInstance(myFixture.project)
    
    // Initially empty
    assertTrue("Index should be empty initially", targetIndex.getAllLabels().isEmpty())
    
    // Add some test data
    val label1 = ResolvedLabel(Main, Package(listOf("pkg1")), SingleTarget("target1"))
    val info1 = BazelTargetInfo(
      targetName = "target1",
      packagePath = "pkg1",
      buildFilePath = "pkg1/BUILD",
      targetType = BazelTargetType.JAVA_LIBRARY,
      ruleName = "java_library"
    )
    
    val label2 = ResolvedLabel(Main, Package(listOf("pkg2")), SingleTarget("target2"))
    val info2 = BazelTargetInfo(
      targetName = "target2", 
      packagePath = "pkg2",
      buildFilePath = "pkg2/BUILD",
      targetType = BazelTargetType.JAVA_BINARY,
      ruleName = "java_binary"
    )
    
    targetIndex.updateTargets(mapOf(label1 to info1, label2 to info2))
    
    // Verify data is indexed
    assertEquals(2, targetIndex.getAllLabels().size)
    assertEquals(info1, targetIndex.getTargetByLabel(label1))
    assertEquals(info2, targetIndex.getTargetByLabel(label2))
    
    // Test package queries
    val pkg1Targets = targetIndex.getTargetsInPackage("pkg1", Main)
    assertEquals(1, pkg1Targets.size)
    assertEquals("target1", pkg1Targets.first().targetName)
    
    // Test name-based search
    val target1Results = targetIndex.findTargetsByName("target1")
    assertEquals(1, target1Results.size)
    assertEquals(label1, target1Results.first().first)
  }
}