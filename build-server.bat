javac --release 11 -d bin/server server/MatchmakingServer.java
echo Main-Class: MatchmakingServer > bin/server/manifest.txt
jar cfm Server.jar bin/server/manifest.txt -C bin/server/ .
