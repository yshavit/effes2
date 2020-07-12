Mutable List[A]:
  setHead :: A -> ()

Mutable LinkedList[A]:
  -- Required by Mutable List[A]:
  setHead = (e:A) -> () -- yuck!
    set id' = Mutable l
    where l = pop; push e
  -- Not required
  popHead = () -> ()
    set id' = Mutable pop
  -- While I'm at it
  doubleSize :: Int = () -> size * 2
nickname Mutator[A] = A -> A

ListsListCrazy[A]:
  is Mutable List[Mutable A] ... figure it out

Alpha[A]:
  is List[Mutable A]
  
