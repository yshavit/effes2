type Map[K,V]
  put = (K,V) -> Map[K,V]
  get = (K) -> Maybe[V]
  size = () -> Int
  isEmpty = () -> Bool: size == 0
  -- Also entries, keys, hasKey, hasValues, etc

-- data LinkedNode[A] = element : A, next : Maybe[LinkedNode[A]]

type HashMap[K,V]
  ^put (k:K, v:V) -> Map[K,V]
    h = hash k
    t = put h, case table get h of
      Nothing -> Entry k, v, h
      b -> b put k, v, h
    id(table = t, size = size + 1)
    where
      Entry -> put (k:K, v:V, h:Int) ->
        if h == hash && k == key && v == value
          id
        else case next of
          Nothing -> Entry k, v, h
          else -> next put k, v, h
  
  ^get (k:K, v:V) -> Maybe[V]
    h = hash k
    table get h; lookUp k, h
    where
      Maybe[Entry] -> lookUp (k:K, h:Int) ->
        if h == hash && k == key
          then value
          else lookUp k, h
  
  ^size = size
  
  static empty (capacity: Int, hasher: K -> Int) -> HashMap[K,V]
    HashMap table = Array(capacity), hasher = hasher

  [K -> Hashable] -> static empty (capacity: Int) -> HashMap[K, V]
    empty capacity, K.hash
  
  where
    size : Int = initially 0
    table : Array[Maybe[Entry]]
    hasher : (k:Key) -> Int
    
    data Entry = key : K, value : V, hash : Int, next : Maybe[Entry]
    
    hash = (k:K) -> Int: hasher k % table size

