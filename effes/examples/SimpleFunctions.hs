-- How should a simple function look?

SomeClass:
  -- zero-arity
  func01 = Int :: () -> 5     -- looks a bit icky
  func02 :: Int = () -> 5     -- inaccurate. func02's type is () -> Int, not Int
  func03 :: (Int) = () -> 5   -- (Int) as shorthand for "a function that returns Int". Icky.
  func04 :: -> Int = () -> 5  -- "a function that returns Int", but looks redundant with the ->
  func05 = () :: Int -> 5     -- confusing
  func06 = () -> Int 5        -- " -> Int" means it returns Int (the "::" is unnecessary)
                              -- This also mirrors the type of () -> Int
  func07 () -> Int 5          -- Same but without the "=". Doesn't feel right.
  func08 = () -> Int: 5       -- Best so far
  func09 () -> Int: 5         -- Looks awkward, but more suggestive of possible overloading
  func10 -> Int: 5            -- parens are optional
  func11 (): Int -> 5

  -- one-arity
  doubler01 = Int :: (i :: Int) -> i * 2
  doubler02 :: Int = (i :: Int) -> i * 2
  doubler03 :: (Int) = (i :: Int) -> i * 2
  doubler04 :: -> Int = (i :: Int) -> i * 2
  doubler05 = (i :: Int) :: Int -> i * 2
  doubler06 = (i :: Int) -> Int: i * 2 -- Mirrors (Int) -> Int

  doubler07 (i: Int) -> Int: i * 2
  doubler08 (i: Int): Int -> i * 2
  doubler09 (i: Int): Int = i * 2

  -- two-arity, "long" functions
  repeat06 = (s :: String, i :: Int) -> String:
    i = Mutable i
    r = Mutable ""
    while i > 0:
      r appends s
      i -= 1
    r

