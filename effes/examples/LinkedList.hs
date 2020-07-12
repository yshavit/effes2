data Nothing
data Something[A] = value : A
data True, False nicknamed Boolean

nickname Maybe[A] = Nothing | Something[A]

hasSomething : (Maybe[A]) -> Boolean
  case of Nothing -> False
          _       -> True

Sizeable ->
  size : Int
  isEmpty : Boolean = size == 0

Stack[A] ->
  Sizeable
  push : a:A -> Stack[A]
  pop : Stack[A]
  head : Maybe[A]

data LinkedNode[A] = elem : A, next : LinkNode[A]
nickname LinkedList[A] = Nothing | LinkedNode[A]

LinkedList[A] -> Stack[A]
  size = case of Nothing -> 0
                 LinkedNode[A] -> 1 + next size
  push = a -> LinkedNode a (Something id)
  pop = case of Nothing -> Nothing
                LinkedNode[A] -> next
  head = case of Nothing -> Nothing
                 LinkedNode[A] -> Something elem

removeFirst = (s : Stack[A Eq], needle : A) -> Stack[A]
  case head of
    Nothing -> id
    Something ->
      next = pop
      if (value == a) then next else removeFirst next

find = (s : Stack[A Eq], needle : A) -> Maybe[A]
  case head of
    Nothing -> Nothing
    Something ->
      if value == a then head else (pop id) find needle

contains = (s : Stack[A Eq], needle : a) -> Boolean = (find needle) hasSomething

ConSize LinkedList[A] ->
  s : Int initially size
  push _ -> s' = s + 1
  pop -> s' = case s of 0 -> 0
                        _ -> s - 1

