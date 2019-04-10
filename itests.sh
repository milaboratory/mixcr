#!/bin/bash

set -eo pipefail

# "Integration" tests for MiXCR
# Test standard analysis pipeline results

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

tests=("case1" "case2" "case3" "case4" "case5" "case6" "case7")

create_standard_results=false
run_tests=false
while [[ $# > 0 ]]
do
    key="$1"
    shift
    case $key in
        std)
            create_standard_results=true
        ;;
        test)
            run_tests=true
        ;;
        case*)
            tests=("$key")
        ;;
        *)
            echo "Unknown option $key";
            exit 1
        ;;
esac
done

rm -rf ${dir}/test_target
mkdir ${dir}/test_target

cp ${dir}/src/test/resources/sequences/*.fastq ${dir}/test_target/

cd ${dir}/test_target/
ln -s ../src/test/resources/sequences/big/CD4M1_test_R1.fastq.gz ${dir}/test_target/CD4M1_test_R1.fastq.gz
ln -s ../src/test/resources/sequences/big/CD4M1_test_R2.fastq.gz ${dir}/test_target/CD4M1_test_R2.fastq.gz

PATH=${dir}:${PATH}

which mixcr

mixcr -v

function go_assemble {
  mixcr assemble -r $1.clns.report $1.vdjca $1.clns
  for c in TCR IG TRB TRA TRG TRD IGH IGL IGK ALL
  do
    mixcr exportClones -c ${c} $1.clns $1.clns.${c}.txt
  done
}

if [[ $create_standard_results == true ]]; then
  for s in sample_IGH test;
  do
    mixcr align -s hs -r ${s}_paired.vdjca.report ${s}_R1.fastq ${s}_R2.fastq ${s}_paired.vdjca
    go_assemble ${s}_paired
    mixcr align -s hs -r ${s}_single.vdjca.report ${s}_R1.fastq ${s}_single.vdjca
    go_assemble ${s}_single
  done
fi

function run_test() {
  cd ${dir}/test_target
  echo "========================"
  echo "Running: $1"
  echo "========================"

  if ../itests/${1}; then
    echo "========================"
    echo "$1 executed successfully"
  else
    echo "========================"
    echo "$1 executed with error"
    touch ${1}.error
  fi
  echo "========================"
}

if [[ $run_tests == true ]]; then
  for testName in ${tests[@]} ; do
      run_test "${testName}.sh"
  done

  if ls ${dir}/test_target/*.error 1> /dev/null 2>&1; then
    echo "There are tests with errors."
    exit 1
  else
    echo "All tests finished successfully."
    exit 0
  fi
fi
