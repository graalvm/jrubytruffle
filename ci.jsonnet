# File is formatted with
# `jsonnet fmt --indent 2 --max-blank-lines 2 --sort-imports --string-style d --comment-style h -i ci.jsonnet`

# The Visual Studio Code is recommended for editing jsonnet files.
# It has a 'jsonnet' plugin with a output preview. Alternatively Atom can be used.
#
# Formatter is part of `jsonnet` command, it can be installed with
# `brew install jsonnet` on mac or with http://linuxbrew.sh/ on a Linux machine
# with the same command. Or it can be downloaded from 
# https://github.com/google/jsonnet/releases and compiled.

# CONFIGURATION
local overlay = "a14b1782d00bf46901b920761900bde71f49f8aa";

# For debugging: generated builds will be restricted to those listed in
# the array. No restriction is applied when it is empty.
local restrict_builds_to = [],
      debug = std.length(restrict_builds_to) > 0;
# Set to false to disable overlay application
local use_overlay = true;

# Import support functions, they can be replaced with identity functions
# and it would still work.
local utils = import "utils.libsonnet";

# All builds are composed **directly** from **independent disjunct composable**
# jsonnet objects defined in here. Use `+:` to make the objects or arrays
# stored in fields composable, see http://jsonnet.org/docs/tutorial.html#oo.
# All used objects used to compose a build are listed
# where build is defined, there are no other objects in the middle.
local part_definitions = {
  local jt = function(args) [["ruby", "tool/jt.rb"] + args],

  use: {
    common: {
      local build = self,
      environment+: {
        path+:: ["$JAVA_HOME/bin", "$MAVEN_HOME/bin", "$PATH"],
        java_opts+:: ["-Xmx2G"],
        CI: "true",
        RUBY_BENCHMARKS: "true",
        JAVA_OPTS: std.join(" ", self.java_opts),
        PATH: std.join(":", self.path),
      },

      setup+: [],
    },

    svm: {
      downloads+: {
        GDB: { name: "gdb", version: "7.11.1", platformspecific: true },
      },

      environment+: {
        GDB_BIN: "$GDB/bin/gdb",
        HOST_VM: "svm",
      },
    },

    maven: {
      downloads+: {
        MAVEN_HOME: { name: "maven", version: "3.3.9" },
      },
    },

    build: {
      setup+: [
        ["mx", "sversions"],
        ["mx", "build", "--force-javac", "--warning-as-error"],
      ],
    },

    sulong: {
      downloads+: {
        LIBGMP: {
          name: "libgmp",
          version: "6.1.0",
          platformspecific: true,
        },
      },

      environment+: {
        CPPFLAGS: "-I$LIBGMP/include",
        LD_LIBRARY_PATH: "$LIBGMP/lib:$LLVM/lib:$LD_LIBRARY_PATH",
      },

      setup+: [
        ["git", "clone", ["mx", "urlrewrite", "https://github.com/graalvm/sulong.git"], "../sulong"],
        ["mx", "sforceimports"],  # ensure versions declared in TruffleRuby
        ["cd", "../sulong"],
        ["mx", "sversions"],
        ["mx", "build"],
        ["cd", "../main"],
      ],
    },

    truffleruby: {
      "$.benchmark.server":: { options: [] },
      environment+: {
        # Using jruby for compatibility with existing benchmark results.
        GUEST_VM: "jruby",
        GUEST_VM_CONFIG: "truffle",
      },
    },

    truffleruby_cexts: {
      is_after+:: ["$.use.truffleruby"],
      environment+: {
        # to differentiate running without (chunky_png) and with cexts (oily_png).
        GUEST_VM_CONFIG+: "-cexts",
      },
    },

    gem_test_pack: {
      setup+: jt(["gem-test-pack"]),
    },

    mri: {
      "$.benchmark.server":: { options: ["--", "--no-core-load-path"] },
      downloads+: {
        MRI_HOME: { name: "ruby", version: "2.3.3" },
      },

      environment+: {
        HOST_VM: "mri",
        HOST_VM_CONFIG: "default",
        GUEST_VM: "mri",
        GUEST_VM_CONFIG: "default",
        RUBY_BIN: "$MRI_HOME/bin/ruby",
        JT_BENCHMARK_RUBY: "$MRI_HOME/bin/ruby",
      },
    },

    jruby: {
      "$.benchmark.server":: { options: ["--", "--no-core-load-path"] },
      downloads+: {
        JRUBY_HOME: { name: "jruby", version: "9.1.12.0" },
      },

      environment+: {
        HOST_VM: "server",
        HOST_VM_CONFIG: "default",
        GUEST_VM: "jruby",
        GUEST_VM_CONFIG: "indy",
        RUBY_BIN: "$JRUBY_HOME/bin/jruby",
        JT_BENCHMARK_RUBY: "$JRUBY_HOME/bin/jruby",
        JRUBY_OPTS: "-Xcompile.invokedynamic=true",
      },
    },
  },

  graal: {
    core: {
      setup+: [
        ["cd", "../graal/compiler"],
        ["mx", "sversions"],
        ["mx", "build"],
        ["cd", "../../main"],
      ],

      environment+: {
        GRAAL_HOME: "../graal/compiler",
        HOST_VM: "server",
        HOST_VM_CONFIG: "graal-core",
      },
    },

    none: {
      environment+: {
        HOST_VM: "server",
        HOST_VM_CONFIG: "default",
        MX_NO_GRAAL: "true",
      },
    },

    enterprise: {
      setup+: [
        [
          "git",
          "clone",
          ["mx", "urlrewrite", "https://github.com/graalvm/graal-enterprise.git"],
          "../graal-enterprise",
        ],
        ["cd", "../graal-enterprise/graal-enterprise"],
        ["mx", "sforceimports"],
        ["mx", "sversions"],
        ["mx", "clean"],  # Workaround for NFI
        ["mx", "build"],
        ["cd", "../../main"],
      ],

      environment+: {
        GRAAL_HOME: "$PWD/../graal-enterprise/graal-enterprise",
        HOST_VM: "server",
        HOST_VM_CONFIG: "graal-enterprise",
      },
    },

    without_om: {
      is_after+:: ["$.graal.enterprise", "$.use.common"],
      environment+: {
        HOST_VM_CONFIG+: "-no-om",
        java_opts+::
          ["-Dtruffle.object.LayoutFactory=com.oracle.truffle.object.basic.DefaultLayoutFactory"],
      },
    },
  },

  svm: {
    core: {
      is_after+:: ["$.use.build"],

      setup+: [
        ["cd", "../graal/substratevm"],
        ["mx", "sforceimports"],
        ["mx", "sversions"],
        ["cd", "../../main"],
      ],

      "$.svm.build_image":: {
        aot_bin: "$GRAAL_HOME/../substratevm/ruby",
      },

      environment+: {
        HOST_VM_CONFIG: "graal-core",
        GRAAL_HOME: "$PWD/../graal/compiler",
        SVM_HOME: "$PWD/../graal/substratevm",
      },
    },

    enterprise: {
      is_after+:: ["$.use.build"],

      setup+: [
        [
          "git",
          "clone",
          ["mx", "urlrewrite", "https://github.com/graalvm/graal-enterprise.git"],
          "../graal-enterprise",
        ],
        ["cd", "../graal-enterprise/substratevm-enterprise"],
        ["mx", "sforceimports"],
        ["mx", "sversions"],
        ["cd", "../../main"],
      ],

      "$.svm.build_image":: {
        aot_bin: "$GRAAL_HOME/../substratevm-enterprise/ruby",
      },

      environment+: {
        HOST_VM_CONFIG: "graal-enterprise",
        GRAAL_HOME: "$PWD/../graal-enterprise/graal-enterprise",
        SVM_HOME: "$PWD/../graal-enterprise/substratevm-enterprise",
      },
    },

    build_image: {
      setup+: [
        ["cd", "$SVM_HOME"],
        # Workaround for NFI when building with different Truffle versions
        ["mx", "clean"],
        ["mx", "build"],
        ["mx", "fetch-languages", "--ruby"],
        # aot-build.log is used for the build-stats metrics
        ["./native-image", "-no-server", "--ruby", "|", "tee", "../../main/aot-build.log"],
        ["cd", "../../main"],
      ],

      local build = self,
      environment+: {
        JT_BENCHMARK_RUBY: "$AOT_BIN",
        AOT_BIN: build["$.svm.build_image"].aot_bin,
        # so far there is no conflict buy it may become cumulative later
        TRUFFLERUBYOPT: "-Xhome=$PWD",
      },
    },
  },

  jdk: {
    labsjdk8: {
      downloads+: {
        JAVA_HOME: {
          name: "labsjdk",
          version: "8u151-jvmci-0.40",
          platformspecific: true,
        },
      },
    },

    labsjdk9: {
      downloads+: {
        JAVA_HOME: {
          name: "labsjdk",
          version: "9+181",
          platformspecific: true,
        },
      },
    },
  },

  platform: {
    linux: {
      local build = self,
      "$.run.deploy_and_spec":: { test_spec_options: ["-Gci"] },
      "$.cap":: {
        normal_machine: ["linux", "amd64"],
        bench_machine: ["x52"] + self.normal_machine + ["no_frequency_scaling"],
      },
      packages+: {
        git: ">=1.8.3",
        mercurial: ">=3.2.4",
        ruby: ">=2.0.0",
        llvm: "==3.8",
      },
    },
    darwin: {
      "$.run.deploy_and_spec":: { test_spec_options: ["-GdarwinCI"] },
      "$.cap":: {
        normal_machine: ["darwin_sierra", "amd64"],
      },
      environment+: {
        path+:: ["/usr/local/opt/llvm/bin"],
        LANG: "en_US.UTF-8",
        # Homebrew does not put llvm on the PATH by default
        OPENSSL_PREFIX: "/usr/local/opt/openssl",
      },
    },
    solaris: {
      "$.run.deploy_and_spec":: { test_spec_options: [] },
      "$.cap":: {
        normal_machine: ["solaris", "sparcv9"],
        bench_machine: ["m7_eighth", "solaris"],
      },
      environment+: {
        # LLVM is currently not available on Solaris
        TRUFFLERUBY_CEXT_ENABLED: "false",
      },
    },
  },

  cap: {
    gate: {
      capabilities+: self["$.cap"].normal_machine,
      targets+: ["gate", "post-push"],
      environment+: {
        REPORT_GITHUB_STATUS: "true",
      },
    },
    deploy: { targets+: ["deploy"] },
    fast_cpu: { capabilities+: ["x62"] },
    bench: { capabilities+: self["$.cap"].bench_machine },
    x52_18_override: {
      is_after+:: ["$.cap.bench"],
      capabilities: if std.count(super.capabilities, "x52") > 0
      then std.map(function(c) if c == "x52" then "x52_18" else c,
                   super.capabilities)
      else error "trying to override x52 but it is missing",
    },
    daily: { targets+: ["bench", "daily"] },
    weekly: { targets+: ["weekly"] },
  },

  run: {
    deploy_and_spec: {
      local without_rewrites = function(commands)
        [
          ["export", "PREV_MX_URLREWRITES=$MX_URLREWRITES"],
          ["unset", "MX_URLREWRITES"],
        ] + commands + [
          ["export", "MX_URLREWRITES=$PREV_MX_URLREWRITES"],
        ],
      local deploy_binaries_commands = [
        ["mx", "deploy-binary-if-master-or-release"],
      ],
      local deploy_binaries_no_rewrites = without_rewrites(deploy_binaries_commands),
      local deploy_binaries = deploy_binaries_commands + deploy_binaries_no_rewrites,

      run+: deploy_binaries +
            jt(["test", "specs"] + self["$.run.deploy_and_spec"].test_spec_options) +
            jt(["test", "specs", ":ruby24"]) +
            jt(["test", "specs", ":ruby25"]),
    },

    test_fast: {
      run+: jt(["test", "fast"]),
    },

    lint: {
      downloads+: {
        JDT: { name: "ecj", version: "4.5.1", platformspecific: false },
      },
      packages+: {
        ruby: ">=2.1.0",
      },
      environment+: {
        # Truffle compiles with ECJ but does not run (GR-4720)
        TRUFFLERUBY_CEXT_ENABLED: "false",
      },
      run+: [
        # Build with ECJ to get warnings
        ["mx", "sversions"],
        ["mx", "build", "--jdt", "$JDT", "--warning-as-error"],
      ] + jt(["lint"]) + [
        ["mx", "findbugs"],
      ],
    },

    test_mri: { run+: jt(["test", "mri"]) },
    test_integration: { run+: jt(["test", "integration"]) },
    test_gems: { run+: jt(["test", "gems"]) },
    test_ecosystem: { run+: jt(["test", "ecosystem"]) },
    test_bundle: { run+: jt(["test", "bundle", "--no-sulong"]) },
    test_compiler: { run+: jt(["test", "compiler"]) },

    test_cexts: {
      is_after+:: ["$.use.common"],
      environment+: {
        # TODO why is this option applied?
        java_opts+:: ["-Dgraal.TruffleCompileOnly=nothing"],
      },
      run+: [
        ["mx", "--dynamicimports", "sulong", "ruby_testdownstream_sulong"],
      ],
    },

    # It tests that the locally-built Graal on Java 9 works fine.
    compiler_standalone: {
      is_after+:: ["$.jdk.labsjdk9"],
      run+: [
        [
          "bin/truffleruby",
          "-J-XX:+UnlockExperimentalVMOptions",
          "-J-XX:+EnableJVMCI",
          "-J--module-path=" + std.join(
            ":",
            [
              "../graal/sdk/mxbuild/modules/org.graalvm.graal_sdk.jar",
              "../graal/truffle/mxbuild/modules/com.oracle.truffle.truffle_api.jar",
            ]
          ),
          "-J--upgrade-module-path=../graal/compiler/mxbuild/modules/jdk.internal.vm.compiler.jar",
          "-e",
          "raise 'no graal' unless Truffle.graal?",
        ],
      ],
    },

    svm_gate: {
      local build = self,
      run+: [
        ["cd", "$SVM_HOME"],
        [
          "mx",
          "--strict-compliance",
          "gate",
          "-B--force-deprecation-as-warning",
          "--strict-mode",
          "--tags",
          build["$.run.svm_gate"].tags,
        ],
      ],
    },
  },

  benchmark: {
    local post_process = [
      ["tool/post-process-results-json.rb", "bench-results.json", "bench-results-processed.json"],
    ] + if debug then [["cat", "bench-results-processed.json"]] else [],
    local upload_results =
      [["bench-uploader.py", "bench-results-processed.json"]],
    local post_process_and_upload_results =
      post_process + upload_results +
      [["tool/fail-if-any-failed.rb", "bench-results-processed.json"]],
    local post_process_and_upload_results_wait =
      post_process + upload_results +
      [["tool/fail-if-any-failed.rb", "bench-results-processed.json", "--wait"]],
    local mx_benchmark = function(bench)
      [["mx", "benchmark"] + if std.type(bench) == "string" then [bench] else bench],

    local benchmark = function(benchs)
      if std.type(benchs) == "string" then
        error "benchs must be an array"
      else
        std.join(post_process_and_upload_results_wait, std.map(mx_benchmark, benchs)) +
        post_process_and_upload_results,

    runner: {
      local benchmarks_to_run = self.benchmarks,
      run+: std.join(
              post_process_and_upload_results_wait,
              std.map(mx_benchmark, benchmarks_to_run)
            ) +
            post_process_and_upload_results,
    },

    metrics: {
      benchmarks+:: ["allocation", "minheap", "time"],
    },

    compiler_metrics: {
      benchmarks+:: [
        "allocation:compile-mandelbrot",
        "minheap:compile-mandelbrot",
        "time:compile-mandelbrot",
      ],
    },

    classic: { benchmarks+:: ["classic"] },
    chunky: { benchmarks+:: ["chunky"] },
    psd: { benchmarks+:: ["psd"] },
    asciidoctor: { benchmarks+:: ["asciidoctor"] },
    other_extra: { benchmarks+:: ["savina", "micro"] },
    other: { benchmarks+:: ["image-demo", "optcarrot", "synthetic"] },

    server: {
      local build = self,
      packages+: {
        "apache/ab": ">=2.3",
      },
      benchmarks+:: [["server"] + build["$.benchmark.server"].options],
    },

    cext_chunky: {
      environment+: {
        TRUFFLERUBYOPT: "-Xcexts.log.load=true",
        USE_CEXTS: "true",
      },
      setup+:
        jt(["cextc", "bench/chunky_png/oily_png"]) + jt(["cextc", "bench/psd.rb/psd_native"]),
      benchmarks+:: ["chunky"],
    },

    svm_build_stats: { benchmarks+:: ["build-stats"] },

    # TODO not compose-able, it would had be broken up to 2 builds
    run_svm_metrics: {
      local run_benchs = benchmark([
        "instructions",
        ["time", "--", "--native"],
        "maxrss",
      ]),

      run: [
        ["export", "GUEST_VM_CONFIG=default"],
      ] + run_benchs + [
        ["export", "GUEST_VM_CONFIG=no-rubygems"],
        ["export", "TRUFFLERUBYOPT=--disable-gems"],
      ] + run_benchs,
    },

  },
};

