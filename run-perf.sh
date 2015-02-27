#!/bin/bash

echo "## User is `id`"
echo "## PWD is `pwd`"

echo "## JAVA_HOME is ${JAVA_HOME}"
JAVA_EXEC="${JAVA_HOME}/bin/java"
if [ -e "${JAVA_EXEC}" ] ; then
    echo "  Exec Exist: ${JAVA_EXEC}"
else
    echo "  Exec Missing: ${JAVA_EXEC}"
fi

echo "## M2_HOME is ${M2_HOME}"
M2_EXEC="${M2_HOME}/bin/mvn"
if [ -e "${M2_EXEC}" ] ; then
    echo "  Exec Exists: ${M2_EXEC}"
else
    echo "  Exec Missing: ${M2_EXEC}"
fi

echo "## PATH is..."
echo -n "  "
echo $PATH | sed -e "s/:/\n  /g"

echo "## Maven Version"
mvn --version

echo "## Java Version"
java -version

