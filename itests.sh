#!/bin/bash

set -e
set -o pipefail

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

# UseCase 1

if [[ $run_tests == true ]]; then
#  echo "Running test case 1"
#  mixcr align -s hs -OvParameters.geneFeatureToAlign=VGeneWithP -OsaveOriginalReads=true test_R1.fastq test_R2.fastq case1.vdjca
#  mixcr assemble case1.vdjca case1.clns
#
#  mixcr exportAlignments -nFeatureImputed VDJRegion -descrsR1 -descrsR2 case1.vdjca case1.alignments.txt
#
#  echo "Running test case 2"
#  mixcr analyze shotgun -f --species hs --contig-assembly --impute-germline-on-export --starting-material rna test_R1.fastq test_R2.fastq case2
#
#  echo "Running test case 3"
#  mixcr analyze amplicon --receptor-type tra --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters no-adapters test_R1.fastq test_R2.fastq case3

  echo "Running test case 4"
  mixcr analyze amplicon --receptor-type tra --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz case4
  # Checking skip steps behaviour
  mixcr analyze amplicon --receptor-type tra --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz case4

  echo "Running test case 5"
  mixcr analyze amplicon --receptor-type tra --align '-OseparateByC=true' --align '-OseparateByV=true' --align '-OseparateByJ=true' --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz case4
fi
