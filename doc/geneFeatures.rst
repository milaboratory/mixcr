.. _ref-geneFeatures:
 
Gene features and anchor points
===============================

There are several immunologically important parts of TCR/BCR gene
(**gene features**). For example, such regions are three complementarity
determining regions (``CDR1``, ``CDR2`` and ``CDR3``), four framework
regions (``FR1``, ``FR2``, ``FR3`` and ``FR4``) etc.

The key feature of MiXCR is the possibility to specify:

-  regions of reference V, D, J and C genes sequences that are used in
   :ref:`alignment of raw reads <ref-align>`
-  regions of sequence to be exported by
   :ref:`exportAlignments <ref-export>`
-  regions of sequence to use as clonal sequence in :ref:`clone assembly <ref-assemble>`
-  regions of clonal sequences to be exported by
   :ref:`exportClones <ref-export>`

For convenience, in MiXCR these regions can be specified in terms of
above mentioned immunological gene features. The illustrated list of
predefined gene features can be found below. The set of possible gene
regions is not limited by this list:

-  boundary points of gene features (called **anchor points**) can be
   used to specify begin and end of custom gene regions
-  gene features can be concatenated (e.g. VTranscript =
   {V5UTRBegin:L1End}+{L2Begin:VEnd}).
-  offsets can be added or subtracted from original positions of
   **anchor points** to define even more custom gene regions (*for more
   detailed description see :ref:`gene feature syntax <ref-featureSyntax>`)*

Naming of gene features is based on IMGT convention described in
*Lefranc et al. (2003), Developmental & Comparative Immunology 27.1 (2003): 55-77*.

Germline features
-----------------

Features defined for germline genes are mainly used in
:ref:`align <ref-align>` and :ref:`export <ref-export>`.

V Gene structure
~~~~~~~~~~~~~~~~

.. figure:: _static/VStructure.svg

Additionally to core gene features in V region (like ``FR3``) we
introduce ``VGene``, ``VTranscript`` and ``VRegion`` for convenience.

D Gene structure
~~~~~~~~~~~~~~~~

.. figure:: _static/DStructure.svg


J Gene structure
~~~~~~~~~~~~~~~~

.. figure:: _static/JStructure.svg

Mature TCR/BCR gene features
----------------------------

Features described here (like ``CDR3``) cannot not be used for
:ref:`align <ref-align>`, since they are not defined for germline genes.

V(D)J junction structure
~~~~~~~~~~~~~~~~~~~~~~~~

Important difference between rearranged TCR/BCR sequence and germline
sequence of its segments lies in the fact that during V(D)J
recombination exact cleavage positions at the end of V gene, begin and
end of D gene and begin of J gene varies. As a result in most cases
actual ``VEnd``, ``DBegin``, ``DEnd`` and ``JBegin`` anchor positions
are not covered by alignment:

.. figure:: _static/VDJAlignmentStructure.svg

In order to use actual V, D, J gene boundaries we introduce four
additional anchor positions: ``VEndTrimmed``, ``DBeginTrimmed``,
``DEndTrimmed`` and ``JBeginTrimmed`` and several named gene features:
``VDJunction``, ``DJJunction`` and ``VJJunction``. On the following
picture one can see the structure of V(D)J junction:

.. figure::  _static/VDJJunctionStructure.svg

If D gene is not found in the sequence or is not present in target locus
(e.g. TRA), ``DBeginTrimmed`` and ``DEndTrimmed`` anchor points as well
as ``VDJunction`` and ``DJJunction`` gene features are not defined.

.. _ref-featureSyntax:

Gene feature syntax
-------------------

Syntax for gene features is the same everywhere. The best way to explain
it is by example:

-  to enter any gene feature mentioned above or listed in the next
   section just use its name: ``VTranscript``, ``CDR2``, ``V5UTR`` etc.
-  to define a gene feature consisting of several concatenated features
   use ``+``: ``V5UTR+L1+L2+VRegion`` is equivalent to ``VTranscript``
-  to create gene feature starting at anchor point ``X`` and ending at
   anchor point ``Y`` use {X:Y} syntax: ``{CDR3Begin:CDR3End}`` for
   ``CDR3``.
-  one can add or subtract offset from original position of anchor point
   using positive or negative integer value in brackets after anchor
   point name AnchorPoint(offset): ``{CDR3Begin(+3):CDR3End}`` for
   ``CDR3`` without first three nucleotides (coding conserved cysteine),
   ``{CDR3Begin(-6):CDR3End(+6)}`` for ``CDR3`` with 6 nucleotides
   downstream its left bound and 6 nucleotides upstream its right bound.
