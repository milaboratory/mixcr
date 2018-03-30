.. |br| raw:: html

   <br />

.. _ref-rna-seq:

Processing RNA-seq data
=======================

.. note::
  
  The procedure described here applies to analysis of sequencing data from non-enriched and/or randomly-shred (c)DNA libraries, like `RNA-Seq <https://en.wikipedia.org/wiki/RNA-Seq>`_. For analysis of targeted RepSeq data, please see :ref:`examples <ref-examples>`  from quick start.


Overview
--------

Analysis method and quirks described here will also be useful for users who want to extract TCR or Ig repertoire from sequencing data of any other type of non-enriched or randomly shred cDNA / gDNA library.

There are two main challenges of repertoire extraction from non-enriched and randomly-shred libraries:

  - **Extraction and alignment of fragments of target molecules.** This procedure must be *sensitive* enough to detect and align sequences with very small parts of V or J genes, but at the same time must be very *selective* not to align non-target sequences homologous to TCR or Ig. Alignment of such sequences and treating them as TCRs or IGs bring a risk of introducing reproducible false-positive clonotypes into resulting clonesets, and may, in turn, lead to detection of false intersections between unlinked repertoires.
  
    MiXCR has a special set of alignment parameters (``-p rna-seq``), which was specifically optimized, and automatically and manually checked on tens of different datasets to give the best possible sensitivity keeping zero false-positive rate. |br| |br|
  
  - **Assembly of overlapping fragmented sequencing reads into long-enough CDR3 containing contigs.** In contrast to sequencing reads from targeted IG or TCR libraries with very determined CDR3 position, reads from randomly shred libraries may cover only a part of CDR3. This fact is especially true for short-read data (like very common 50+50 RNA-Seq), where most part of target sequences only partially cover CDR3. In order to efficiently extract repertoire from such data one have to reconstruct initial CDR3s from fragments scattered all over the initial sequencing dataset. The main challenge of this procedure is, again, the possibility to introduce false-positive clones, namely to perform an overlap between two sequences from different clones. This false positives are not so dangerous as those described in the previous paragraph, but still may introduce certain biases. The problem is that it is very easy to make such false-overlaps as TCR or IG sequences consist mainly from conservative V, D and J regions. So overlapping must be done very carefully, taking into account the positions of all conserved regions.

    MiXCR has a special action to perform such an assembly of reads, partially covering CDR3 - ``assemblePartial``. Basically it performs an overlap of already aligned reads from `*.vdjca` file, realigns resulting contig, and checks if initial overlap has covered enough part of a non-template N region. Default thresholds in this procedure were optimized to assemble as many contigs as possible while producing zero false overlaps (no false overlaps were detected in all of the benchmarks we have performed).

.. _ref-rna-seq-extend-description:

In case of short reads input, even after ``assemblePartial`` many contigs/reads still only partially cover CDR3. A substantial fraction of such contigs needs only several nucleotides on the 5' or the 3' end to fill up the sequence up to a complete CDR3. These sequence parts can be taken from the germline, if corresponding V or J gene for the contig is uniquely determined (e.g. from second mate of a read pair). Such procedure is not safe for IGs, because of hypermutations, but for TCRs which have relatively conservative sequence near conserved ``Cys`` and ``Phe``/``Trp``, it can reconstruct additional clonotypes with relatively small chance to introduce false ones. Described procedure is implemented in the action `extendAlignments`, by default it acts only on TCR sequences.


Analysis pipeline
-----------------

MiXCR has all of the steps required to efficiently extract repertoire data from RNA-Seq and similar sequencing datasets, starting from raw ``fastq(.gz)`` files to final list of clonotypes for each immunological chain (``TRB``, ``IGH``, etc..).

All default values for analysis parameters were carefully optimized, and should be suitable for most of the use-cases.

Prerequisite
^^^^^^^^^^^^

There are only two things you must tell MiXCR for a successfull analysis. Both on the first ``align`` step.

1. **Species.** Using ``-s ...`` parameter. See :ref:`here <ref-align-cli-params>`.

2. **Data source origin**. Genomic or transcriptomic. This affects which part of reference V gene seqeucnes will be used for alignment, with or without intron. By default transcriptomic source is assumed, so no additional parameters have to be specified for an analysis of RNA-Seq data. If your data has a genomic DNA origin add the following option to the ``align`` command:

  ::

      -OvParameters.geneFeatureToAlign=VGeneWithP


  This option tells MiXCR to use unspliced reference sequences of V genes for alignments.


Typical analysis workflow
^^^^^^^^^^^^^^^^^^^^^^^^^

