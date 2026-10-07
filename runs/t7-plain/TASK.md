# Arithmetic expressions, and a stack machine that runs them

Two pure namespaces. `expr.core` evaluates and simplifies arithmetic
expressions; `stack.core` compiles them to a small stack machine and runs
it, using `expr.core` where it needs to.

## expr.core

An expression is one of

    [:num n]       n an Int
    [:var k]       k a Keyword
    [:add a b]     a and b expressions
    [:mul a b]
    [:neg a]

- `(evaluate e env)` is the Int value of `e`, where `env` maps keywords to
  Ints; a variable `env` does not have is 0.
- `(simplify e)` simplifies `e` bottom-up: it simplifies the parts of a
  node first, then applies the first of these rules that matches the node
  with its simplified parts, once:
  1. `[:add [:num a] [:num b]]` is `[:num (+ a b)]`; `[:mul [:num a] [:num
     b]]` is `[:num (* a b)]`; `[:neg [:num a]]` is `[:num (- a)]`.
  2. `[:add [:num 0] x]` and `[:add x [:num 0]]` are `x`.
  3. `[:mul [:num 1] x]` and `[:mul x [:num 1]]` are `x`.
  4. `[:mul [:num 0] x]` and `[:mul x [:num 0]]` are `[:num 0]`.
  5. `[:neg [:neg x]]` is `x`.
  A node no rule matches stays as it is (with its simplified parts).
  `simplify` never changes an expression's value under any `env`.
- `(size e)` is the number of nodes in `e` (`[:num 3]` has 1, `[:add [:num
  1] [:var :x]]` has 3).
- `(variables e)` is the vector of the distinct keywords `e` mentions,
  sorted.

## stack.core

A program is a vector of instructions: `[:push n]`, `[:load k]`, `[:add]`,
`[:mul]`, `[:neg]`.

- `(compile-expr e)` is the program for `e`, in postorder: a `[:num n]` is
  `[[:push n]]`, a `[:var k]` is `[[:load k]]`, `[:add a b]` is the program
  for `a`, then `b`'s, then `[:add]` (likewise `:mul`), and `[:neg a]` is
  `a`'s program then `[:neg]`.
- `(run prog env)` runs a program on an empty stack: `:push` pushes `n`,
  `:load` pushes `k`'s value in `env` (0 when absent), `:add`/`:mul` pop
  two values and push their sum/product, `:neg` pops one and pushes its
  negation. Its result is the one value left on the stack at the end. It
  is nil when an instruction finds too few values on the stack, or when
  the program ends with other than exactly one value on the stack.
- `(compile-simplified e)` is `(compile-expr (expr.core/simplify e))`.
- For every expression `e` and `env`, `(run (compile-expr e) env)` is
  `(expr.core/evaluate e env)`.
