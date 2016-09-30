Appendix
========

TCR/BCR refenrece sequences library
-----------------------------------

Default list and sequences of ``V``, ``D``, ``J`` and ``C`` genes used
by MiXCR are taken from GenBank. Accession numbers of records used for
each locus are listed in the following table:

+-----------------+-----------------+-------------+
+-----------------+-----------------+-------------+
| |               | ``TRA``/``TRD`` | NG_001332.2 |
|                 +-----------------+-------------+
|                 | ``TRB``         | NG_001333.2 |
|                 +-----------------+-------------+
|                 | ``TRG``         | NG_001336.2 |
|                 +-----------------+-------------+
| *Homo sapiens*  | ``IGH``         | NG_001019.5 |
|                 +-----------------+-------------+
|                 | ``IGK``         | NG_000834.1 |
|                 +-----------------+-------------+
|                 | ``IGL``         | NG_000002.1 |
+-----------------+-----------------+-------------+
+-----------------+-----------------+-------------+
| |               | ``TRA``/``TRD`` | NG_007044.1 |
|                 +-----------------+-------------+
|                 | ``TRB``         | NG_006980.1 |
|                 +-----------------+-------------+
|                 | ``TRG``         | NG_007033.1 |
| *Mus musculus*  +-----------------+-------------+
|                 | ``IGH``         | NG_005838.1 |
|                 +-----------------+-------------+
|                 | ``IGK``         | NG_005612.1 |
|                 +-----------------+-------------+
|                 | ``IGL``         | NG_004051.1 |
+-----------------+-----------------+-------------+



.. _ref-encoding:

Alignment and mutations encoding
--------------------------------

MiXCR outputs alignments in ``exportClones`` and ``exportAlignments`` as
a list of 7 fields separated by ``|`` symbol as follows:

``targetFrom | targetTo | targetLength | queryFrom | queryTo | mutations | alignmentScore``

where

-  ``targetFrom`` - position of first aligned nucleotide in **target
   sequence** (sequence of gene feature from reference V, D, J or C
   gene used in alignment; e.g. ``VRegion`` in TRBV12-2); this
   boundary is inclusive
-  ``targetTo`` - next position after last aligned nucleotide in **target
   sequence**; this boundary is exclusive
-  ``targetLength`` - length of **target sequence** (e.g. length of
   ``VRegion`` in TRBV12-2)
-  ``queryFrom`` - position of first aligned nucleotide in **query
   sequence** (sequence of sequencing read or clonal sequence); this
   boundary is inclusive
-  ``queryTo`` - next position after last aligned nucleotide in **query
   sequence**; this boundary is exclusive
-  ``mutations`` - list of mutations from **target sequence** to **query
   sequence** (see below)
-  ``alignmentScore`` - score of alignment

*all positions are zero-based (i.e. first nucleotide has index 0)*

Mutations are encoded as a list of single-nucleotide edits (similar to
what is used in definition of Levenshtein distance, i.e. insertions,
deletions or substitutions); if one apply these mutations to aligned
subsequence of **target sequence**, one will obtain aligned
subsequence of **query sequence**.


Each single mutation (single-nucleotide edit) is encoded in the
following way (without any spaces; some fields may absent in some cases,
see description):

``type`` [``fromNucleotide``] ``position`` [``toNucleotide]``

-  type of mutation (one letter):
-  ``S`` for substitution
-  ``D`` for deletion
-  ``I`` for insertion
-  fromNucleotide is a nucleotide in **target sequence** affected by
   mutation (applicable only for substitutions and deletions; absent for
   insertions)
-  position is a zero-based absolute position in **target sequence**
   affected by mutation; for insertions denotes position in **target
   sequence** right after inserted nucleotide
-  toNucleotide nucleotide after mutation (applicable only for
   substitutions and insertions; absent for deletions)

**Note**, that for deletions and substitutions

``targetSequence[position] == fromNucleotide``

i.e. target sequence always have fromNucleotide at position position;
for insertions fromNucleotide field is absent

Here are several examples of single mutations:

-  ``SA4T`` - substitution of ``A`` at position ``4`` to ``T``

-  ``DC12`` - deletion of ``C`` at position ``12``

-  ``I15G`` - insertion of ``G`` before position ``15``

Consider the following BLAST-like alignments encoded in MiXCR notation:

-  Alignment without mutation

   .. raw:: html

      <pre style="font-size: 11px">
      target = TTGTGCTGACAGATACCCC
      query  = CGAGTGCTGACAGATACCGTCGATGCT
      
      BLAST like alignment:
      2 GTGCTGACAGATACC 16
        |||||||||||||||
      3 GTGCTGACAGATACC 17
      
      MiXCR alignment:
      2|17|19|3|18||75.0
      </pre>

subsequence from ``target`` (from nucleotide 0 to nucleotide 15) was
found to be identical to susequence from ``query`` (from nucleotide 3 to
nucleotide 18).

-  Alignment with mutation

   .. raw:: html

      <pre style="font-size: 11px">
      target = TTGTGCTGACAGATACCCC
      query  = CGAGTGCTATAGACTACCGTCGATGCT
      
      BLAST like alignment:
      2 GTGCTGACAGA-TACC 16
        ||||| | ||| ||||
      3 GTGCT-ATAGACTACC 17
      
      MiXCR alignment:
      2|17|19|3|18|DG7SC9TI13C|41.0
      </pre>

so, to obtain subseqeunce from **query sequence** from 3 to 18 we need
to apply the following mutations to subsequence of **target sequence**
from 2 to 16: - deletion of ``G`` at position ``7`` - substitution of
``C`` at position ``9`` to ``T`` - insertion of ``C`` before at position
``13``
