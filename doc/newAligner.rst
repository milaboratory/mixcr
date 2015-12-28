.. _ref-kAligner2:
 
(BETA) KAligner2: New aligner with big gaps support
===================================================

.. danger::

    This feature is provided for beta testing, and not recommended for production use!

To process data using new aligner, apply special paramenter pre-sets as follows:

::

    mixcr align -p kaligner2 ....
    mixcr assemble -p default_affine ....
    ....

Any other parameters can also be provided along with ``-p ...`` option.
