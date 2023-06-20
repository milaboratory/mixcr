#!/usr/bin/env bash

temp_dir="src/test/resources/sequences/big/regression_to_add"
rm -rf "$temp_dir"
mkdir "$temp_dir"

revision=$(./mixcr -v | grep MiXCR | cut -d= -f2 | cut -d';' -f1)

./itests.sh test case-base_single_cell
#head -n1 V04_baseBuldTrees.shmt | cut -c 12-15
clnaVersion=$(head -n1 test_target/base_single_cell.vdjcontigs.clna | cut -c 12-15)
clnsVersion=$(head -n1 test_target/base_single_cell.vdjcontigs.contigs.clns | cut -c 12-15)
vdjcaVersion=$(head -n1 test_target/base_single_cell.raw.vdjca | cut -c 12-15)

cp test_target/base_single_cell.vdjcontigs.clna "$temp_dir/${clnaVersion}_${revision}_base_single_cell.vdjcontigs.clna"
cp test_target/base_single_cell.vdjcontigs.contigs.clns "$temp_dir/${clnsVersion}_${revision}_base_single_cell.vdjcontigs.contigs.clns"
cp test_target/base_single_cell.raw.vdjca "$temp_dir/${vdjcaVersion}_${revision}_base_single_cell.raw.vdjca"

./itests.sh test case003

cp test_target/case3.clna "$temp_dir/${clnaVersion}_${revision}_case3.clna"
cp test_target/case3.contigs.clns "$temp_dir/${clnsVersion}_${revision}_case3.contigs.clns"
cp test_target/case3.vdjca "$temp_dir/${vdjcaVersion}_${revision}_case3.vdjca"

./itests.sh test case-base_build_trees
shmtVersion=$(head -n1 test_target/base_build_trees.shmt | cut -c 12-15)
cp test_target/base_build_trees.shmt "$temp_dir/${shmtVersion}_${revision}_base_build_trees.shmt"

echo
files=$(ls $temp_dir)
echo "Files prepared to upload:"
echo "$files"
echo "You can remove some from dir $temp_dir before continue"

read -r -p "Proceed? [y/N] " response
if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]
then
  files=$(ls $temp_dir)
  for file in $files; do
    aws s3 cp --acl public-read "$temp_dir/$file" "s3://files.milaboratory.com/test-data/regression/$file"
  done
  aws s3 ls s3://files.milaboratory.com/test-data/regression/ | tr -s ' ' '\t' | cut -f 4 > regression/list
fi
