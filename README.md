## Overview

MiXCR is a universal software for fast and accurate analysis of raw T- or B- cell receptor repertoire sequencing data.

## Installation / Download

#### Using Homebrew on Mac OS X or Linux (linuxbrew)

    brew tap milaboratory/all
    brew install mixcr

to upgrade already installed MiXCR to the newest version:

    brew update
    brew upgrade mixcr

#### Manual install (any OS)

* download latest MiXCR version from [release page](https://github.com/milaboratory/mixcr/releases/latest)
* unzip the archive
* add resulting folder to your ``PATH`` variable
  * or add symbolic link for ``mixcr`` script to your ``bin`` folder
  * or use MiXCR directly by specifying full path to the executable script

#### Requirements

* Any OS with Java support (Linux, Windows, Mac OS X, etc..)
* Java 1.7 or higher
 
## Usage

Here is a very simple example of analysis of raw human RepSeq data:

    mixcr align -r log.txt input_R1.fastq.gz input_R2.fastq.gz alignments.vdjca
    mixcr assemble -r log.txt alignments.vdjca clones.clns
    mixcr exportClones clones.clns clones.txt
  
this sequence of commands will produce a tab-delimited list of clones (`clones.txt`) assembled by their CDR3 sequences with extensive information on their abondancies, V, D and J genes etc.

For more details see documentation.

## Documentation

Detailed documentation can be found at https://mixcr.readthedocs.org/

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

Bolotin, Dmitriy A., Stanislav Poslavsky, Igor Mitrophanov, Mikhail Shugay, Ilgar Z. Mamedov, Ekaterina V. Putintseva, and Dmitriy M. Chudakov. "MiXCR: software for comprehensive adaptive immunity profiling." *Nature methods* 12, no. 5 (**2015**): 380-381.

## Files referenced in original paper

can be found [here](https://github.com/milaboratory/mixcr/blob/develop/doc/paper/paperAttachments.md).
