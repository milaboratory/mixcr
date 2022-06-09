[![image](https://readthedocs.org/projects/mixcr/badge/?version=latest)](https://mixcr.readthedocs.io)


![MiXCR logo](./doc/_static/MiXCR_logo_dark.png#gh-light-mode-only)
![MiXCR logo](./doc/_static/MiXCR_logo_white.png#gh-dark-mode-only)

MiXCR is a universal software for fast and accurate analysis of raw T- or B- cell receptor repertoire sequencing data. It works with any kind of sequencing data:
 - Bulk repertoire sequencing data with or without UMIs
 - Single cell sequencing data including but not limited to 10x Genomics protocols 
 - RNA-Seq or any other kind of fragmented/shotgun data which may contain just a tiny fraction of target sequences
 - and any other kind of sequencing data containing TCRs or BCRs 

Powerful downstream analysis tools allow to obtain vector plots and tabular results for multiple measures. Key features include:
 - Ability to group samples by metadata values and compare repertoire features between groups 
 - Comprehensive repertoire normalization and filtering 
 - Statistical significance tests with proper p-value adjustment
 - Repertoire overlap analysis
 - Vector plots output (.svg / .pdf)
 - Tabular outputs

Other key features:

- Clonotype assembly by arbitrary gene feature, including *full-length* variable region
- PCR / Sequencing error correction with or without aid of UMI or Cell barcodes
- Robust and dedicated aligner algorithms for maximum extration with zero false-positive rate
- Supports any custom barcode sequences architecture (UMI / Cell)
- _Human_, _Mice_, _Rat_, _Spalax_, _Alpaca_, _Monkey_
- Support IMGT reference
- Barcodes error-correction
- Adapter trimming
- Optional CDR3 reconstruction by assembling overlapping fragmented sequencing reads into complete CDR3-containing contigs when the read position is floating (e.g. shotgun-sequencing, RNA-Seq etc.)
- Optional contig assembly to build longest possible TCR/IG sequence from available data (with or without aid of UMI or Cell barcodes) 
- Comprehensive quality control reports provided at all the steps of the pipeline
- Regions not covered by the data may be imputed from germline
- Exhaustive output information for clonotypes and alignments:
    - nucleotide and amino acid sequences of all immunologically relevant regions (FR1, CDR1, ..., CDR3, etc..)
    - identified V, D, J, C genes
    - comprehensive information on nucleotide and amino acid mutations
    - positions of all immunologically relevant points in output sequences
    - and many more informative columns
- Ability to backtrack fate of each raw sequencing read through the whole pipeline 

## Who uses MiXCR 
MiXCR is used by 8 out of 10 world leading pharmaceutical companies in the R&D for:
- Vaccine development
- Antibody discovery
- Cancer immunotherapy research

Widely adopted by academic community with 1000+ citations in peer-reviewed scientific publications.

## Installation / Download

#### Using Homebrew on Mac OS X or Linux (linuxbrew)

    brew install milaboratory/all/mixcr
    
to upgrade already installed MiXCR to the newest version:

    brew update
    brew upgrade mixcr

[//]: # (#### Docker)

[//]: # ()
[//]: # (See [official Docker Image]&#40;https://hub.docker.com/r/milaboratory/mixcr&#41;.)

#### Manual install (any OS)

* download latest stable MiXCR build from [release page](https://github.com/milaboratory/mixcr/releases/latest)
* unzip the archive
* add resulting folder to your ``PATH`` variable
  * or add symbolic link for ``mixcr`` script to your ``bin`` folder
  * or use MiXCR directly by specifying full path to the executable script

#### Requirements

* Any OS with Java support (Linux, Windows, Mac OS X, etc..)
* Java 1.8 or higher

## Obtaining a license

To run MiXCR one need a license file. MiXCR is free for academic users with no commercial funding. We are committed to support academic community and provide our software free of charge for scientists doing non-profit research.

Academic users can quickly get a license at https://licensing.milaboratories.com.

Commercial trial license may be requested at https://licensing.milaboratories.com or by email to licensing@milaboratories.com.

To activate the license do one of the following:

- put `mi.license` to
    - `~/.mi.license`
    - `~/mi.license`
    - directory with `mixcr.jar` file
    - directory with MiXCR executable
    - to any place and specify it in `MI_LICENSE_FILE` environment variable
- put `mi.license` content to `MI_LICENSE` environment variable
- run `mixcr activate-license` and paste `mi.license` content to the command prompt



## Usage & documentation

See usage examples in the official documentation https://mixcr.readthedocs.io/en/master/quickstart.html

Detailed documentation can be found at https://mixcr.readthedocs.io/

If you haven't found the answer to your question in the docs, or have any suggestions concerning new features, feel free to create an issue here, on GitHub, or write an email to support@milaboratory.com .

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
  
  \
  (Files referenced in this paper can be found [here](https://github.com/milaboratory/mixcr/blob/develop/doc/paper/paperAttachments.md).)

  

* Dmitriy A. Bolotin, Stanislav Poslavsky, Alexey N. Davydov, Felix E. Frenkel, Lorenzo Fanchi, Olga I. Zolotareva, Saskia Hemmers, Ekaterina V. Putintseva, Anna S. Obraztsova, Mikhail Shugay, Ravshan I. Ataullakhanov, Alexander Y. Rudensky, Ton N. Schumacher & Dmitriy M. Chudakov. "Antigen receptor repertoire profiling from RNA-seq data." *Nature Biotechnology* 35, 908–911 (**2017**)


