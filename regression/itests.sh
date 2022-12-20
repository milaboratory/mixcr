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
done < <(find itests -name '*.sh' | sed 's/\itests\///' | sed 's/\.sh//' | sort)

tests=()
while [[ $# -gt 0 ]]; do
  key="$1"
  shift
  tests=("${tests[@]}" "$key")
done

# set to all tests if user didn't provide any specific test cases to run
if [[ ${#tests[@]} -eq 0 ]]; then
  tests=("${all_tests[@]}")
fi

PATH=${dir}:${PATH}

rm -rf ${dir}/test_target
mkdir ${dir}/test_target

ln -s ${dir}/test_data/single_cell_vdj_t_subset_R1.fastq.gz ${dir}/test_target/single_cell_vdj_t_subset_R1.fastq.gz
ln -s ${dir}/test_data/single_cell_vdj_t_subset_R2.fastq.gz ${dir}/test_target/single_cell_vdj_t_subset_R2.fastq.gz
ln -s ${dir}/test_data/trees_samples ${dir}/test_target/trees_samples

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
    touch "${1}".error
  fi
  echo "========================"
}

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
