#!/bin/bash

set -e
set -o pipefail

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

mkdir -p $dir/src/test/resources/sequences/big/
cd $dir/src/test/resources/sequences/big/

if [[ ! -f CD4M1_test_R1.fastq.gz ]]; then
    wget https://s3.amazonaws.com/files.milaboratory.com/test-data/CD4M1_test_R1.fastq.gz
fi

if [[ ! -f CD4M1_test_R2.fastq.gz ]]; then
    wget https://s3.amazonaws.com/files.milaboratory.com/test-data/CD4M1_test_R2.fastq.gz
fi

