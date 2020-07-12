data Json =
  | Nothing
  | String
  | Real
  | Map[String,Json]
  | List[Json]

(String) toJson -> Try[Json] =
  {ltrim}
  case head of
    Nothing: Failure "nothing left to parse" @ id
    '"': toJsonString
    '[': toJsonArray
    '{': toJsonObject
    _ where isNumeric: toJsonNumber

private (String) toJsonString -> Try[Json] = TODO
