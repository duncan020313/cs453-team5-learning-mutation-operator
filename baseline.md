# PIT Mutation Operators

## Baseline

- This repository uses `org.pitest:pitest:1.22.1`.
- The experiment code in this repository runs two variants.
  - default variant: no `--mutators` option is passed
  - full variant: `--mutators ALL`
- Therefore, "full mutation operator" here refers to PIT's `ALL` mutator group. It is different from PIT's `fullMutationMatrix` report option.

## Default mutation operators

The default variant in this repository omits `--mutators`, so it uses the Gregor default mutator set from PIT 1.22.1.

| Mutator ID | Description |
| --- | --- |
| `CONDITIONALS_BOUNDARY` | Changes boundary conditions. For example, changes `<` to `<=` or `>=` to `>` |
| `EMPTY_RETURNS` | Changes object return values such as strings, collections, and Optionals to empty values |
| `FALSE_RETURNS` | Changes `boolean` or boxed Boolean return values to `false` |
| `INCREMENTS` | Reverses increment/decrement operations on local variables |
| `INVERT_NEGS` | Removes or applies numeric negation in the opposite direction |
| `MATH` | Changes arithmetic/bitwise operators to other operators. For example, changes `+` to `-` |
| `NEGATE_CONDITIONALS` | Negates conditional expressions. For example, changes `==` to `!=` or `<` to `>=` |
| `NULL_RETURNS` | Changes object return values to `null` |
| `PRIMITIVE_RETURNS` | Changes numeric primitive return values to `0` |
| `TRUE_RETURNS` | Changes `boolean` or boxed Boolean return values to `true` |
| `VOID_METHOD_CALLS` | Removes calls to `void` methods |

## Full mutation operators

The full variant uses `--mutators ALL`, so every built-in mutator available on the PIT 1.22.1 classpath is enabled. The list below also includes the default mutators.

| Mutator ID | Description |
| --- | --- |
| `CONDITIONALS_BOUNDARY` | Changes boundary conditions |
| `CONSTRUCTOR_CALLS` | Changes constructor call results to `null` |
| `EMPTY_RETURNS` | Changes object return values to empty values |
| `EXPERIMENTAL_ARGUMENT_PROPAGATION` | Replaces a method call result with an argument of the same type |
| `EXPERIMENTAL_BIG_DECIMAL` | Changes `BigDecimal` operation methods to different operations |
| `EXPERIMENTAL_BIG_INTEGER` | Changes `BigInteger` operation methods to different operations |
| `EXPERIMENTAL_MEMBER_VARIABLE` | Removes assignments to member variables, leaving their default values |
| `EXPERIMENTAL_NAKED_RECEIVER` | Replaces a method call with the receiver object itself |
| `EXPERIMENTAL_REMOVE_SWITCH_MUTATOR_[0-99]` | Experimental mutator group that removes switch case mappings or replaces them with other cases |
| `EXPERIMENTAL_SWITCH` | Swaps the default label and other labels in switch statements |
| `FALSE_RETURNS` | Changes boolean return values to `false` |
| `INCREMENTS` | Changes local variable increments/decrements |
| `INLINE_CONSTS` | Changes constants written directly in code to other values |
| `INVERT_NEGS` | Changes numeric negation |
| `MATH` | Changes arithmetic/bitwise operators |
| `NEGATE_CONDITIONALS` | Negates conditional expressions |
| `NON_VOID_METHOD_CALLS` | Removes non-void method calls and replaces them with type default values |
| `NULL_RETURNS` | Changes object return values to `null` |
| `PRIMITIVE_RETURNS` | Changes numeric primitive return values to `0` |
| `REMOVE_CONDITIONALS_EQUAL_ELSE` | Removes equality conditions so the else branch is executed |
| `REMOVE_CONDITIONALS_EQUAL_IF` | Removes equality conditions so the if branch is executed |
| `REMOVE_CONDITIONALS_ORDER_ELSE` | Removes order conditions so the else branch is executed |
| `REMOVE_CONDITIONALS_ORDER_IF` | Removes order conditions so the if branch is executed |
| `REMOVE_INCREMENTS` | Removes local variable increment/decrement operations |
| `TRUE_RETURNS` | Changes boolean return values to `true` |
| `VOID_METHOD_CALLS` | Removes calls to `void` methods |

## Note: Explicit `DEFAULTS` Group

In PIT 1.22.1, the default behavior when `--mutators` is omitted is not exactly the same as explicitly passing `--mutators DEFAULTS`. The default variant in this repository omits the option, so it follows the "Default mutation operators" list above.

When `--mutators DEFAULTS` is passed explicitly, the following two remove-conditionals mutators are included instead of `NEGATE_CONDITIONALS`.

- `REMOVE_CONDITIONALS_EQUAL_ELSE`
- `REMOVE_CONDITIONALS_ORDER_ELSE`

## References

- Official PIT mutation operators documentation: <https://pitest.org/quickstart/mutators/>
- Mutator group explanation in the PIT FAQ: <https://pitest.org/faq/>
- Local verification basis: checked the expansion results of `Mutator.newDefaults()` and `Mutator.fromStrings(List.of("ALL"))` from `org.pitest:pitest:1.22.1` in the Gradle cache
