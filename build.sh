#!/bin/bash

# Linux readlink -f alternative for Mac OS X
function readlinkUniversal() {
    targetFile=$1

    cd `dirname $targetFile`
    targetFile=`basename $targetFile`

    # iterate down a (possible) chain of symlinks
    while [ -L "$targetFile" ]
    do
        targetFile=`readlink $targetFile`
        cd `dirname $targetFile`
        targetFile=`basename $targetFile`
    done

    # compute the canonicalized name by finding the physical path 
    # for the directory we're in and appending the target file.
    phys_dir=`pwd -P`
    result=$phys_dir/$targetFile
    echo $result
}

os=`uname`
delta=100

dir=""

case $os in
    Darwin)
        dir=$(dirname "$(readlinkUniversal "$0")")
    ;;
    Linux)
        dir="$(dirname "$(readlink -f "$0")")"
    ;;
    FreeBSD)
        dir=$(dirname "$(readlinkUniversal "$0")")    
    ;;
    *)
       echo "Unknown OS."
       exit 1
    ;;
esac

if [[ ! -f ${dir}/repseqio/milib/pom.xml ]];
then
  echo "Please init git submodules. Try:"
  echo "git submodule update --init --recursive"
  exit 1
fi

function error_exit {
    echo -e "$1"
    exit "${2:-1}"
}

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "Building MiLib."
cd ${dir}/repseqio/milib
mvn clean install -DskipTests -B || error_exit "${RED}Problem building MiLib!${NC}" 1

echo "Building RepSeq.IO util/lib"
cd ${dir}/repseqio
mvn clean install -DskipTests -B || error_exit "${RED}Problem building RepSeq.IO util!${NC}" 1

echo "Building MiXCR."
cd ${dir}
mvn clean install -DskipTests -B || error_exit "${RED}Problem building MiXCR util!${NC}" 1

echo -e "${GREEN}Build successfull!${NC}"
echo "The following is the output of \"mixcr -v\" command:"
${dir}/mixcr -v || error_exit "${RED}Something went wrong!${NC}" 1
echo -e "${GREEN}Everything seems OK!${NC}"
echo ""
echo "Add mixcr script from this folder to your PATH variable or add symlink to your bin folder."
echo ""
echo "MiXCR is free for Academic use. For commertial use please contact licensing@milaboratory.com ."
echo ""
echo "If you discover bugs or have any questions, contact us at https://github.com/milaboratory/mixcr/issues ."
