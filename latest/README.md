# CI report — run #1

- commit: `4341f98ca387e3925a76c6fe10441c5440b59113` (main, event: push)
- run: https://github.com/lejiyo09/topsecret/actions/runs/40605916654
- app version: 1.0.0 · release signing: ephemeral

| job | result |
|---|---|
| build (compile, unit tests, lint, APKs) | failure |
| device-test (emulator API 30 + 34) | skipped |
| publish (GitHub Release) | skipped  |

## JVM unit tests
- no unit test results found (build did not reach the test task)

## Android Lint (debug)
- no lint report found

## Release APK
```
(not built)
```

## Build errors
```
e: The daemon has terminated unexpectedly on startup attempt #1 with error code: 0. The daemon process output:
    1. Kotlin compile daemon is ready
e: The daemon has terminated unexpectedly on startup attempt #2 with error code: 0. The daemon process output:
    1. Kotlin compile daemon is ready
Problems may have occurred during auto-selection of GC. The preferred GC is Parallel GC.
If the problems persist, try adding the JVM option to the Kotlin daemon JVM arguments: -XX:-UseParallelGC.
GC auto-selection logic is disabled temporary for the next daemon startup.
--
> Task :app:compileDebugKotlin FAILED
e: file:///home/runner/work/topsecret/topsecret/app/src/main/java/io/github/lejiyo09/independentbrowser/MainActivity.kt:453:89 'return' is prohibited here.

> Task :app:compileReleaseKotlin
e: file:///home/runner/work/topsecret/topsecret/app/src/main/java/io/github/lejiyo09/independentbrowser/MainActivity.kt:453:89 'return' is prohibited here.

> Task :app:compileReleaseKotlin FAILED
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/gradle-1793145643280.json

[Incubating] Problems report is available at: file:///home/runner/work/topsecret/topsecret/build/reports/problems/problems-report.html

--
* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

--
* What went wrong:
Execution failed for task ':app:compileReleaseKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

--
BUILD FAILED in 2m 5s
85 actionable tasks: 76 executed, 9 from cache
```
