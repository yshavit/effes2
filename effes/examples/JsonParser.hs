data Json =
  | Nothing
  | String
  | Real
  | Map[String,Json]
  | List[Json]

type Try[A] = A | Failure

toJson (String) => Try[Json]: -- => indicates that id can be modified
  {ltrim}
  case firstChar of
    Nothing -> Failure "nothing to read"
    '"' -> toJsonString
    '[' -> toJsonArray
    '{' -> toJsonObject
    isNumeric -> toJsonNumber
    -- Cool idea? Since we have composite objects, if each element of a string
    -- is an object anyway, maybe compose it with something that includes its
    -- current state, like line number and column. That way, if there's a failure,
    -- you just get the last character involved, and it'll carry all the information
    -- you need!

isNumeric (Char) -> Bool: "0123456789".contains id

toJsonString (String) => Try[Json]:
  if {nextChar} isnt '"'
    return Failure "expected opening double-quote" @ id
  result = ""
  loop {nextChar}
    Nothing -> done with Failure "expected closing double-quote" @ id
    '"' -> done with result
    '\\' -> result += toUtf8Char
    _ -> result += nextChar
  result

toJsonArray (String) => Try[Json]:
  if {nextChar} isnt '[':
    return Failure "expected opening brace"
  {ltrim}
  result = LinkedList:empty -- Type inference I hope?
  loop {nextNonspace}
    Nothing -> done with Failure "expected closing brace" @ id
    ']' -> done with result
    c -> result{push} ((prepend c) toJson) -- need a better way to do lookahead
         if {nextNonspace} isnt ','
           done with Failure "expected comma between list elements" @ id

toJsonObject (String) => Try[Json]:
  if {nextChar} isnt '{'
    return Failure "expected opening curly" @ id
  {ltrim}
  result = TreeMap:empty
  loop {nextNonspace}
    Nothing -> done with Failure "expected closing curly" @ id
    '}' -> done with result
    c -> {prepend c} -- ned a better way to do lookahead
         key = toJsonString
         {ltrim}
         if {nextChar} isnt ':'
           done with Failure "expected a colon" @ id
         value = {toJson}
         result.put key, value

