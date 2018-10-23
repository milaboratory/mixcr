.. |br| raw:: html

   <br />

.. _ref-assembleContigs:

Assemble full TCR/Ig receptor sequences
=======================================

.. tip:: 

  MiXCR provides :ref:`analyze <ref-analyze>` command that packs a complicated execution pipelines (alignment, assembly, exporting etc.) into a single command. We recommend to use :ref:`analyze <ref-analyze>` for most types of input libraries instead of manual execution of all MiXCR analysis steps. To assemble full TCR/IG receptor sequences with :ref:`analyze <ref-analyze>` command, one should simply use ``--contig-assembly`` option of :ref:`analyze <ref-analyze>`.

MiXCR allows to assemble full TCR/Ig receptor sequences (that is all available off-``CDR3`` regions) with the use of ``assembleContigs`` command. Full sequence assembly may be performed after building of initial alignments and assembly of ordinary CDR3-based clonotypes. The typical workflow for full receptor assembly of e.g. mouse B-cells may be the following:

.. code-block:: bash

    # align raw sequences 
    mixcr align --species mmu -p kAligner2 --report report.txt input_R1.fq input_R2.fq alignments.vdjca

    # assemble default CDR3 clonotypes (note: --write-alignments is required for further contig assembly)
    mixcr assemble --write-alignments --report report.txt alignments.vdjca clones.clna

    # assemble full BCR receptors
    mixcr assembleContigs --report report.txt clones.clna full_clones.clns

    # export full BCR receptors
    mixcr exportClones -c IG -p fullImputed full_clones.clns full_clones.txt


Note that at :ref:`assembly <ref-assemble>` stage we specified ``--write-alignments`` option that enables ``.clna`` file format for storing clones and alignments to clones mapping. This mapping is used then by the ``assembleContig`` algorithms. The output of ``assembleContig`` is a standard binary file with clonotypes (``.clns``). To export the full information about assembled full IG receptor sequences it is recommended to use the option ``-p fullImputed`` in ``exportClones``. With this option the germline nucleotide sequences will be used for uncovered regions of gene features (marked lowercase). The output will look like:

+---------+---------------+-----+---------------------------+-----+-----------------+-----+
| cloneId | cloneFraction | ... | aaSeqImputedFR1           | ... | aaSeqCDR3       | ... |
+=========+===============+=====+===========================+=====+=================+=====+
| 0       | 0.061         | ... | qvqlqqwgagllkpsetlslTCAVY | ... | CARKKLEGRFDYW   | ... |
+---------+---------------+-----+---------------------------+-----+-----------------+-----+
| 1       | 0.054         | ... | qvqlvesgggvvqpgrslrlscaAS | ... | CARQGQA_*RQVDPW | ... |
+---------+---------------+-----+---------------------------+-----+-----------------+-----+
| ...     | ...           | ... | ...                       | ... | ...             | ... |
+---------+---------------+-----+---------------------------+-----+-----------------+-----+

To print help for ``assembleContigs`` use:

::

    mixcr help assembleContigs


.. _ref-assembleContigs-cli-params:

Full sequence assembler parameters
----------------------------------


To pass specific option for the full sequence assembler use the following syntax:

::

    mixcr assembleContigs -Oparameter=value input.clna output.clns


The following options are available:

+------------------------------------------+---------------+------------------------------------------------------------------------+
| Parameter                                | Default value | Description                                                            |
+==========================================+===============+========================================================================+
| ``subCloningRegion``                     | ``CDR3``      | Region where variants are allowed                                      |
+------------------------------------------+---------------+------------------------------------------------------------------------+
| ``minimalContigLength``                  | ``20``        | Minimal contiguous sequence length                                     |
+------------------------------------------+---------------+------------------------------------------------------------------------+
| ``alignedRegionsOnly``                   | ``false``     | Assemble only parts of sequences covered by alignments                 |
+------------------------------------------+---------------+------------------------------------------------------------------------+
| ``branchingMinimalQualityShare``         | ``0.1``       | Minimal quality fraction (variant may be marked significant            |
|                                          |               | if ``variantQuality > totalSumQuality * branchingMinimalQualityShare`` |
+------------------------------------------+---------------+------------------------------------------------------------------------+
| ``branchingMinimalSumQuality``           | ``80``        | Minimal variant quality threshold (variant may be marked significant   |
|                                          |               | if ``variantQuality > branchingMinimalSumQuality``                     |
+------------------------------------------+---------------+------------------------------------------------------------------------+
| ``decisiveBranchingSumQualityThreshold`` | ``120``       | Variant quality that guaranties that variant will be marked            |
|                                          |               | significant (even if other criteria are not satisfied)                 |
+------------------------------------------+---------------+------------------------------------------------------------------------+
| ``outputMinimalQualityShare``            | ``0.5``       | Positions having quality share less then this value, will not be       |
|                                          |               | represented in the output                                              |
+------------------------------------------+---------------+------------------------------------------------------------------------+
| ``outputMinimalSumQuality``              | ``50``        | Positions having sum quality less then this value, will not be         |
|                                          |               | represented in the output                                              |
+------------------------------------------+---------------+------------------------------------------------------------------------+
| ``alignedSequenceEdgeDelta``             | ``3``         | Maximal number of not aligned nucleotides at the edge of sequence so   |
|                                          |               | that sequence is still considered aligned "to the end"                 |
+------------------------------------------+---------------+------------------------------------------------------------------------+
| ``alignmentEdgeRegionSize``              | ``7``         | Number of nucleotides at the edges of alignments (with almost fully    |
|                                          |               | aligned seq2) that are "not trusted"                                   |
+------------------------------------------+---------------+------------------------------------------------------------------------+
| ``minimalNonEdgePointsFraction``         | ``0.25``      | Minimal fraction of non edge points in variant that must be reached to |
|                                          |               | consider the variant significant                                       |
+------------------------------------------+---------------+------------------------------------------------------------------------+

