#!/usr/bin/env bash
set -e

javac --release 8 -d bin/server server/MatchmakingServer.java

printf "Main-Class: MatchmakingServer\n" > bin/server/manifest.txt

jar cfm Server.jar bin/server/manifest.txt -C bin/server .
