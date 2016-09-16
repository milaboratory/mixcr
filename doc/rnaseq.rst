.. |br| raw:: html

   <br />

.. _ref-rna-seq:

Processing RNA-seq data
=======================

The typical MiXCR workflow can be applied for the analysis of RNA-seq samples. Though MiXCR can be used with the default parameters for aligning RNA-seq data, it is recommended to use ``rna-seq`` preset which is specifically tuned to perform well on such type of input:

::

    mixcr align --parameters rna-seq data_R1.fastq.gz data_R2.fastq.gz alignments.vdjca
    mixcr assemble alignments.vdjca clones.clns


Due to the small length of typical sequences obtained by the RNA-seq method, a tangible amount of alignments may be lost on the ``assemble`` step due the absence of V or J CDR3 part in the alignment (i.e. V CDR3 found but J CDR3 is too short to extract clonal sequence or vice versa). MiXCR allows to "rescue" such alignments by performing `partial assembling` of the alignments, i.e. to find overlapping sequences and merge them in order to extend possible alignemnts.

The typical workflow including `partial assembling` looks like:

::

    mixcr align --parameters rna-seq -OallowPartialAlignments=true data_R1.fastq.gz data_R2.fastq.gz alignments.vdjca
    mixcr assemblePartial alignments.vdjca alignmentsRescued.vdjca
    mixcr assemble alignmentsRescued.vdjca clones.clns

Note option ``-OallowPartialAlignments=true`` of the ``align`` command: it will preserve alignments with not fully aligned V and J parts in the ``.vdjca`` file. The ``assemblePartial`` action will then process theese "bad" alignments in order to find overlapping sequences and restore the full alignment. The following options are available for ``assemblePartial``:

+------------------------------+---------------+--------------------------------------------------------------+
| Parameter                    | Default value | Description                                                  |
+==============================+===============+==============================================================+
| ``kValue``                   | ``12``        | Length of k-mer taken from VJ junction region and used for   |
|                              |               | searching potentially overlapping sequences.                 |
+------------------------------+---------------+--------------------------------------------------------------+
| ``kOffset``                  | ``0``         | Offset taken from ``VEndTrimmed``/``JBeginTrimmed``.         |
+------------------------------+---------------+--------------------------------------------------------------+
| ``minimalVJJunctionOverlap`` | ``18``        | Minimal length of the overlapped VJ region: two squences can |
|                              |               | be potentially merged only if they has at least              |
|                              |               | ``minimalVJJunctionOverlap`` consequent same nucleotides     |
|                              |               | in the VJJunction region.                                    |
+------------------------------+---------------+--------------------------------------------------------------+



The above parameters can be specified in e.g. the following way:

::

    mixcr assemblePartial -OminimalVJJunctionOverlap=25 alignments.vdjca alignmentsRescued.vdjca


The algorithm which restores merged sequence from two overlapped alignments has the following parameters:

+-----------------------------+---------------------+--------------------------------------------------------------+
| Parameter                   | Default value       | Description                                                  |
+=============================+=====================+==============================================================+
| ``qualityMergingAlgorithm`` | ``SumSubtraction``  | Algorithm used for assigning quality of the merged read.     |
|                             |                     | Possible values: ``SumMax``, ``SumSubtraction``              |
+-----------------------------+---------------------+--------------------------------------------------------------+
| ``partsLayout``             | ``CollinearDirect`` | Relative orientation of paired reads.                        |
+-----------------------------+---------------------+--------------------------------------------------------------+
| ``minimalOverlap``          | ``20``              | Minimal length of the overlapped region.                     |
+-----------------------------+---------------------+--------------------------------------------------------------+
| ``maxQuality``              | ``45``              | Maximal sequence quality that can may be assigned in the     | 
|                             |                     | region of overlap.                                           |
+-----------------------------+---------------------+--------------------------------------------------------------+
| ``minimalIdentity``         | ``0.95``            | Minimal identity in the region of overlap.                   |
+-----------------------------+---------------------+--------------------------------------------------------------+


The above parameters can be specified in e.g. the following way:

::

    mixcr assemblePartial -OmergerParameters.minimalOverlap=15 alignments.vdjca alignmentsRescued.vdjca

