load("@//server/rules/kotlin:junit5.bzl", "kt_test")

kt_test(
    name = "BepDiagnosticsTest",
    src = "BepDiagnosticsTest.kt",
    deps = [
        "//server/server/src/main/kotlin/org/jetbrains/bsp/bazel/server/bep",
    ],
)

kt_test(
    name = "TestXmlParserTest",
    size = "small",
    src = "TestXmlParserTest.kt",
    deps = [
        "//server/server/src/main/kotlin/org/jetbrains/bsp/bazel/server/bep",
        "@maven//:ch_epfl_scala_bsp4j",
    ],
)
