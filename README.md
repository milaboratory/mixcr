[![image](https://readthedocs.org/projects/mixcr/badge/?version=latest)](https://mixcr.readthedocs.io)

## Overview

MiXCR is a universal software for fast and accurate analysis of raw T- or B- cell receptor repertoire sequencing data.

 - Easy to use. Default pipeline can be executed without any additional parameters (see *Usage* section)

 - TCR and IG repertoires
 
 - Following species are supported *out-of-the-box* using built-in library:
   - human
   - mouse
   - rat (only TRB and TRA)
   - *... several new species will be available soon*

- Efficiently extract repertoires from most of (if not *all*) types of TCR/IG-containing raw sequencing data:
  - data from all specialized RepSeq sample preparation protocols
  - RNA-Seq
  - WGS
  - single-cell data
  - *etc..*

- Has optional CDR3 reconstruction step, that allows to *recover full hypervariable region from several disjoint reads*. Uses sophisticated algorithms protecting from false-positive assemblies at the same time having best in class efficiency.

- Assemble clonotypes, applying several *error-correction* algorithms to eliminate artificial diversity arising from PCR and sequencing errors

- Clonotypes can be assembled based on CDR3 sequence (default) as well as any other region, including *full-length* variable sequence (from beginning of FR1 to the end of FR4)

- Assemble full TCR/Ig receptor sequences 

- Provides exhaustive output information for clonotypes and per-read alignments:
  - nucleotide and amino acid sequences of all immunologically relevant regions (FR1, CDR1, ..., CDR3, etc..)
  - identified V, D, J, C genes
  - nucleotide and amino acid mutations in germline regions
  - variable region topology (number of end V / D / J nucleotide deletions, length of P-segments, number of non-template N nucleotides)
  - sequencing quality scores for any extracted sequence
  - several other useful pieces of information
  
- Completely transparent pipeline, possible to track individual read fate from raw fastq entry to clonotype. Several useful tools available to evaluate pipeline performance: human readable alignments visualization, diff tool for alignment and clonotype files, etc...


## Installation / Download

#### Using Homebrew on Mac OS X or Linux (linuxbrew)

    brew install milaboratory/all/mixcr
    
to upgrade already installed MiXCR to the newest version:

    brew update
    brew upgrade mixcr

#### Docker

See [official Docker Image](https://hub.docker.com/r/milaboratory/mixcr).

#### Manual install (any OS)

* download latest stable MiXCR build from [release page](https://github.com/milaboratory/mixcr/releases/latest)
* unzip the archive
* add resulting folder to your ``PATH`` variable
  * or add symbolic link for ``mixcr`` script to your ``bin`` folder
  * or use MiXCR directly by specifying full path to the executable script

#### Requirements

* Any OS with Java support (Linux, Windows, Mac OS X, etc..)
* Java 1.8 or higher
 
## Usage

See usage examples in the official documentation https://mixcr.readthedocs.io/en/master/quickstart.html

## Documentation

Detailed documentation can be found at https://mixcr.readthedocs.io/

If you haven't found the answer to your question in the docs, or have any suggestions concerning new features, feel free to create an issue here, on GitHub, or write an email to support@milaboratory.com .

## Build

Requirements:

- Maven 3 (https://maven.apache.org/)

To build MiXCR from source:

- Clone repository

  ```
  git clone https://github.com/milaboratory/mixcr.git
  ```

- Refresh git submodules

  ```
  git submodule update --init --recursive
  ```
  
- Run build script. First build may take several minuties to download sequences for built-in V/D/J/C gene libraries from NCBI.

  ```
  ./build.sh
  ```

## License

Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved

Before downloading or accessing the software, please read carefully the
License Agreement available at:
https://github.com/milaboratory/mixcr/blob/develop/LICENSE

By downloading or accessing the software, you accept and agree to be bound
by the terms of the License Agreement. If you do not want to agree to the terms
of the Licensing Agreement, you must not download or access the software.

## Cite

* Dmitriy A. Bolotin, Stanislav Poslavsky, Igor Mitrophanov, Mikhail Shugay, Ilgar Z. Mamedov, Ekaterina V. Putintseva, and Dmitriy M. Chudakov. "MiXCR: software for comprehensive adaptive immunity profiling." *Nature methods* 12, no. 5 (**2015**): 380-381.

* Dmitriy A. Bolotin, Stanislav Poslavsky, Alexey N. Davydov, Felix E. Frenkel, Lorenzo Fanchi, Olga I. Zolotareva, Saskia Hemmers, Ekaterina V. Putintseva, Anna S. Obraztsova, Mikhail Shugay, Ravshan I. Ataullakhanov, Alexander Y. Rudensky, Ton N. Schumacher & Dmitriy M. Chudakov. "Antigen receptor repertoire profiling from RNA-seq data." *Nature Biotechnology* 35, 908–911 (**2017**)
