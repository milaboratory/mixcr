.. |br| raw:: html

   <br />

.. _ref-assembleContigs:

Assemble clone contigs
======================

After ``assembling`` clonotypes basing on a single gene feature (e.g. CDR3), MiXCR allows to collect and assemble all available off-``CDR3`` sequences from initial reads into wider contig. To do this, one must write ``assemble`` output in "cloes & alignments" (clna) format.

The following command performs mentioned procedure:

::

    mixcr assembleContigs -r report.txt clonesAndAlignments.clna contigs.clns

