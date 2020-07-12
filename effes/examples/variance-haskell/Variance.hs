data Producer a = Producer a
data Consumer a b = Consumer (a -> b)
data Predicate a = Predicate (a -> Bool)

consumeOne :: Producer a -> Consumer a b -> Predicate a -> Maybe b
consumeOne (Producer p) (Consumer c) (Predicate f) =
  if f e then
    Just $ c e
  else
    Nothing
  where e = p

numPredicate :: Num n => Predicate n
numPredicate = Predicate $ const True

intPredicate :: Predicate Int
intPredicate = Predicate $ const True

intProducer :: Producer Int
intProducer = Producer 1

numProducer :: Num a => Producer a
numProducer = Producer 1

intConsumer :: Consumer Int String
intConsumer = Consumer show

alpha = consumeOne intProducer intConsumer intPredicate
beta = consumeOne intProducer intConsumer numPredicate
gamma = consumeOne numProducer intConsumer numPredicate

idConsumer = Consumer id
alpha' = consumeOne intProducer idConsumer intPredicate
beta' = consumeOne intProducer idConsumer numPredicate
gamma' = consumeOne numProducer idConsumer numPredicate

