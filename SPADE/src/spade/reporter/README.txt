
WARNING: Ian has been futzing around here.

Here are the current changes:

1. Fixed the conflation of FILE* with a 32 bit int, that crashes on 64 bit machines.

2. Solved the "Daemon problem" via adding a "smart close" to the system, then doing
some linking magic (Chris Dodd and Bruno helped here).

3. Added some cleaner makefiles.  Build here by:

"make"

Irregardless of whether you are on linux or a mac.


