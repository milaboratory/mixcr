MiXCR: a universal tool for fast and accurate analysis of T- and B- cell receptor repertoire sequencing data
------------------------------------------------------------------------------------------------------------

MiXCR is a universal framework that processes big immunome data from
raw sequences to quantitated clonotypes. MiXCR efficiently handles
paired- and single-end reads, considers sequence quality, corrects
PCR errors and identifies germline hypermutations. The software
supports both partial- and full-length profiling and employs all
available RNA or DNA information, including sequences upstream of V
and downstream of J gene segments.

MiXCR is free for academic and non-profit use (see :ref:`License <license>`).


.. figure:: _static/mixcr.png
   :align: center
   :width: 80%

   *MiXCR pipeline. The workflow from IG or T-cell receptor data sets to final clonotypes is shown*

.. _getting-started:

.. toctree::
   :hidden:
   :caption: Getting started
   :maxdepth: 3

   install
   quickstart

.. _main-actions:

.. toctree::
   :hidden:
   :caption: Main actions
   :maxdepth: 1

   align
   assemble
   export

.. _other-actions:

.. toctree::
   :hidden:
   :caption: Other actions
   :maxdepth: 1

   assembleContigs

.. _special-cases:

.. toctree::
   :hidden:
   :caption: Special cases
   :maxdepth: 1

   rnaseq
   importSegments
   newAligner

.. _in-depth-topics:

.. toctree::
   :hidden:
   :caption: In-depth topics
   :maxdepth: 1

   geneFeatures
   appendix
   utils
   license