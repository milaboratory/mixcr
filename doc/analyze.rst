.. |br| raw:: html

   <br />

.. include:: <isonum.txt>
.. _ref-analyze:

``analyze``: single command to run complicated pipelines
========================================================

The ``analyze`` command packs a complicated execution pipelines into a single command. It is suitable for a wide range of input library types. Under the hood it runs all required MiXCR actions (:ref:`align <ref-align>`, :ref:`assemblePartial <ref-assemblePartial>`, :ref:`extend <ref-extend>`, :ref:`assemble <ref-assemble>`, :ref:`assembleContigs <ref-assembleContigs>` and :ref:`export <ref-export>`) inferring correct :ref:`aligner <ref-align>` and :ref:`assembler <ref-assemble>` parameters from the type of the input library.

Generally, there two distinct types of library preparation which correspond to the two ``analyze`` pipelines:

 - ``analyze amplicon`` for analysis of targeted TCR/IG library amplification (5'RACE, Amplicon, Multiplex, etc).
 - ``analyze shotgun`` for analysis of random fragments (RNA-Seq, Exome-Seq, etc).


.. _ref-analyze-amplicon:

Analysis of targeted TCR/IG libraries
-------------------------------------

The command ``analyze amplicon`` implements the pipeline for the analysis of enriched targeted TCR/IG libraries (5'RACE, Amplicon, Multiplex, etc). The pipeline includes alignment of raw sequencing reads using :ref:`align <ref-align>`, assembly of aligned sequences into clonotypes using :ref:`assemble <ref-assemble>` and exporting the resulting clonotypes into tab-delimited file using :ref:`export <ref-export>`. Optionally, it also assembles full receptor sequences using :ref:`assembleContigs <ref-assembleContigs>`. It has the following syntax:

.. code-block:: bash

    mixcr analyze amplicon
        -s <species> \
        --starting-material <startingMaterial> \
        --5-end <5End> --3-end <3End> \
        --adapters <adapters> \
        [OPTIONS] input_file1 [input_file2] analysis_name


The following table lists the required options for ``analyze amplicon`` command. This set of high-level options unambiguously determines all parameters of the underline MiXCR pipeline.

.. list-table::
    :widths: 15 30
    :header-rows: 1

    * - Option
      - Description

    * - ``-s``, ``--species``
      - Species (organism). Possible values: ``hsa`` (or ``HomoSapiens``), ``mmu`` (or ``MusMusculus``), ``rat`` (currently only ``TRB``, ``TRA`` and ``TRD`` are supported), or any species from IMGT |reg| library, if it is used (see here :ref:`import segments <ref-importSegments>`)

    * - ``--starting-material``
      - Type of starting material. Two  values possible: ``rna`` (RNA) and ``dna`` (DNA).

    * - ``--5-end``
      -  5'-end of the library. There are two possible values: ``no-v-primers`` --- no V gene primers (e.g. 5'RACE with template switch oligo or a like), ``v-primers`` --- V gene single primer / multiple.
      
    * - ``--3-end``
      -  3'-end of the library. There are three possible values: ``j-primers`` --- J gene single primer / multiplex, ``j-c-intron-primers`` --- J-C intron single primer / multiplex, ``c-primers`` --- C gene single primer / multiplex (e.g. IGHC primers specific to different immunoglobulin isotypes).

    * - ``--adapters``
      - Presence of PCR primers and/or adapter sequences. If sequences of primers used for PCR or adapters are present in sequencing data, it may influence the accuracy of V, J and C gene segments identification and CDR3 mapping. There are two possible values: ``adapters-present`` (adapters may be present) and 
        ``no-adapters`` (absent or nearly absent or trimmed).
        

The following parameters are optional:


.. list-table::
    :widths: 15 10 30
    :header-rows: 1

    * - Option
      - Default
      - Description

    * - ``--report``
      - ``analysis_name.report``
      - Report file.

    * - ``--receptor-type``
      - ``xcr``
      - Dedicated receptor type for analysis. By default, all T- and B-cell receptor chains are analyzed. MiXCR has special aligner ``kAligner2``, which is used when B-cell receptor type is selected. Possible values for ``--receptor-type`` are: ``xcr`` (all chains), ``tcr``, ``bcr``, ``tra``, ``trb``, ``trg``, ``trd``, ``igh``, ``igk``, ``igl``.

    * - ``--contig-assembly``
      - ``false``
      - Whether to assemble full receptor sequences (:ref:`assembleContigs <ref-assembleContigs>`). This option may slow down the computation.

    * - ``--export-germline``
      - ``false``
      - Use germline segments (printed with lowercase letters) for uncovered gene features.
      
    * - ``--region-of-interest``
      - ``CDR3``
      - MiXCR will use only reads covering the whole target region; reads which partially cover selected region will be dropped during clonotype assembly. All non-CDR3 options require long high-quality paired-end data. See :ref:`ref-geneFeatures` for details.

    * - ``--only-productive``
      - ``false``
      - Filter out-of-frame and stop-codons in export
    
    * - ``--align``
      -
      -  Additional parameters for :ref:`align <ref-align>` step specified with double quotes (e.g --align "--limit 1000" --align "-OminSumScore=100")
    
    * - ``--assemble``
      -
      -  Additional parameters for :ref:`assemble <ref-assemble>` step specified with double quotes (e.g --assemble "-ObadQualityThreshold=0").
      
    * - ``--assembleContigs``
      -
      -  Additional parameters for :ref:`assembleContigs <ref-assembleContigs>` step specified with double quotes.

    * - ``--export``
      -
      -  Additional parameters for :ref:`exportClones <ref-export>` step specified with double quotes.


The complete help information information can be obtained via

::

    mixcr analyze help amplicon



.. _ref-analyze-shotgun:

Analysis of non-enriched or random fragments
--------------------------------------------

The command ``analyze amplicon`` implements the pipeline for the analysis of non-enriched RNA-seq and non-targeted genomic data. The pipeline includes alignment of raw sequencing reads using :ref:`align <ref-align>`, assembly of overlapping fragmented reads using :ref:`assemblePartial <ref-assemblePartial>`, imputing good TCR alignments using :ref:`extend <ref-rna-seq>`,  assembly of aligned sequences into clonotypes using :ref:`assemble <ref-assemble>` and exporting the resulting clonotypes into tab-delimited file using :ref:`export <ref-export>`. Optionally, it also assembles full receptor sequences using :ref:`assembleContigs <ref-assembleContigs>`. It has the following syntax:

.. code-block:: bash

    mixcr analyze shotgun
        -s <species> \
        --starting-material <startingMaterial> \
        [OPTIONS] input_file1 [input_file2] analysis_name


There are two required options:

.. list-table::
    :widths: 15 30
    :header-rows: 1

    * - Option
      - Description

    * - ``-s``, ``--species``
      - Species (organism). Possible values: ``hsa`` (or ``HomoSapiens``), ``mmu`` (or ``MusMusculus``), ``rat`` (currently only ``TRB``, ``TRA`` and ``TRD`` are supported), or any species from IMGT |reg| library, if it is used (see here :ref:`import segments <ref-importSegments>`)

    * - ``--starting-material``
      - Type of starting material. Two  values possible: ``rna`` (RNA) and ``dna`` (DNA).


The following parameters are optional:


.. list-table::
    :widths: 15 10 30
    :header-rows: 1

    * - Option
      - Default
      - Description

    * - ``--report``
      - ``analysis_name.report``
      - Report file.

    * - ``--receptor-type``
      - ``xcr``
      - Dedicated receptor type for analysis. By default, all T- and B-cell receptor chains are analyzed. MiXCR has special aligner ``kAligner2``, which is used when B-cell receptor type is selected. Possible values for ``--receptor-type`` are: ``xcr`` (all chains), ``tcr``, ``bcr``, ``tra``, ``trb``, ``trg``, ``trd``, ``igh``, ``igk``, ``igl``.

    * - ``--contig-assembly``
      - ``false``
      - Whether to assemble full receptor sequences (:ref:`assembleContigs <ref-assembleContigs>`). This option may slow down the computation.

    * - ``--export-germline``
      - ``false``
      - Use germline segments (printed with lowercase letters) for uncovered gene features.
  
    * - ``--only-productive``
      - ``false``
      - Filter out-of-frame and stop-codons in export
    
    * - ``--assemble-partial-rounds``
      - ``2``
      - Number of consequent ``assemblePartial`` executions.

    * - ``--do-not-extend-alignments``
      - 
      - Do not perform extension of good TCR alignments.

    * - ``--align``
      -
      -  Additional parameters for :ref:`align <ref-align>` step specified with double quotes (e.g --align "--limit 1000" --align "-OminSumScore=100")
    
    * - ``--assemblePartial``
      -
      -  Additional parameters for :ref:`assemblePartial <ref-assemblePartial>` step specified with double quotes.
    
    * - ``--extend``
      -
      -  Additional parameters for :ref:`extend <ref-rna-seq>` step specified with double quotes.
    
    * - ``--assemble``
      -
      -  Additional parameters for :ref:`assemble <ref-assemble>` step specified with double quotes (e.g --assemble "-ObadQualityThreshold=0").
      
    * - ``--assembleContigs``
      -
      -  Additional parameters for :ref:`assembleContigs <ref-assembleContigs>` step specified with double quotes.

    * - ``--export``
      -
      -  Additional parameters for :ref:`exportClones <ref-export>` step specified with double quotes.



The complete help information information can be obtained via

::

    mixcr analyze help shotgun


Resuming execution / re-analyzing
---------------------------------

MiXCR allows to continue aborted execution or re-analyze existing data with updated parameters, without complete re-processing of each of pipeline steps. This is possible with ``--resume`` option. For example, suppose the analysis was performed with the default options:

::
    
    mixcr analyze shotgun --species hs --starting-material rna data_R1.fastq data_R2.fastq analysis_name


Now to re-analyze the data with updated options for :ref:`assemble <ref-assemble>`, one can pass ``--resume`` option in order to avoid unnecessary invocation of ``align``, ``assemblePartial`` and ``extend``:

:: 

    mixcr analyze shotgun --force --resume --species hs --starting-material rna --assemble "-ObadQualityThreshold=0" data_R1.fastq data_R2.fastq analysis_name

This way, the previous results of :ref:`align <ref-align>`, :ref:`assemblePartial <ref-assemblePartial>` and :ref:`extend <ref-extend>` will be used, while :ref:`assembly step <ref-assemble>` will be re-executed.

