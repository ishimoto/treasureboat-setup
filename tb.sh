#!/bin/bash

echo "**********************************************************************"
echo "***** starting 'tb.sh' script version : 2025/11/19"
echo "***** TB Gradle/Maven Setup"
echo "**********************************************************************"
echo ""

echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] Update Wrapper"
echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] "
./mvnw wrapper:wrapper
cd GradleDev
./gradlew wrapper
cd ..

echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] Executing EO Template Installation"
echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] "
cd TBEOTemplates/
mkdir -p /Users/Shared/TreasureBoat
chmod 777 /Users/Shared/TreasureBoat
cp *.vm /Users/Shared/TreasureBoat/.
cd ..

echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] Install TreasureBoat SuperPOM"
echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] "
cd SuperPOM/
../mvnw install
cd ..

echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] Executing Gradle PlugIn Installation"
echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] "
cd GradleDev/gradle-tb-plugin
../gradlew wrapper
../gradlew clean
../gradlew build
../gradlew publishToMavenLocal
ls -l ~/.m2/repository/org/treasureboat/gradle/gradle-tb-plugin/
cd ../..

echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] DONE"
echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] "
