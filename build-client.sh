mkdir -p bin/client

javac --release 8 -d bin/client src/*.java

echo "Main-Class: Tetris2805" > bin/client/manifest.txt

mkdir -p bin/client/resources
cp -r src/resources/assets bin/client/resources/
cp -r src/resources/load bin/client/resources/

mkdir -p goblin-tetris/src
cp -r src/data goblin-tetris/src/

mkdir -p goblin-tetris/src/resources
cp -r src/resources/audio goblin-tetris/src/resources/

jar cfm goblin-tetris/GoblinTetris.jar bin/client/manifest.txt -C bin/client .
