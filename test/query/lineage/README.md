## Remote Lineage Setup

This describes instructions for setting up SPADE to try out simple remote SPADE querying.

__Steps__:

* Setup a SPADE instance to be used as the querying server. This is used to perform all queries. Required files included in the `server` directory.
* Setup a SPADE instance to be used as the queryable client. This is queried by the SPADE query server instance. A specific `Vagrantfile` is provided in the `client` directory to set it up.
* Exchange public keys between the SPADE instances, and add them using the `addKeys.sh` script.
* Start both instances of SPADE.
* Add one of the queryable SPADE storages on both instances of SPADE.
* Set the added storage as the default storage for querying using the command `set storage <storage_name>` on both instances of SPADE.
* Add the `DSL` reporter with the command `add reporter DSL /tmp/spade_pipe` on both instances of SPADE.
* Execute the script `./server/input-for-dsl.sh` on the SPADE instance being considered as server.
* Execute the script `./client/input-for-dsl.sh` on the SPADE instance being considered as client.
* Use the SPADE instance considered as the server to execute the queries in the file `server/spade.queries`.

