#!/bin/zsh -p

exec java \
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=29009 \
-Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.SocketProviderWithBlockingDisabled \
--patch-module=java.base=patched-nio/target/patched-nio-1.0-SNAPSHOT.jar \
-cp echoserver/target/echoserver-1.0-SNAPSHOT.jar:chunked-io-framework/target/chunked-io-framework-1.0-SNAPSHOT.jar \
com.flowlogix.io.echoserver.Main
