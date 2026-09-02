# Interface Exception Divergence Companion

Flags a call through an interface-typed reference, wrapped in a broad
silent `catch`, where real implementations diverge on which
`RuntimeException` they throw directly.

## Why it exists

A real Liskov Substitution violation: a caller written assuming one
implementation's behavior ("PayPalGateway never throws this") silently
breaks when dependency injection hands it a different real
implementation at runtime. No PMD/SonarQube/CodeQL rule found with this
exact angle (PMD's `MissingOverride` is about a missing `@Override`
annotation, a different concern); no dedicated Marketplace plugin
found.

## Why built this way

- **Real Class Hierarchy Analysis (CHA)** -- resolves EVERY concrete
  implementation of the interface in the project via
  `ClassInheritorsSearch`, the same mechanism a compiler/JIT uses to
  decide whether a virtual call can be devirtualized.
- **The opposite of every other whole-project mechanism in this
  catalog** -- `deadlock-lock-order-companion` v0.3 and
  `kafka-topic-feedback-loop-companion` both DISCARD a call site the
  moment more than one real implementation exists ("ambiguity never
  guessed"). Here, 2+ real implementations is the PRECONDITION: the
  whole point is comparing their actual behavior against each other.
- A lambda/anonymous class body nested inside an override is never
  descended into -- a separate execution context, not really "this
  method throwing" in the same direct sense.

## v0.1 scope — stated honestly, not exhaustively

- Only `RuntimeException` subtypes thrown DIRECTLY
  (`throw new X(...)`) in an implementation's own override body --
  never follows a call to another method to infer transitive
  exceptions (that's `exception-escape-chain-companion`'s own,
  separate, already-published mechanism).
- Only interfaces with 2-10 real implementations in the project (more
  is a safety valve against a pathologically over-implemented
  interface).
- The catch type must be exactly `Exception` or `RuntimeException` (a
  genuinely broad catch) -- a narrower catch of a specific exception
  type is a different, more deliberate case, out of scope.

## Usage

Open a Java file with an interface that has 2+ real implementations
diverging on a thrown `RuntimeException`, called through the interface
type inside a broad silent `catch` -- the call site shows a warning.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us at
**gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
