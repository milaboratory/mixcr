.. _ref-importSegments:

.. |br| raw:: html

   <br />

.. tip::

  If you want to impoert reference V, D and J sequences from IMGT, see importIMGT.sh script documentation.

Importing V, D, J gene sequences from file
==========================================

If you need to analyse data from species that are not covered by MiXCR built-it reference V, D, J genes library, or you just want to use alternative reference library, you can convert specially formatted fasta files to MiXCR loci-library format by using ``importSegments`` action.

Here is the examaple command:

::

    mixcr importSegments -p imgt -v human_TRBV.fasta -j human_TRBJ.fasta \
          -d human_TRBD.fasta -l TRB -s 9606:hs -r report.txt

This command will import IMGT formatted fasta files (like those that can be downloade on this_ page) and import it to a local loci library file (stored in ``~/.mixcr/local.ll``).

.. _this: http://www.imgt.org/vquest/refseqh.html

Command line parameters
-----------------------

Here is the list of command line parameters for ``importSegments`` action:

+------------------------------------+-------------------------------------------------------------------+
| Option                             | Description                                                       |
+====================================+===================================================================+
| ``-p {params}`` |br|               | select the parameters of import. Parameters determine how to      |
| ``--parameters {params}``          | parse fasta headers and how to extract information about anchor   |
|                                    | points (e.g. using specific positions in sequences with IMGT gaps |
|                                    | or searching for a specific patterns in gene seqeuence).          |
|                                    | |br| |br| currently, the only possible value is ``imgt``          | 
+------------------------------------+-------------------------------------------------------------------+
| ``-v {file}``                      | specify fasta-formatted file with sequences ov V genes            |
+------------------------------------+-------------------------------------------------------------------+
| ``-d {file}``                      | specify fasta-formatted file with sequences ov D genes            |
+------------------------------------+-------------------------------------------------------------------+
| ``-j {file}``                      | specify fasta-formatted file with sequences ov J genes            |
+------------------------------------+-------------------------------------------------------------------+
| ``-l {locus}`` |br|                | determines which immunological locus data is being imported       |
| ``--locus {locus}``                |                                                                   |
|                                    | possible values: ``TRA``, ``TRB``, ``TRG``, ``TRD``,              |
|                                    | ``IGH``, ``IGL``, ``IGK``                                         |
+------------------------------------+-------------------------------------------------------------------+
| ``-s {taxonID:commName1:..}`` |br| |                                                                   |
| ``--species {...}``                |                                                                   |
+------------------------------------+-------------------------------------------------------------------+
