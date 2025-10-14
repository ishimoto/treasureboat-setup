#!/bin/bash

echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] Executing EO Template Installation (2024/07/09)"
echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] "

sudo mkdir -p /usr/local/TreasureBoat
sudo chmod 777 /usr/local/TreasureBoat
cp *.vm /usr/local/TreasureBoat/.

echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] EO Template Installation DONE"
echo "[INFO] ------------------------------------------------------------------------"
