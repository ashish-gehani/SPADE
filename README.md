Data is continuously transformed by computation. Understanding the origins of a piece of data can help in a variety of circumstances. For example, the data's history can facilitate fault analysis, decide how much the data should be trusted, or aid in profiling applications.

**SPADE** provides functionality to track and analyze the provenance of data that arises from multiple sources, distributed over the wide area, at varied levels of abstraction, and from both discrete and continuous systems.


# Cross-Platform #

**SPADE** provides a cross-platform distributed data provenance collection, filtration, storage, and querying service. It includes support for collecting provenance from the Linux, Mac OS X, and Windows operating systems. **SPADE** uses the auditing functionality of each operating system, which remains stable across various releases, to transparently record the provenance of all data. Installation can be performed with a pre-built package or from source code. <br /><img src='../../wiki/img/frontpage_platforms.png'><br />

#Easy to Deploy #

**SPADE** automates the generation and collection of data provenance at the operating system level. It provides a broad view of activity across all the computers it is installed on in a distributed system. **SPADE** does this without requiring applications or the operating systems to be modified. It reports information about the name, owner, group, parent, host, creation time, command line, and environment variables of each process. It also reports the name, path, host, size, and modification time of files read or written during a computation. All this information can be collected with a few simple commands. <br /><img src='../../wiki/img/frontpage_easy.png'> <br />

#Flexible Querying #

**SPADE** supports the use of Boolean, wildcard, fuzzy, proximity, range, boosting, and group operators when searching local provenance records. It also supports graph and relational (SQL) queries over local provenance. Provenance collected by **SPADE** can also be inspected with third-party tools, such as Neoclipse and SQL Workbench. Finally, the **SPADE** query tool can transparently resolve path and lineage queries that span multiple hosts in a distributed system. <br /><img src='../../wiki/img/frontpage_query.png'><br />

#Modular and Extensible #

**SPADE** is designed to be extensible in four ways. A _reporter_ can be implemented to collect provenance activity about a new domain of interest. A new _filter_ can be written to perform novel transformations on provenance events. A new _storage_ system can be added to record provenance in a different format. A new _sketch_ can be designed to optimize the distributed querying. <br /><img src='../../wiki/img/frontpage_modular.png' width='250px'> <br />

#Getting Started #

Please refer to **SPADE**'s [documentation](../../wiki/Documentation.md) to learn how to use it to collect, integrate, filter, store, and query your provenance metadata.

To learn more about **SPADE**, please see:

  * Ashish Gehani and Dawood Tariq, **SPADE: Support for Provenance Auditing in Distributed Environments**, _13th ACM/IFIP/USENIX International Conference on Middleware_, 2012. [[PDF](http://www.csl.sri.com/users/gehani/papers/MW-2012.SPADE.pdf)].

ProvBench traces are accessible [here](../../wiki/Traces.md).


---


This material is based upon work supported by the National Science Foundation under Grants [OCI-0722068](http://www.nsf.gov/awardsearch/showAward?AWD_ID=0722068)<sup>1</sup> and [IIS-1116414](http://www.nsf.gov/awardsearch/showAward?AWD_ID=1116414)<sup>2</sup>. Any opinions, findings, and conclusions or recommendations expressed in this material are those of the author(s) and do not necessarily reflect the views of the National Science Foundation.

<sup>1</sup> NSF Grant 0722068: [Scalable Authentication of Grid Data Provenance](http://www.nsf.gov/awardsearch/showAward?AWD_ID=0722068), PI: [Ashish Gehani](http://www.csl.sri.com/people/gehani/)

<sup>2</sup> NSF Grant 1116414: [Scalable Integration and Analysis of the Provenance of Diverse Scientific Data](http://www.nsf.gov/awardsearch/showAward?AWD_ID=1116414), PI: [Ashish Gehani](http://www.csl.sri.com/people/gehani/)

<a href='Hidden comment: 
The Apple logo is licensed under the Creative Commons Attribution-Share Alike 3.0 Unported, 2.5 Generic, 2.0 Generic and 1.0 Generic license.
The Linux Tux logo is (c) Larry Ewing, Simon Budig und Anja Gerwinsk and is licensed under the terms of the GNU General Public License version 3.
The Windows logo is in the public domain.
'></a>