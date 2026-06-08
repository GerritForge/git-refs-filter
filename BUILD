load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)

PLUGIN = "git-refs-filter"

gerrit_plugin(
    name = PLUGIN,
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
)

gerrit_plugin_tests(
    srcs = glob(
        [
            "src/test/java/**/*.java",
        ],
    ),
    plugin = PLUGIN,
    visibility = ["//visibility:public"],
)
