javac --release 11 -d bin/client src/*.java
echo Main-Class: Tetris2805 > bin/client/manifest.txt
xcopy "src\resources\assets" "bin\client\resources\assets" /E /I /Y /Q
xcopy "src\resources\load" "bin\client\resources\load" /E /I /Y /Q
xcopy "src\data" "goblin-tetris\src\data" /E /I /Y /Q
xcopy "src\resources\audio" "goblin-tetris\src\resources\audio" /E /I /Y /Q
jar cfm goblin-tetris/GoblinTetris.jar bin/client/manifest.txt -C bin/client/ .
