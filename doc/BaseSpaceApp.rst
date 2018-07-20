.. |br| raw:: html

   <br />

.. _ref-basespace:

MiXCR Immune Repertoire Analyzer @ BaseSpace®
=============================================

If your data is deposited at the BaseSpace cloud platform you can perform repertoire extraction and calculate common analysis metrics like V/J usage, without leaving BaseSpace, with the use of MiXCR Immune Repertoire Analyzer app (this `link <https://basespace.illumina.com/apps/5538533/MiXCR-Immune-Repertoire-Analyzer>`_ works if you are logged in into base space, and `this one <https://www.illumina.com/products/by-type/informatics-products/basespace-sequence-hub/apps/milaboratory-mixcr-immune-repertoire-analyzer.html>`_ points to the overview page available without authentication).

Input
-----

User interface of MiXCR Immune Repertoire Analyzer was specifically optimized to set up best possible analysis pipeline with the minimal possible set of parameters, covering most part of the sequencing data types TCR/IG repertoires can be extracted from.

The list of possible sequencing material sources include, but is not limited to:

  - all targeted TCR and IG profiling protocols (both RNA- and gDNA-derived, including multiplex-PCR and 5'RACE based techniques)
  - non-enriched RNA-Seq data
  - WGS
  - single-cell data

Parameters
----------

Starting material
^^^^^^^^^^^^^^^^^

Sets the type of starting material (RNA or Genomic DNA) of the library. This determines whether MiXCR will look for V intron.

Library type
^^^^^^^^^^^^

This option determines whether the data will be treated as amplicon library with the same relative sequence architecture across all reads, where CDR3 is fully covered by each reads. Or, randomly shred library like RNA-Seq or WGS, so MiXCR will perform assembly of target molecules from several reads.

"Random fragments" option works well for shallow libraries like RNA-Seq of solid tissue or sorted cell population, but if the library is too reach with the target molecules (e.g. library was additionally enriched using bait probes), using this option may drastically degrade both computational and "analytical" performance. In this case, select "Targeted TCR/IG library", no partial CDR3 assembly will be performed, still sequences extracted from reads with full coverage of CDR3, should be enough for the analysis.

Targeted Library Parameters: 5’-end of the library
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you specify the library may contain V primer sequence on the 5' end, and it was not trimmed (see "Presence of PCR primers and/or adapter sequences"), alignment of V genes will not be forcefully extended to the left side, and clonotypes with the same clonal sequence (specified in "Target region") but different V gene will not be separated into individual records.

Primers for some segments if accidentally annealed to non-target region may introduce chimeric sequences, and prevent exact V gene assignment, thus generating artificial clonotypes differing by only V gene assigned. Splitting by V gene is turned of to prevent this.

Targeted Library Parameters: 3’-end of the library
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you specify the library may contain J or C primer sequence on the 3' end, and it was not trimmed (see "Presence of PCR primers and/or adapter sequences"), alignment of J or C genes respectively will not be forcefully extended to the right side, and clonotypes with the same clonal sequence (specified in "Target region") but different J or C gene will not be separated into individual records (the motivation is the same as for 5’-end).

Presence of PCR primers and/or adapter sequences
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Specifies whether the adapter and primer sequences were trimmed for this data. This applies to V/J/C primers, 5'RACE adapters or nay other sequence not originating from target molecules.

It affects V/J/C alignment parameters (see above), and also affects alignment of V 5'UTR sequence (5'UTR will not be aligned if sequence may contain 5'RACE adapter sequences, e.g. if 5’-end of the library do not contain primer sequence is not marked as containing primer, but "Presence of PCR primers and/or adapter sequences" is set to "May be present".

Analysis Settings
-----------------

Target region
^^^^^^^^^^^^^

Region of the sequence to assemble clones by. Only this part of the sequence will be available in the output.

Filter
^^^^^^

**Filter out-of-frame CDR3s** Output only in-frame reads
**Filter out CDR3s with stop codon** Don't output sequences containing stop codons in their target region.

Output
^^^^^^

Specify which immunological chains you are interested in. Regardless of the values specified here, data will be aligned against all chains.

Hidden parameters
-----------------

All other parameters, like parameters for error correction algorithms, parameters for partial CDR3 reconstruction, extension, etc. are set to their default values, optimal in most cases.

Output
------

MiXCR Immune Repertoire Analyzer produces tab separated tables containing comprehensive information about clonotypes detected during analysis. This information includes:

  - Clonal sequence
  - Aggregated quality score values for clonal sequence
  - Anchor positions inside clonal sequence
  - Assigned V/D/J/C genes, among with corresponding aggregated alignment scoring
  - Encoded alignments of V/J/C genes inside clonal sequence

MiXCR Immune Repertoire Analyzer also contains useful statistics and corresponding charts for the clonesets produced with VDJTools.

