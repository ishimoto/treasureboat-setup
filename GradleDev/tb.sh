#!/bin/bash

./gradlew wrapper

cd gradle-tb-plugin
./gradlew wrapper
./gradlew clean
./gradlew build
./gradlew publishToMavenLocal
ls -l ~/.m2/repository/org/treasureboat/gradle/gradle-tb-plugin/
cd ..
