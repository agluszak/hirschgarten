load("@rules_kotlin//kotlin:core.bzl", "kt_compiler_plugin")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

kt_compiler_plugin(
    name = "open_for_testing_plugin",
    compile_phase = True,
    id = "org.jetbrains.kotlin.allopen",
    options = {
        "annotation": "plugin.allopen.OpenForTesting",
    },
    stubs_phase = True,
    deps = [
        "@rules_kotlin//kotlin/compiler:allopen-compiler-plugin",
    ],
)

kt_jvm_library(
    name = "open_for_testing",
    srcs = ["OpenForTesting.kt"],
)

kt_jvm_library(
    name = "user",
    srcs = ["User.kt"],
    plugins = [
        ":open_for_testing_plugin",
    ],
    deps = [
        ":open_for_testing",
    ],
)

kt_jvm_library(
    name = "open_for_testing_export",
    srcs = [],
    exported_compiler_plugins = [":open_for_testing_plugin"],
    exports = [":open_for_testing"],
)

kt_jvm_library(
    name = "user_of_export",
    srcs = ["User.kt"],
    deps = [
        ":open_for_testing_export",
    ],
)
