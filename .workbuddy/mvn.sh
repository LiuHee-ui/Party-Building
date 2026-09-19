#!/usr/bin/env bash
# 本机 shell 缺少 dirname，Maven 自带的 mvn 脚本无法自解析路径，故直接用 classworlds 启动。
export JAVA_HOME="C:/Program Files/Java/jdk-21"
MVN_HOME="C:/Users/45029/.workbuddy/binaries/maven/apache-maven-3.9.9"
exec "$JAVA_HOME/bin/java" -classpath "$MVN_HOME/boot/plexus-classworlds-2.8.0.jar" \
  "-Dclassworlds.conf=$MVN_HOME/bin/m2.conf" \
  "-Dmaven.home=$MVN_HOME" \
  "-Dmaven.multiModuleProjectDirectory=$(pwd)" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
