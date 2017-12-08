[![Build Status](https://travis-ci.org/milaboratory/mixcr.svg)](https://travis-ci.org/milaboratory/mixcr)
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

#### Enriched RepSeq Data

Here is a very simple usage example that will extract repertoire data (in the form of clonotypes list) from raw sequencing data of enriched RepSeq library:

    mixcr align -r log.txt input_R1.fastq.gz input_R2.fastq.gz alignments.vdjca
    mixcr assemble -r log.txt alignments.vdjca clones.clns
    mixcr exportClones clones.clns clones.txt
  
this will produce a tab-delimited list of clones (`clones.txt`) assembled by their CDR3 sequences with extensive information on their abundances, V, D and J genes, mutations in germline regions, topology of VDJ junction etc.

#### Repertoire extraction from RNA-Seq

MiXCR is equally effective in extraction of repertoire information from non-enriched data, like RNA-Seq or WGS. This example illustrates usage for RNA-Seq:

    mixcr align -p rna-seq -r log.txt input_R1.fastq.gz input_R2.fastq.gz alignments.vdjca
    mixcr assemblePartial alignments.vdjca alignment_contigs.vdjca
    mixcr assemble -r log.txt alignment_contigs.vdjca clones.clns
    mixcr exportClones clones.clns clones.txt

#### Further reading

MiXCR pipeline is very flexible, and can be applied to raw data from broad spectrum of experimental setups. For detailed description of MiXCR features and options please see documentation.

## Documentation

Detailed documentation can be found at https://mixcr.readthedocs.io/

If you haven't found the answer to your question in the docs, or have any suggestions concerning new features, feel free to create an issue here, on GitHub, or write an email to support@milaboratory.com .

## Build

Dependancy:

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

Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
(here and after addressed as Inventors)
All Rights Reserved

Permission to use, copy, modify and distribute any part of this program for
educational, research and non-profit purposes, by non-profit institutions
only, without fee, and without a written agreement is hereby granted,
provided that the above copyright notice, this paragraph and the following
three paragraphs appear in all copies.

Those desiring to incorporate this work into commercial products or use for
commercial purposes should contact the Inventors using one of the following
email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com

IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
PATENT, TRADEMARK OR OTHER RIGHTS.

## Cite

* Dmitriy A. Bolotin, Stanislav Poslavsky, Igor Mitrophanov, Mikhail Shugay, Ilgar Z. Mamedov, Ekaterina V. Putintseva, and Dmitriy M. Chudakov. "MiXCR: software for comprehensive adaptive immunity profiling." *Nature methods* 12, no. 5 (**2015**): 380-381.

* Dmitriy A. Bolotin, Stanislav Poslavsky, Alexey N. Davydov, Felix E. Frenkel, Lorenzo Fanchi, Olga I. Zolotareva, Saskia Hemmers, Ekaterina V. Putintseva, Anna S. Obraztsova, Mikhail Shugay, Ravshan I. Ataullakhanov, Alexander Y. Rudensky, Ton N. Schumacher & Dmitriy M. Chudakov. "Antigen receptor repertoire profiling from RNA-seq data." *Nature Biotechnology* 35, 908–911 (**2017**)

## Files referenced in original paper

Can be found [here](https://github.com/milaboratory/mixcr/blob/develop/doc/paper/paperAttachments.md).
