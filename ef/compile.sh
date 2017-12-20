#!/usr/bin/bash

cd "$(dirname $0)"
compiler_jar=../effes2j/target/effes2j-1.0-SNAPSHOT-jar-with-dependencies.jar
java -Dsource=. -Doutput=../efct -jar "$compiler_jar" 