-  one can specify offsets for predefined gene feature boundaries using
   GeneFeatureName(leftOffset, rightOffset) syntax: ``CDR3(3,0)``,
   ``CDR3(-6,6)`` - equivalents of two examples from previous item
-  all syntax constructs can be combined:
   ``{L1Begin(-12):L1End}+L2+VRegion(0,+10)}``.

List of predefined gene features
--------------------------------

+----------------------------+--------------------------------------+
| Gene Feature Name          | Gene feature decomposition           |
+============================+======================================+
| VGene                      | {UTR5Begin:VEnd}                     |
+----------------------------+--------------------------------------+
| VDJTranscript              | {UTR5Begin:L1End}+{L2Begin:FR4End}   |
+----------------------------+--------------------------------------+
| V5UTR                      | {UTR5Begin:UTR5End}                  |
+----------------------------+--------------------------------------+
| VTranscript                | {UTR5Begin:L1End}+{L2Begin:VEnd}     |
+----------------------------+--------------------------------------+
| Exon1                      | {L1Begin:L1End}                      |
+----------------------------+--------------------------------------+
| L                          | {L1Begin:L1End}+{L2Begin:L2End}      |
+----------------------------+--------------------------------------+
| VTranscriptWithout5UTR     | {L1Begin:L1End}+{L2Begin:VEnd}       |
+----------------------------+--------------------------------------+
| VLIntronL                  | {L1Begin:L2End}                      |
+----------------------------+--------------------------------------+
| VDJTranscriptWithout5UTR   | {L1Begin:L1End}+{L2Begin:FR4End}     |
+----------------------------+--------------------------------------+
| Intron                     | {VIntronBegin:VIntronEnd}            |
+----------------------------+--------------------------------------+
| VExon2                     | {L2Begin:VEnd}                       |
+----------------------------+--------------------------------------+
| Exon2                      | {L2Begin:FR4End}                     |
+----------------------------+--------------------------------------+
| L2                         | {L2Begin:L2End}                      |
+----------------------------+--------------------------------------+
| VExon2Trimmed              | {L2Begin:VEndTrimmed}                |
+----------------------------+--------------------------------------+
| FR1                        | {FR1Begin:FR1End}                    |
+----------------------------+--------------------------------------+
| VRegionTrimmed             | {FR1Begin:VEndTrimmed}               |
+----------------------------+--------------------------------------+
| VRegion                    | {FR1Begin:VEnd}                      |
+----------------------------+--------------------------------------+
| VDJRegion                  | {FR1Begin:FR4End}                    |
+----------------------------+--------------------------------------+
| CDR1                       | {CDR1Begin:CDR1End}                  |
+----------------------------+--------------------------------------+
| FR2                        | {FR2Begin:FR2End}                    |
+----------------------------+--------------------------------------+
| CDR2                       | {CDR2Begin:CDR2End}                  |
+----------------------------+--------------------------------------+
| FR3                        | {FR3Begin:FR3End}                    |
+----------------------------+--------------------------------------+
| VCDR3Part                  | {CDR3Begin:VEndTrimmed}              |
+----------------------------+--------------------------------------+
| CDR3                       | {CDR3Begin:CDR3End}                  |
+----------------------------+--------------------------------------+
| GermlineVCDR3Part          | {CDR3Begin:VEnd}                     |
+----------------------------+--------------------------------------+
| ShortCDR3                  | {CDR3Begin(3):CDR3End(-3)}           |
+----------------------------+--------------------------------------+
| VDJunction                 | {VEndTrimmed:DBeginTrimmed}          |
+----------------------------+--------------------------------------+
| VJJunction                 | {VEndTrimmed:JBeginTrimmed}          |
+----------------------------+--------------------------------------+
| DRegion                    | {DBegin:DEnd}                        |
+----------------------------+--------------------------------------+
| DCDR3Part                  | {DBeginTrimmed:DEndTrimmed}          |
+----------------------------+--------------------------------------+
| DJJunction                 | {DEndTrimmed:JBeginTrimmed}          |
+----------------------------+--------------------------------------+
| GermlineJCDR3Part          | {JBegin:CDR3End}                     |
+----------------------------+--------------------------------------+
| JRegion                    | {JBegin:FR4End}                      |
+----------------------------+--------------------------------------+
| JRegionTrimmed             | {JBeginTrimmed:FR4End}               |
+----------------------------+--------------------------------------+
| JCDR3Part                  | {JBeginTrimmed:CDR3End}              |
+----------------------------+--------------------------------------+
| FR4                        | {FR4Begin:FR4End}                    |
+----------------------------+--------------------------------------+
| CExon1                     | {CBegin:CExon1End}                   |
+----------------------------+--------------------------------------+
| CRegion                    | {CBegin:CEnd}                        |
+----------------------------+--------------------------------------+