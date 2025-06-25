# Kotlin Compiler Plugin for Exposed SQL

This is a compiler plugin for the Kotlin compiler that generates extension functions for the [Exposed SQL library](https://github.com/JetBrains/Exposed) to convert database rows to data classes.

## Features

- Automatically generates extension functions to convert `ResultRow` objects to data classes
- Generates extension functions to convert `Iterable<ResultRow>` to `Iterable<YourDataClass>`
- Performs compile-time validation to ensure:
  - Annotated classes are data classes
  - Data class properties match table columns
  - Table classes extend the Exposed `Table` class

## Details

This project has three modules:
- The [`:compiler-plugin`](compiler-plugin/src) module contains the compiler plugin itself.
- The [`:plugin-annotations`](plugin-annotations/src/main/kotlin) module contains annotations which can be used in
user code for interacting with the compiler plugin.
- The [`:gradle-plugin`](gradle-plugin/src) module contains a simple Gradle plugin to add the compiler plugin and
annotation dependency to a Kotlin project. 

Extension point registration:
- K2 Frontend (FIR) extensions can be registered in `SimplePluginRegistrar`.
- All other extensions (including K1 frontend and backend) can be registered in `SimplePluginComponentRegistrar`.

## Usage

1. Apply the Gradle plugin to your project
2. Annotate your data classes with `@Transformer(YourTable::class)`
3. Use the generated extension functions: `resultRow.toYourDataClass()` and `resultRows.toYourDataClasss()`

## Tests

The [Kotlin compiler test framework][test-framework] is set up for this project.
To create a new test, add a new `.kt` file in a [compiler-plugin/testData](compiler-plugin/testData) sub-directory:
`testData/box` for codegen tests and `testData/diagnostics` for diagnostics tests.
The generated JUnit 5 test classes will be updated automatically when tests are next run.
They can be manually updated with the `generateTests` Gradle task as well.
To aid in running tests, it is recommended to install the [Kotlin Compiler DevKit][test-plugin] IntelliJ plugin,
which is pre-configured in this repository.

[//]: # (Links)

[test-framework]: https://github.com/JetBrains/kotlin/blob/2.1.20/compiler/test-infrastructure/ReadMe.md
[test-plugin]: https://github.com/JetBrains/kotlin-compiler-devkit
