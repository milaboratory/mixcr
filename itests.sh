#!/bin/bash

set -eo pipefail

# "Integration" tests for MiXCR
# Test standard analysis pipeline results

# Linux readlink -f alternative for Mac OS X
function readlinkUniversal() {
  targetFile=$1

  cd $(dirname $targetFile)
  targetFile=$(basename $targetFile)

  # iterate down a (possible) chain of symlinks
  while [ -L "$targetFile" ]; do
    targetFile=$(readlink $targetFile)
    cd $(dirname $targetFile)
    targetFile=$(basename $targetFile)
  done

  # compute the canonicalized name by finding the physical path
  # for the directory we're in and appending the target file.
  phys_dir=$(pwd -P)
  result=$phys_dir/$targetFile
  echo $result
}

os=$(uname)
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

declare -a all_tests
while read -r tst; do
  all_tests=( "${all_tests[@]}" "$tst" )
done < <(find itests -name '*.sh' | sed 's/\itests\///' | sed 's/\.sh//' | grep -v 'ignored' | sort)

tests=()
create_standard_results=false
run_tests=false
while [[ $# -gt 0 ]]; do
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
    tests=("${tests[@]}" "$key")
    ;;
  *)
    echo "Unknown option $key"
    exit 1
    ;;
  esac
done

# set to all tests if user didn't provide any specific test cases to run
if [[ ${#tests[@]} -eq 0 ]]; then
  tests=("${all_tests[@]}")
fi

rm -rf ${dir}/test_target
mkdir ${dir}/test_target

PATH=${dir}:${PATH}

which mixcr

mixcr -v

function go_assemble {
  mixcr assemble -r $1.clns.report $1.vdjca $1.clns
  for c in TCR IG TRB TRA TRG TRD IGH IGL IGK ALL; do
    mixcr exportClones -c ${c} $1.clns $1.clns.${c}.txt
  done
}

if [[ $create_standard_results == true ]]; then
  for s in sample_IGH test; do
    mixcr align -s hs -r ${s}_paired.vdjca.report ${s}_R1.fastq ${s}_R2.fastq ${s}_paired.vdjca
    go_assemble ${s}_paired
    mixcr align -s hs -r ${s}_single.vdjca.report ${s}_R1.fastq ${s}_single.vdjca
    go_assemble ${s}_single

    mixcr align -s hs -p kAligner2 -r ${s}_paired.vdjca.report ${s}_R1.fastq ${s}_R2.fastq ${s}_paired2.vdjca
    go_assemble ${s}_paired2
    mixcr align -s hs -p kAligner2 -r ${s}_single.vdjca.report ${s}_R1.fastq ${s}_single2.vdjca
    go_assemble ${s}_single2
  done
fi

function run_test() {
  cd ${dir}/test_target
  find . -type f -not -name '*.error' -delete

  echo "========================"
  echo "Running: $1"
  echo "========================"

  if ../itests/${1}; then
    echo "========================"
    echo "$1 executed successfully"
  else
    echo "========================"
    echo "$1 executed with error"
    touch "${1}".error
  fi
  echo "========================"
}

if [[ $run_tests == true ]]; then
  cd ${dir}/test_target
  files=`ls ../src/test/resources/sequences/*.fastq`
  for file in $files; do
    filename=${file#../src/test/resources/sequences/*}
    ln -s -f ../src/test/resources/sequences/$filename ../test_target/$filename
  done

  ln -s -f ../src/test/resources/sequences/big/CD4M1_test_R1.fastq.gz ${dir}/test_target/CD4M1_test_R1.fastq.gz
  ln -s -f ../src/test/resources/sequences/big/CD4M1_test_R2.fastq.gz ${dir}/test_target/CD4M1_test_R2.fastq.gz
  ln -s -f ../src/test/resources/sequences/big/subset_B004-7_S247_L001_R1_001.fastq.gz ${dir}/test_target/subset_B004-7_S247_L001_R1_001.fastq.gz
  ln -s -f ../src/test/resources/sequences/big/subset_B004-7_S247_L001_R2_001.fastq.gz ${dir}/test_target/subset_B004-7_S247_L001_R2_001.fastq.gz
  ln -s -f ../src/test/resources/sequences/big/subset_B004-7_S247_L001_I1_001.fastq.gz ${dir}/test_target/subset_B004-7_S247_L001_I1_001.fastq.gz
  ln -s -f ../src/test/resources/sequences/big/subset_B004-7_S247_L001_I2_001.fastq.gz ${dir}/test_target/subset_B004-7_S247_L001_I2_001.fastq.gz
  ln -s -f ../src/test/resources/sequences/big/single_cell_vdj_t_subset_R1.fastq.gz ${dir}/test_target/single_cell_vdj_t_subset_R1.fastq.gz
  ln -s -f ../src/test/resources/sequences/big/single_cell_vdj_t_subset_R2.fastq.gz ${dir}/test_target/single_cell_vdj_t_subset_R2.fastq.gz
  ln -s -f ../src/test/resources/sequences/big/trees_samples ${dir}/test_target/trees_samples
  ln -s -f ../src/test/resources/sequences/big/regression ${dir}/test_target/regression
  ln -s -f ../src/test/resources/sequences/umi_ig_data_2_subset_R1.fastq.gz ${dir}/test_target/umi_ig_data_2_subset_R1.fastq.gz
  ln -s -f ../src/test/resources/sequences/umi_ig_data_2_subset_R2.fastq.gz ${dir}/test_target/umi_ig_data_2_subset_R2.fastq.gz
  ln -s -f ../src/test/resources/bam/unsorted.bam ${dir}/test_target/unsorted.bam
  ln -s -f ../src/test/resources/bam/unpairedSortedByCoord.bam ${dir}/test_target/unpaired.bam
  ln -s -f ../src/test/resources/library_for_alleles_test.json ${dir}/test_target/library_for_alleles_test.json
  ln -s -f ../src/test/resources/sligtly_broken_library_for_alleles_test.json ${dir}/test_target/sligtly_broken_library_for_alleles_test.json
  cd ${dir}

  echo "======================================="
  echo "The following tests are to be executed:"
  for testName in "${tests[@]}"; do
    echo "${testName}.sh"
  done
  echo "======================================="

  for testName in "${tests[@]}"; do
    run_test "${testName}.sh"
  done

  if ls "${dir}"/test_target/*.error 1>/dev/null 2>&1; then
    ls -1 "${dir}"/test_target/*.error
    echo "There are tests with errors."
    exit 1
  else
    echo "All tests finished successfully."
    exit 0
  fi
fi
