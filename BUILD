load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "git-refs-filter",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
)

junit_tests(
    name = "git_refs_filter_tests",
    srcs = glob(
        [
            "src/test/java/**/*Test.java",
            "src/test/java/**/*IT.java",
        ],
        exclude = ["src/test/java/**/Abstract*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":git-refs-filter__plugin",
        ":git_refs_filter__plugin_test_deps",
    ],
)

java_library(
    name = "git_refs_filter__plugin_test_deps",
    testonly = 1,
    srcs = glob(["src/test/java/**/Abstract*.java"]),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":git-refs-filter__plugin",
    ],
)