# composition_environment inherits from part_definitions all building blocks
# (parts) can be addressed using jsonnet root syntax e.g. $.use.maven
local composition_environment = utils.add_inclusion_tracking(part_definitions, "$", false) {

  test_builds:
    {
      local ruby_deploy_and_spec = $.use.maven + $.jdk.labsjdk8 + $.use.common + $.use.build + $.cap.deploy +
                                   $.cap.gate + $.run.deploy_and_spec + { timelimit: "30:00" },

      "ruby-deploy-and-specs-linux": $.platform.linux + ruby_deploy_and_spec,
      "ruby-deploy-and-specs-darwin": $.platform.darwin + ruby_deploy_and_spec,
      "ruby-deploy-and-specs-solaris": $.platform.solaris + ruby_deploy_and_spec,
    } +

    {
      "ruby-test-fast-java9-linux": $.platform.linux + $.jdk.labsjdk9 + $.use.common + $.use.build + $.cap.gate +
                                    $.run.test_fast + { timelimit: "30:00" },
    } +

    {
      local linux_gate = $.platform.linux + $.cap.gate + $.use.maven + $.jdk.labsjdk8 + $.use.common +
                         { timelimit: "01:00:00" },

      "ruby-lint": linux_gate + $.run.lint + { timelimit: "30:00" },  # timilimit override
      "ruby-test-tck": linux_gate + $.use.build + { run+: [["mx", "rubytck"]] },
      "ruby-test-mri": $.cap.fast_cpu + linux_gate +
                       $.use.sulong +  # OpenSSL is required to run RubyGems tests
                       $.use.build + $.run.test_mri,
      "ruby-test-integration": linux_gate + $.use.sulong + $.use.build + $.run.test_integration,
      "ruby-test-cexts": linux_gate + $.use.sulong + $.use.build + $.use.gem_test_pack + $.run.test_cexts,
      "ruby-test-gems": linux_gate + $.use.build + $.use.gem_test_pack + $.run.test_gems,
      "ruby-test-ecosystem": linux_gate + $.use.sulong + $.use.build + $.use.gem_test_pack + $.run.test_ecosystem,

      "ruby-test-compiler-graal-core": linux_gate + $.use.build + $.use.truffleruby + $.graal.core +
                                       $.run.test_compiler,
      # TODO was commented out, needs to be rewritten?
      # {name: "ruby-test-compiler-graal-enterprise"} + linux_gate + $.graal_enterprise + {run: jt(["test", "compiler"])},
      # {name: "ruby-test-compiler-graal-vm-snapshot"} + linux_gate + $.graal_vm_snapshot + {run: jt(["test", "compiler"])},
    } +

    {
      local shared = $.platform.linux + $.cap.gate + $.jdk.labsjdk9 + $.use.common + $.use.build +
                     $.use.truffleruby + $.graal.core + { timelimit: "01:00:00" },

      "ruby-test-compiler-graal-core-java9": shared + $.run.test_compiler,
      "ruby-test-compiler-standalone-java9": shared + $.run.compiler_standalone,
    } +

    local svm_test_platforms = {
      local shared = $.use.maven + $.jdk.labsjdk8 + $.use.common + $.use.svm + $.cap.gate + $.run.svm_gate,

      linux: $.platform.linux + shared + { "$.run.svm_gate":: { tags: "build,ruby_debug,ruby_product" } },
      darwin: $.platform.darwin + shared + { "$.run.svm_gate":: { tags: "build,darwin_ruby" } },
    };
    {
      local shared = $.use.build + $.svm.core + { timelimit: "01:00:00" },
      local tag_override = { "$.run.svm_gate":: { tags: "build,ruby" } },

      "ruby-test-svm-graal-core-linux": shared + svm_test_platforms.linux + tag_override,
      "ruby-test-svm-graal-core-darwin": shared + svm_test_platforms.darwin + tag_override,
    } + {
      local shared = $.use.build + $.svm.enterprise + { timelimit: "01:00:00" },

      "ruby-test-svm-graal-enterprise-linux": shared + svm_test_platforms.linux,
      "ruby-test-svm-graal-enterprise-darwin": shared + svm_test_platforms.darwin,
    },

  local other_rubies = {
    mri: $.use.mri + $.cap.bench + $.cap.weekly,
    jruby: $.use.jruby + $.cap.bench + $.cap.weekly,
  },
  local graal_configurations = {
    local shared = $.use.truffleruby + $.use.build + $.cap.daily + $.cap.bench,
    # TODO was commented out, needs to be rewritten?
    # { name: "no-graal",               caps: $.weekly_bench_caps, setup: $.no_graal,               kind: "graal"  },
    # { name: "graal-vm-snapshot",      caps: $.bench_caps,        setup: $.graal_vm_snapshot,      kind: "graal" },

    "graal-core": shared + $.graal.core,
    "graal-enterprise": shared + $.graal.enterprise,
    "graal-enterprise-no-om": shared + $.graal.enterprise + $.graal.without_om,
  },
  local svm_configurations = {
    local shared = $.cap.bench + $.cap.daily + $.use.truffleruby + $.use.build + $.use.svm,

    "svm-graal-core": shared + $.svm.core + $.svm.build_image,
    "svm-graal-enterprise": shared + $.svm.enterprise + $.svm.build_image,
  },

  bench_builds:
    {
      local shared = $.platform.linux + $.use.maven + $.jdk.labsjdk8 + $.use.common +
                     $.benchmark.runner + $.benchmark.compiler_metrics + { timelimit: "00:50:00" },

      "ruby-metrics-compiler-graal-core": shared + graal_configurations["graal-core"],
      "ruby-metrics-compiler-graal-enterprise": shared + graal_configurations["graal-enterprise"],
      "ruby-metrics-compiler-graal-enterprise-no-om": shared + graal_configurations["graal-enterprise-no-om"],
    } +

    {
      local shared = $.platform.linux + $.use.maven + $.jdk.labsjdk8 + $.use.common +
                     $.benchmark.runner + $.benchmark.svm_build_stats + { timelimit: "02:00:00" },
      # TODO this 2 jobs have GUEST_VM_CONFIG: 'default' instead of 'truffle', why?
      local guest_vm_override = { environment+: { GUEST_VM_CONFIG: "default" } },

      "ruby-build-stats-svm-graal-core": shared + svm_configurations["svm-graal-core"] + guest_vm_override,
      "ruby-build-stats-svm-graal-enterprise": shared + svm_configurations["svm-graal-enterprise"] + guest_vm_override,
    } +

    {
      local shared = $.platform.linux + $.use.maven + $.jdk.labsjdk8 + $.use.common +
                     $.benchmark.run_svm_metrics + { timelimit: "00:30:00" },

      "ruby-metrics-svm-graal-core": shared + svm_configurations["svm-graal-core"] + $.cap.x52_18_override,
      "ruby-metrics-svm-graal-enterprise": shared + svm_configurations["svm-graal-enterprise"] + $.cap.x52_18_override,
    } +

    {
      local shared = $.platform.linux + $.use.maven + $.jdk.labsjdk8 + $.use.common +
                     $.benchmark.runner + $.benchmark.classic,

      "ruby-benchmarks-classic-mri": shared + other_rubies.mri + { timelimit: "00:35:00" },
      "ruby-benchmarks-classic-jruby": shared + other_rubies.jruby + { timelimit: "00:35:00" },
      "ruby-benchmarks-classic-graal-core": shared + graal_configurations["graal-core"] + { timelimit: "00:35:00" },
      "ruby-benchmarks-classic-graal-enterprise": shared + graal_configurations["graal-enterprise"] + { timelimit: "00:35:00" },
      "ruby-benchmarks-classic-graal-enterprise-no-om": shared + graal_configurations["graal-enterprise-no-om"] + { timelimit: "00:35:00" },
      "ruby-benchmarks-classic-svm-graal-core": shared + svm_configurations["svm-graal-core"] + { timelimit: "01:10:00" },
      "ruby-benchmarks-classic-svm-graal-enterprise": shared + svm_configurations["svm-graal-enterprise"] + { timelimit: "01:10:00" },
    } +

    {
      local shared = $.platform.linux + $.use.maven + $.jdk.labsjdk8 + $.use.common,

      local chunky = $.benchmark.runner + $.benchmark.chunky + { timelimit: "01:00:00" },
      "ruby-benchmarks-chunky-mri": shared + chunky + other_rubies.mri,
      "ruby-benchmarks-chunky-jruby": shared + chunky + other_rubies.jruby,
      "ruby-benchmarks-chunky-graal-core": shared + chunky + $.use.sulong + graal_configurations["graal-core"],
      "ruby-benchmarks-chunky-graal-enterprise": shared + chunky + $.use.sulong + graal_configurations["graal-enterprise"],
      "ruby-benchmarks-chunky-graal-enterprise-no-om": shared + chunky + $.use.sulong + graal_configurations["graal-enterprise-no-om"],
      local psd = $.benchmark.runner + $.benchmark.psd + { timelimit: "02:00:00" },
      "ruby-benchmarks-psd-mri": shared + psd + other_rubies.mri,
      "ruby-benchmarks-psd-jruby": shared + psd + other_rubies.jruby,
      "ruby-benchmarks-psd-graal-core": shared + psd + graal_configurations["graal-core"],
      "ruby-benchmarks-psd-graal-enterprise": shared + psd + graal_configurations["graal-enterprise"],
      "ruby-benchmarks-psd-graal-enterprise-no-om": shared + psd + graal_configurations["graal-enterprise-no-om"],
      "ruby-benchmarks-psd-svm-graal-core": shared + psd + svm_configurations["svm-graal-core"],
      "ruby-benchmarks-psd-svm-graal-enterprise": shared + psd + svm_configurations["svm-graal-enterprise"],
      local asciidoctor = $.benchmark.runner + $.benchmark.asciidoctor + { timelimit: "00:55:00" },
      "ruby-benchmarks-asciidoctor-mri": shared + asciidoctor + other_rubies.mri,
      "ruby-benchmarks-asciidoctor-jruby": shared + asciidoctor + other_rubies.jruby,
      "ruby-benchmarks-asciidoctor-graal-core": shared + asciidoctor + graal_configurations["graal-core"],
      "ruby-benchmarks-asciidoctor-graal-enterprise": shared + asciidoctor + graal_configurations["graal-enterprise"],
      "ruby-benchmarks-asciidoctor-graal-enterprise-no-om": shared + asciidoctor + graal_configurations["graal-enterprise-no-om"],
      "ruby-benchmarks-asciidoctor-svm-graal-core": shared + asciidoctor + svm_configurations["svm-graal-core"],
      "ruby-benchmarks-asciidoctor-svm-graal-enterprise": shared + asciidoctor + svm_configurations["svm-graal-enterprise"],
      local other = $.benchmark.runner + $.benchmark.other + $.benchmark.other_extra + { timelimit: "00:40:00" },
      local svm_other = $.benchmark.runner + $.benchmark.other + { timelimit: "01:00:00" },
      "ruby-benchmarks-other-mri": shared + other + other_rubies.mri,
      "ruby-benchmarks-other-jruby": shared + other + other_rubies.jruby,
      "ruby-benchmarks-other-graal-core": shared + other + graal_configurations["graal-core"],
      "ruby-benchmarks-other-graal-enterprise": shared + other + graal_configurations["graal-enterprise"],
      "ruby-benchmarks-other-graal-enterprise-no-om": shared + other + graal_configurations["graal-enterprise-no-om"],
      "ruby-benchmarks-other-svm-graal-core": shared + svm_other + svm_configurations["svm-graal-core"],
      "ruby-benchmarks-other-svm-graal-enterprise": shared + svm_other + svm_configurations["svm-graal-enterprise"],
    } +

    {
      local shared = $.platform.linux + $.use.maven + $.jdk.labsjdk8 + $.use.common +
                     $.benchmark.runner + $.benchmark.server +
                     { timelimit: "00:20:00" },

      "ruby-benchmarks-server-mri": shared + other_rubies.mri,
      "ruby-benchmarks-server-jruby": shared + other_rubies.jruby,
      "ruby-benchmarks-server-graal-core": shared + graal_configurations["graal-core"],
      "ruby-benchmarks-server-graal-enterprise": shared + graal_configurations["graal-enterprise"],
      "ruby-benchmarks-server-graal-enterprise-no-om": shared + graal_configurations["graal-enterprise-no-om"],
    } +

    {
      "ruby-metrics-truffle":
        $.platform.linux + $.use.maven + $.jdk.labsjdk8 + $.use.common + $.use.build +
        $.use.truffleruby + $.graal.none +
        $.cap.bench + $.cap.daily +
        $.benchmark.runner + $.benchmark.metrics +
        { timelimit: "00:25:00" },
    } +

    {
      "ruby-benchmarks-cext":
        $.platform.linux + $.use.maven + $.jdk.labsjdk8 + $.use.common +
        $.use.truffleruby + $.use.truffleruby_cexts +
        $.use.build + $.use.sulong + $.graal.core + $.use.gem_test_pack +
        $.cap.bench + $.cap.daily +
        $.benchmark.runner + $.benchmark.cext_chunky +
        { timelimit: "02:00:00" },
    } +

    local solaris_benchmarks = {
      local shared = $.platform.solaris + $.use.maven + $.jdk.labsjdk8 + $.use.common + $.use.build +
                     $.use.truffleruby + $.cap.bench + $.cap.daily,

      "graal-core-solaris": shared + $.graal.core,
      "graal-enterprise-solaris": shared + $.graal.enterprise,
    };
    {
      local shared = $.benchmark.runner + $.benchmark.classic + { timelimit: "01:10:00" },

      "ruby-benchmarks-classic-graal-core-solaris": shared + solaris_benchmarks["graal-core-solaris"],
      "ruby-benchmarks-classic-graal-enterprise-solaris": shared + solaris_benchmarks["graal-enterprise-solaris"],
    },

  builds:
    local all_builds = $.test_builds + $.bench_builds;
    utils.check_builds(
      restrict_builds_to,
      # Move name inside into `name` field
      # and ensure timelimit is present
      [
        all_builds[k] {
          name: k,
          timelimit: if std.objectHas(all_builds[k], "timelimit")
          then all_builds[k].timelimit
          else error "Missing timelimit in " + k + " build.",
        }
        for k in std.objectFields(all_builds)
      ]
    ),
};

