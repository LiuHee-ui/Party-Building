#!/usr/bin/env bash
# Maven 启动包装脚本
#
# 为什么需要它：某些 Windows 环境下 bash 缺少 dirname，Maven 自带的 mvn 脚本
# 无法自解析路径而报错。这里绕过 mvn 脚本，直接用 classworlds 启动 Maven。
#
# 用法（必须 source，因为要设置 JAVA_HOME）：
#   . scripts/mvn.sh -B clean package
#
# 路径解析顺序：
#   JAVA_HOME / MVN_HOME 环境变量优先 -> 常见的本机安装位置 -> 报错提示
# 不再硬编码某台机器的绝对路径。

if [ -n "$MVN_HOME" ]; then
  : # 用外部传入的
elif [ -d "$HOME/.workbuddy/binaries/maven/apache-maven-3.9.9" ]; then
  MVN_HOME="$HOME/.workbuddy/binaries/maven/apache-maven-3.9.9"
elif [ -n "$(command -v mvn 2>/dev/null)" ]; then
  # 本机已装 Maven，直接从 mvn 的位置反推 MVN_HOME
  MVN_HOME="$(cd "$(dirname "$(command -v mvn)")/.." 2>/dev/null && pwd)"
fi

if [ -z "$JAVA_HOME" ]; then
  for candidate in \
    "$LOCALAPPDATA/Programs/Java/jdk-21" \
    "$LOCALAPPDATA/Programs/Java/jdk-25" \
    "/c/Program Files/Java/jdk-21"
  do
    if [ -x "$candidate/bin/java" ] || [ -x "$candidate/bin/java.exe" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [ -z "$JAVA_HOME" ]; then
  echo "错误：未找到 JDK。请设置 JAVA_HOME 环境变量。" >&2
  return 1 2>/dev/null || exit 1
fi

if [ -z "$MVN_HOME" ] || [ ! -f "$MVN_HOME/boot/plexus-classworlds-2.8.0.jar" ]; then
  echo "错误：未找到 Maven。" >&2
  echo "  请设置 MVN_HOME 指向 Maven 安装目录（需含 boot/plexus-classworlds-2.8.0.jar），" >&2
  echo "  或安装 Maven 后重试。当前 MVN_HOME='$MVN_HOME'" >&2
  return 1 2>/dev/null || exit 1
fi

exec "$JAVA_HOME/bin/java" -classpath "$MVN_HOME/boot/plexus-classworlds-2.8.0.jar" \
  "-Dclassworlds.conf=$MVN_HOME/bin/m2.conf" \
  "-Dmaven.home=$MVN_HOME" \
  "-Dmaven.multiModuleProjectDirectory=$(pwd)" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
