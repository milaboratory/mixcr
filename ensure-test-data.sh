#!/bin/bash

set -e
set -o pipefail

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

mkdir -p $dir/src/test/resources/sequences/big/
cd $dir/src/test/resources/sequences/big/

if [[ ! -f CD4M1_test_R1.fastq.gz ]]; then
    curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/CD4M1_test_R1.fastq.gz
fi

if [[ ! -f CD4M1_test_R2.fastq.gz ]]; then
    curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/CD4M1_test_R2.fastq.gz
fi

if [[ ! -f single_cell_vdj_t_subset_R1.fastq.gz ]]; then
    curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/single_cell_vdj_t_subset_R1.fastq.gz
fi

if [[ ! -f single_cell_vdj_t_subset_R2.fastq.gz ]]; then
    curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/single_cell_vdj_t_subset_R2.fastq.gz
fi

if [[ ! -d yf_sample_data ]]; then
  curl -sS https://s3.amazonaws.com/files.milaboratory.com/test-data/yf_sample_data.tar | tar -xv
fi

if [[ ! -d pa_test_data ]]; then
  curl -sS https://s3.amazonaws.com/files.milaboratory.com/test-data/pa_test_data.tar | tar -xv
fi

mkdir -p trees_samples
cd trees_samples
if [[ ! -d MRK_p02_Bmem_1_CGTACTAG-AAGGAGTA_L00M_R1.fastq.gz ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/MRK_p02_Bmem_1_CGTACTAG-AAGGAGTA_L00M_R1.fastq.gz
fi
if [[ ! -d MRK_p02_Bmem_1_CGTACTAG-AAGGAGTA_L00M_R2.fastq.gz ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/MRK_p02_Bmem_1_CGTACTAG-AAGGAGTA_L00M_R2.fastq.gz
fi
if [[ ! -d MRK_p03_Bmem_1_TCCTGAGC-GTAAGGAG_L00M_R1.fastq.gz ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/MRK_p03_Bmem_1_TCCTGAGC-GTAAGGAG_L00M_R1.fastq.gz
fi
if [[ ! -d MRK_p03_Bmem_1_TCCTGAGC-GTAAGGAG_L00M_R2.fastq.gz ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/MRK_p03_Bmem_1_TCCTGAGC-GTAAGGAG_L00M_R2.fastq.gz
fi
cd ../

mkdir -p regression
cd regression
if [[ ! -d V04_baseBuldTrees.shmt ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V04_baseBuldTrees.shmt
fi
if [[ ! -d V05_base_build_trees.shmt ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V05_base_build_trees.shmt
fi

if [[ ! -d V10_baseSingleCell_vdjcontigs.clna ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V10_baseSingleCell_vdjcontigs.clna
fi
if [[ ! -d V10_case3.clna ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V10_case3.clna
fi
if [[ ! -d V11_base_single_cell.vdjcontigs.clna ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V11_base_single_cell.vdjcontigs.clna
fi
if [[ ! -d V11_case3.clna ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V11_case3.clna
fi

if [[ ! -d V15_baseSingleCell_vdjcontigs.clns ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V15_baseSingleCell_vdjcontigs.clns
fi
if [[ ! -d V15_case3.clns ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V15_case3.clns
fi
if [[ ! -d V16_base_single_cell.vdjcontigs.contigs.clns ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V16_base_single_cell.vdjcontigs.contigs.clns
fi
if [[ ! -d V16_case3.contigs.clns ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V16_case3.contigs.clns
fi

if [[ ! -d V20_baseSingleCell_vdjcontigs.vdjca ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V20_baseSingleCell_vdjcontigs.vdjca
fi
if [[ ! -d V20_case3.vdjca ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V20_case3.vdjca
fi
if [[ ! -d V21_base_single_cell.vdjcontigs.vdjca ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V21_base_single_cell.vdjcontigs.vdjca
fi
if [[ ! -d V21_case3.vdjca ]]; then
  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/V21_case3.vdjca
fi
cd ../