{
  local no_overlay = "6f4eafb4da3b14be3593b07ed562d12caad9b64b",
  overlay: if use_overlay then overlay else no_overlay,

  builds: composition_environment.builds,
}

/**

# Additional notes

## Structure

- All builds are composed from disjunct parts.
- Part is just an jsonnet object which includes a part of a build declaration
  so fields like setup, run, etc.
- All parts are defined in part_definitions in groups like
  platform, graal, etc.
  - They are always nested one level deep therefore
    $.<group_name>.<part_name>
- Each part is included in a build at most once. A helper function is
  checking it and will raise an error otherwise with a message
  `Parts ["$.use.maven", "$.use.maven"] are used more than once in build: ruby-metrics-svm-graal-core. ...`
- All parts have to be disjoint and distinct.
- All parts have to be compose-able with each other. Therefore all fields
  like setup or run should end with '+' to avoid overriding.
  - An example of nested compose-able field (e.g. PATH in an environment)
    can be found in `$.use.common.environment`, look for `path::` and `PATH:`

### Exceptions

- In few cases values in a part (A) depend on values provided in another
  part (O), therefore (A) needs (O) to also be used in a build.
  - The inter part dependencies should be kept to minimum.
  - Use name of the part (A) for the field in (O).
  - See `$.platform` parts for an example.
  - If (A) is used without (O) an error is risen that a field
    (e.g. '$.run.deploy_and_spec') is missing which makes it easy to look up
    which inter dependency is broken.
- Few parts depend on othering with other parts, in this case
  they have `is_after+:: ['$.a_group.a_name']` (or `is_before`) which ensures
  the required part are included in correct order.
  See $.use.truffleruby_cexts

## How to edit

- The composition of builds is intentionally kept very flat.
- All parts included in the build are listed where the build is being defined.
- Since parts do not inherit from each other or use each others' fields
  there is no need to track down what is composed of what.
- Each manifested build has included_parts field with names of all
  the included parts, which makes it clear what the build is
  using and doing.
  - The included parts is also stored in environment variable
    PARTS_INCLUDED_IN_BUILD therefore printed in each build log.
- The structure rules exist to simplify thinking about the build, to find
  out the composition of any build one needs to only investigate all named
  parts in the definition, where the name tells its placement in
  part_definitions object. Nothing else is in the build.
- When a part is edited, it can be easily looked up where it's used just by
  using its full name (e.g. $.run.deploy_and_spec). It's used nowhere else.

 */

