# Java

This build follows the general build guidelines of a subpackage, except for the following variations.

Since Maven is used, the `all` make target calls Maven's `package` goal. The reason is that the `package` goal builds the jar if it is not already present, and puts the resulting jar and headers to the specified location.

`src` needs to be here so that subpackages are self-contained, and for easier development environment configuration.
