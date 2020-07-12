type Sizeable:
  size: Int
  isEmpty -> Bool = size == 0

type List[A]:
  is Sizeable
  push A -> List[A]
  pop -> List[A]
  head -> Maybe[A]
  [pattern] if isEmpty then Nothing else (head, pop)

type Quicksizable List[A]:
  let s = size
  [override] size = s
  [on] push: s' = s + 1
  [on/ pop s' = s - 1

data Nothing = Nothing
type Maybe[A] = Nothing | A

-- Can't be Node[A] | Nothing, because that means Nothing
-- is a list *in specific contexts* but not at runtime.
-- Well, what if a generic compile-time context resolves
-- to 2+ runtime contexts that both claim Nothing as a
-- List[A]?
-- For instance, if we had
--   type LinkedList[A] = Node[A] | Nothing
--   type SinglyLinkedList[A] = SingleNode[A] | Nothing
-- (Two, redundant implementations) Then we can write:
--   List[A] list = Nothing
-- In that case, what does
--   push list "foo"
-- do? Which push does it resolve to?
data LinkedList[A] = Node(value : A, tail : LinkedList[A]) | Empty
  is List[A]
  
  [override] push a = Head(a, this)
  
  [override] pop = case of
      Empty: Nothing
      Node: tail

  [override] head = case of
      Empty: Nothing
      Node: head

  [override] size = case of
      Empty: 0
      _: 1 + (size tail)

  ::empty -> LinkedList[A] = Empty

-- Instead of doing this:
--   type List[A]:
--     contains a:A -> Bool = ...
-- We can do this:
(List[A]) contains a:A -> Bool = case of
  Nothing: False
  (head, tail) where head == a: True
  (_, tail): tail contains a

