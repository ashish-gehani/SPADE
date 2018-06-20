1. unit1.c: This program has a main loop and each iteration of the loop reads a
		input file (input/dataXX.txt) and write a output file (output/outputXX.txt).
		Each iteration is a seperate unit.



2. unit2.c: This program creates two threads; producer and consumer.
Both threads have own main loops:
- Producer reads an input file (input/dataXX.txt) and stores it in shared buffer.
- Consumer reads the data from the shared buffer and stores it in a output file (output/outputXX.txt)

Note that each iteration of the producer loop and the consumer loop are are causally
related via the shared buffer. For example:
1st iteration of the producer loop: reads a file "input/data00.txt" and stores data into buffer[0].
1st iteration of the consumer loop: reads buffer[0] and writes data into output/output00.txt 

2nd iteration of the producer loop: reads "input/data01.txt" and store data into
buffer[1].
2nd iteration of the consumer loop: reads buffer[1] and writes it into output/output01.txt

1st unit (1st iteration) of producer and 1st unit of consumer are causally
related, but 1st unit of producer and 2nd unit of consumer do not have any dependence.