1. Align sequencing reads against reference V, D, J and C genes.

  ::

    mixcr align -p rna-seq -s hsa -OallowPartialAlignments=true data_R1.fastq.gz data_R2.fastq.gz alignments.vdjca

  For single-end data simply specify single input file:

  ::

    mixcr align -p rna-seq -s hsa -OallowPartialAlignments=true data.fastq.gz alignments.vdjca

  If your data has a genomic origin add ``-OvParameters.geneFeatureToAlign=VGeneWithP`` option.

  ``-OallowPartialAlignments=true`` option is needed to prevent MiXCR from filtering out partial alignments, that don't fully cover CDR3 (the default behaviour while processing targeted RepSeq data). MiXCR will try to assemble contigs using those alignments and reconstruct their full CDR3 sequence on the next step.


2. Perform two rounds of contig assembly (please see :ref:`here <ref-assemblePartial>` for available parameters).

  ::

    mixcr assemblePartial alignments.vdjca alignments_rescued_1.vdjca
    mixcr assemblePartial alignments_rescued_1.vdjca alignments_rescued_2.vdjca

3. (optional) Perform extension of incomplete TCR CDR3s with uniquely determined V and J genes using germline sequences. As described in the :ref:`last paragraph of introduction <ref-rna-seq-extend-description>`

  ::

     mixcr extendAlignments alignments_rescued_2.vdjca alignments_rescued_2_extended.vdjca

4. Assemble (see :ref:`here <ref-assemble>` for details) clonotypes

  ::

    mixcr assemble alignments_rescued_2_extended.vdjca clones.clns

5. Export (see :ref:`here <ref-export>` for details) all clonotypes:

  ::

    mixcr exportClones clones.clns clones.txt


  or clonotypes for a specific immunological chain:

  ::

    mixcr exportClones -c TRB clones.clns clones.TRB.txt
    mixcr exportClones -c IGH clones.clns clones.IGH.txt
    ...

  The resulting ``*.txt`` files will contain clonotypes along with comprehansive biological information like V, D, J and C genes, clone abundances, etc...

.. _ref-assemblePartial:

``assemblePartial`` action
--------------------------

The following options are available for ``assemblePartial``:

+------------------------------+---------------+--------------------------------------------------------------+
| Parameter                    | Default value | Description                                                  |
+==============================+===============+==============================================================+
| ``kValue``                   | ``12``        | Length of k-mer taken from VJ junction region and used for   |
|                              |               | searching potentially overlapping sequences.                 |
+------------------------------+---------------+--------------------------------------------------------------+
| ``kOffset``                  | ``-7``        | Offset taken from ``VEndTrimmed``/``JBeginTrimmed``.         |
+------------------------------+---------------+--------------------------------------------------------------+
| ``minimalAssembleOverlap``   | ``12``        | Minimal length of the overlapped VJ region: two sequences    |
|                              |               | can be potentially merged only if they have at least         |
|                              |               | ``minimalAssembleOverlap``-wide overlap in the VJJunction    |
|                              |               | region. No mismatches are allowed in the overlapped region.  |
+------------------------------+---------------+--------------------------------------------------------------+
| ``minimalNOverlap``          | ``5``         | Minimal number of non-template nucleotides (N region) that   |
|                              |               | overlap region must cover to accept the overlap.             |
+------------------------------+---------------+--------------------------------------------------------------+

The above parameters can be specified in e.g. the following way:

::

    mixcr assemblePartial -OminimalAssembleOverlap=10 alignments.vdjca alignmentsRescued.vdjca


.. The algorithm which restores merged sequence from two overlapped alignments has the following parameters:

.. +-----------------------------+---------------------+--------------------------------------------------------------+
.. | Parameter                   | Default value       | Description                                                  |
.. +=============================+=====================+==============================================================+
.. | ``qualityMergingAlgorithm`` | ``SumSubtraction``  | Algorithm used for assigning quality of the merged read.     |
.. |                             |                     | Possible values: ``SumMax``, ``SumSubtraction``              |
.. +-----------------------------+---------------------+--------------------------------------------------------------+
.. | ``minimalOverlap``          | ``20``              | Minimal length of the overlapped region.                     |
.. +-----------------------------+---------------------+--------------------------------------------------------------+
.. | ``maxQuality``              | ``45``              | Maximal sequence quality that can may be assigned in the     | 
.. |                             |                     | region of overlap.                                           |
.. +-----------------------------+---------------------+--------------------------------------------------------------+
.. | ``minimalIdentity``         | ``0.95``            | Minimal identity in the region of overlap.                   |
.. +-----------------------------+---------------------+--------------------------------------------------------------+


.. The above parameters can be specified in e.g. the following way:

..     mixcr assemblePartial -OmergerParameters.minimalOverlap=15 alignments.vdjca alignmentsRescued.vdjca

