# Code style

lwjwae follows [Google Java Style](https://google.github.io/styleguide/javaguide.html) for code and
the [Google developer documentation style guide](https://developers.google.com/style) for the text
of Javadoc. Every deviation from Google Java Style is listed in this document; anything that isn't
listed here follows the guide.

Formatting is enforced: `spotlessCheck` fails the build when a file differs from what the formatter
would write, and `spotlessApply` fixes it in one run. Everything else is reported: Checkstyle
prints its findings as warnings, as Google's own configuration does, and the build passes. A warning
is still a defect to fix before a change is merged; the difference is that it doesn't block a local
run halfway through. This document explains what each tool does, which rules the project adds on
top of Google, and how to get a file into shape.

## Tools

Three tools share the work. Each owns one concern, and none of them overlaps with another.

| Tool                                                                                        | Owns                                                   | Changes code | Task                                                         |
|---------------------------------------------------------------------------------------------|--------------------------------------------------------|--------------|--------------------------------------------------------------|
| [google-java-format](https://github.com/google/google-java-format) 1.33.0, through Spotless | Layout: indentation, wrapping, imports, Javadoc reflow | Yes          | `spotlessApply` to format, `spotlessCheck` to verify         |
| [Checkstyle](https://checkstyle.org/) 11.1.0 with `google_checks.xml`                       | Naming, braces, Javadoc presence, wording              | No           | `checkstyleMain`, `checkstyleTest`, `checkstyleTestFixtures` |
| `javadoc` with `-Xdoclint:all,-missing`                                                     | Broken `{@link}` targets, malformed HTML, unknown tags | No           | `javadoc`, part of `assemble`                                |

The configuration lives in one place, the root [`build.gradle.kts`](../build.gradle.kts), under
`subprojects { plugins.withId(...) }`. A module opts in by listing the plugins in its own
`plugins {}` block and configures nothing else.

[`.editorconfig`](../.editorconfig) carries the same values (two-space indentation, 100 columns,
LF, IntelliJ import settings) so that an editor doesn't fight the formatter.

### Formatting: Spotless

Spotless runs two steps in order on every `.java` file:

1. **`googleJavaFormat("1.33.0").reflowLongStrings()`** rewrites the whole file: two-space
   indentation, 100 columns, one ASCII-sorted import block, no unused imports, Javadoc wrapped at
   100 columns with `<p>` at the start of each paragraph. `reflowLongStrings` splits a string
   literal that doesn't fit.
2. **`formatAnnotations()`** keeps a type annotation on the line of its declaration and puts every
   other annotation on a line of its own.
Run the formatter before you run anything else:

```bash
./gradlew spotlessApply
```

`spotlessCheck` compares instead of writing, and prints the diff of every file that differs.

### Rules: Checkstyle

[`config/checkstyle/checkstyle.xml`](../config/checkstyle/checkstyle.xml) is the
`google_checks.xml` that ships with Checkstyle 11.1.0, with one change to the base and a block of
project additions at the end of each section. The change to the base:

- The name patterns for parameters, lambda parameters, catch parameters, local variables, and
  pattern variables accept `_`, the unnamed variable of Java 22. The upstream patterns predate it.

The additions are the subject of the [next section](#rules-beyond-google-java-style).

Suppressions live in two files:

- [`suppressions.xml`](../config/checkstyle/suppressions.xml), by file: no Javadoc in tests, no
  checks on the generated `lombok.config`, and Unicode escapes allowed in the files that handle the
  bridge separator and the JavaScript line separators.
- [`suppressions-xpath.xml`](../config/checkstyle/suppressions-xpath.xml), by syntax tree: the
  members of a `@UtilityClass` are static after Lombok runs, so `RequireThis` and the constant
  naming checks don't apply to them; a record's compact constructor and an exception constructor
  that only forwards its message need no Javadoc.

Inline suppression is available but unused so far: `// CHECKSTYLE.OFF: CheckName` and
`// CHECKSTYLE.ON: CheckName` around a region, or `// CHECKSTYLE.SUPPRESS: CheckName` on the line
before. Prefer a change to the code. If the rule is wrong for a whole class of cases, change the
suppression file with a comment that says why.

The HTML report of a failed run is at `<module>/build/reports/checkstyle/<sourceSet>.html`.

## Rules beyond Google Java Style

Google Java Style allows everything in this section. The project restricts it.

### `this.` on every instance member

Access to an instance field or method is qualified with `this.`: `this.repository.persist(...)`,
`this.validate(request)`, `this::handle`. A static member is never qualified with `this.`; call it
bare in its own class and by class name from elsewhere.

Checkstyle `RequireThis` with `checkFields`, `checkMethods`, and `validateOnlyOverlapping=false`.
Members of a `@UtilityClass` are exempt (they're static).

Google Java Style doesn't regulate it. Google's own code omits `this.`.

### No static imports

`Assertions.assertEquals(...)`, not `assertEquals(...)`. A reader sees where a name comes from
without scrolling to the imports.

Checkstyle `AvoidStaticImport`.

Google Java Style §3.3.1 allows static imports; `import static ...assertThat` is normal in Google
tests.

### Javadoc on every public type

A public class, interface, enum, record, or annotation has a Javadoc block. Tests are exempt: their
method names document them.

Checkstyle `MissingJavadocType` with `scope=public`, on top of the upstream instance that covers
`protected`.

### `@SuppressWarnings` is documented

An `@SuppressWarnings` sits directly under the closing `*/` of a Javadoc block, and that block has a
paragraph that starts with `Suppressed warnings:` and names each suppressed warning with the reason
it's wrong at this site:

```java
/**
 * Handles the {@code destroy} signal of the window.
 *
 * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
 * binds it by name, so no Java code calls it and the compiler sees a dead private method.
 */
@SuppressWarnings("unused")
private void onDestroy(MemorySegment widget, MemorySegment data) {
```

Checkstyle `RegexpMultiline` on the line before the annotation. Google Java Style §4.8.6.1 asks for
a comment when the reason isn't obvious; the project asks always, and in the Javadoc rather than a
line comment, so that the reason is part of the API documentation.

### Unused parameters are `_`

An unused lambda parameter, catch parameter, or pattern variable is the unnamed variable `_`:
`(_, t) -> reported.add(t)`, `catch (IOException _)`, `case AddressLayout _ ->`. Never `ignored`,
`unused`, or a real name that the body doesn't read.

Two Checkstyle `RegexpSingleline` rules catch `ignored` and `unused`; the widened name patterns let
`_` through. Google Java Style predates the unnamed variable and says nothing.

### Wording of Javadoc

The text of a Javadoc block follows the Google developer documentation style guide. Checkstyle
enforces the mechanical part with `RegexpSingleline` rules that match only inside a Javadoc block:

| Rejected                                                                         | Use instead                                |
|----------------------------------------------------------------------------------|--------------------------------------------|
| `e.g.`, `i.e.`, `etc.`, `vs.`, `N.B.`                                            | for example, that is, and so on, versus    |
| simply, easily, just, basically, actually, utilize, leverage, please, via        | drop the word; use, through                |
| in order to, prior to, note that, allows you to, enables you to                  | to, before, a Note paragraph, lets you     |
| behaviour, colour, initialise, realise, cancelled, cancelling, catalogue, centre | American spelling                          |
| `@param x the ...`, `@return the ...`                                            | A description starts with a capital letter |
| `@throws X when ...`                                                             | `@throws X If ...`                         |

The rest of the guide isn't mechanical and is a matter of review: second person, present tense,
active voice, one idea per sentence, and a summary sentence that says what the thing does rather
than restating its name. The class-level block explains why: the design decision, the invariant,
the trade-off. It never restates what the code says.

### Comments in code

An in-body comment is rare and explains why, never what. A comment that restates the code, the
purpose of a dependency, or the description of a Gradle task is noise. A comment marker may group
fields (`// --- shapes ---`); it never groups methods.

`// TODO` is written in capitals with a colon: `// TODO: ...` (Checkstyle `TodoComment`).

## Where the project relaxes Google Java Style

- The upstream suppression file is optional and absent; the project has two, listed above. Each
  entry has a comment with the reason.
- `IllegalTokenText`, which rejects a Unicode escape of a printable character, is off for
  `BridgeProtocol`, `ScriptUtil`, and their tests. They escape U+001F, U+2028, and U+2029, which
  aren't printable, and the escape is the readable form. The upstream rule can't tell the
  difference, and the files are few.

## What isn't in Google Java Style at all

- **Build files.** Kotlin DSL, four-space indentation, 120 columns. No `apply(plugin = ...)`; every
  plugin goes through the `plugins {}` block, with `apply(false)` at the root when a version is
  declared but not applied. No `buildSrc`, no extra build modules: shared configuration lives in the
  root `subprojects` block.
- **Lombok.** `@UtilityClass` for a class of static members, `@RequiredArgsConstructor` for
  injected finals, `@SneakyThrows` around a `MethodHandle.invokeExact` whose `Throwable` can't
  happen, `@Builder` on parameter records. Checkstyle sees the source before Lombok runs, hence the
  XPath suppressions for `@UtilityClass`.
- **`AutoCloseable`.** Every one is opened in try-with-resources: `Arena`, streams, servers,
  executors, the library's own backends. Never a bare `close()` or a `finally { close(); }`.
- **Method references.** A lambda that only forwards its arguments is a method reference:
  `map(Provider::name)`, `forEach(found::add)`, `new Thread(this::loop)`. A lambda that forwards
  nothing is `Function.identity()`.
- **Structure.** One top-level type per file. A nested type only inside a record, and only when it
  exists for that record; everything else gets its own file. A custom exception is unchecked, ends
  in `Exception`, and lives in an `exception` package. Native binding goes through a `foreign`
  package only, so that every downcall and upcall stays enumerable for the reachability metadata
  that `native-image` needs. None of this is checked by a tool; it's a matter of review.
- **Markdown.** 100 columns, no trailing-whitespace trimming (a hard line break is two trailing
  spaces).

## Workflow

```bash
./gradlew spotlessApply   # Format
./gradlew check           # Everything: style, unit tests, display tests
```

`check` runs `spotlessCheck`, the three Checkstyle tasks, compilation, `test`, and `displayTest`, in
that order. Only `spotlessCheck` can fail on style; Checkstyle reports and moves on. The CI `style`
job on one Linux runner runs the same tasks before the platform matrix, so an unformatted file
stops the build there, and the Checkstyle warnings appear in its log and in the HTML report it
uploads.

A Checkstyle warning names the file, the line, and the rule. A Spotless failure prints the diff.
When the two disagree, that is, the formatter produces something that Checkstyle rejects, the
Checkstyle rule is wrong: file it rather than working around it.

To run the style checks in the environment of the Linux CI job:

```bash
scripts/linux/test-in-docker.sh spotlessCheck checkstyleMain checkstyleTest checkstyleTestFixtures
```

## Changing a rule

1. Change [`checkstyle.xml`](../config/checkstyle/checkstyle.xml), a suppression file, or the
   Spotless block in [`build.gradle.kts`](../build.gradle.kts).
2. Run `./gradlew spotlessApply check` and fix what the new rule reports.
3. Update this document. A rule that isn't described here doesn't exist as far as a contributor is
   concerned.
