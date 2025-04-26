#!/bin/bash

# Use absolute path to project root
PROJECT_ROOT="/home/rui/Coding/jaws"

echo "Project root: $PROJECT_ROOT"

# Build the project
cd "$PROJECT_ROOT" || exit 1
echo "Current directory: $(pwd)"
mvn clean package

# Create a directory for dependencies
mkdir -p "$PROJECT_ROOT/target/dependency"
(cd "$PROJECT_ROOT/target/dependency" && jar -xf "$PROJECT_ROOT/target"/*.jar)

# Get the Maven repository path
MAVEN_REPO=$(cd "$PROJECT_ROOT" && mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout)

# Run the fuzzer with all dependencies
"$PROJECT_ROOT/jazzer/jazzer" \
    --cp="$PROJECT_ROOT/target/classes:$PROJECT_ROOT/target/test-classes:$PROJECT_ROOT/target/dependency/*:\
$MAVEN_REPO/org/tinylog/tinylog-api/2.7.0/tinylog-api-2.7.0.jar:\
$MAVEN_REPO/org/tinylog/tinylog-impl/2.7.0/tinylog-impl-2.7.0.jar:\
$MAVEN_REPO/org/tinylog/slf4j-tinylog/2.7.0/slf4j-tinylog-2.7.0.jar:\
$MAVEN_REPO/org/xerial/sqlite-jdbc/3.47.0.0/sqlite-jdbc-3.47.0.0.jar:\
$MAVEN_REPO/io/jsonwebtoken/jjwt/0.12.6/jjwt-0.12.6.jar:\
$MAVEN_REPO/io/jsonwebtoken/jjwt-api/0.12.6/jjwt-api-0.12.6.jar:\
$MAVEN_REPO/io/jsonwebtoken/jjwt-impl/0.12.6/jjwt-impl-0.12.6.jar:\
$MAVEN_REPO/io/jsonwebtoken/jjwt-jackson/0.12.6/jjwt-jackson-0.12.6.jar:\
$MAVEN_REPO/com/fasterxml/jackson/core/jackson-databind/2.18.2/jackson-databind-2.18.2.jar:\
$MAVEN_REPO/com/fasterxml/jackson/core/jackson-core/2.18.2/jackson-core/2.18.2.jar:\
$MAVEN_REPO/com/fasterxml/jackson/core/jackson-annotations/2.18.2/jackson-annotations-2.18.2.jar:\
$MAVEN_REPO/at/favre/lib/bcrypt/0.10.2/bcrypt-0.10.2.jar" \
    --target_class=fuzzer.MimirFuzzer \
    --instrumentation_includes=org.ruitx.jaws.** \
    --reproducer_path="$PROJECT_ROOT/fuzz-reproducers" \
    --keep_going=1000