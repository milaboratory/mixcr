#!/usr/bin/env sh

set -e

mkdir -p target/mixcr
cp mixcr target/mixcr/mixcr
cp LICENSE target/mixcr/LICENSE
cp target/mixcr*-distribution*.jar target/mixcr/mixcr.jar

cd target

zip -r -9 mixcr.zip mixcr
