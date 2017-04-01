#!/bin/bash 
#directory to read from
dir=$1
example_dir="./examples"

if [ "$dir" == "" ]
then
	echo "Usage: ./documentation.sh <input_json_dir>"
	exit 0
fi

rm -rf $example_dir
mkdir $example_dir

#extract all types of events
events=`sed s'/[\"{}]//g' $dir/*.json | grep "type:EVENT_" | cut -d ":" -f6 | cut -d "," -f1 | sort | uniq`

#for each event extract an example
for event in $events
do
	#find all the files where this event exists
	event_examples=`grep "$event" sys_map_all.txt | cut -d ":" -f1`
	
	for example in $event_examples
	do
		#extract the file in which the example is found
		file_example_name=`echo $example | cut -d ":" -f1 | cut -d "." -f1`
		file_example=$dir"/"${file_example_name}".bin.json"
		
		#extract the example from the file
		event_example=`grep "type\":\"${event}" ${file_example}`
		
		#generate the output file name
		syscall_name=`grep -w $file_example_name".c" sys_map_all.txt | awk -v vevent="$event" 'BEGIN { FS = "{" vevent "}"; } {print $1}' | rev | cut -d " " -f2 | rev`
		output_file=$example_dir"/"$file_example_name"_"$event"_"$syscall_name".json"
		echo $event_example > $output_file

		echo -e EXTRACTING example on ${event} and $syscall_name from ${file_example}
	
		#extract all the infomation on this event example
		subject=`echo $event_example | sed s'/[\"{}]//g' | cut -d ":" -f8 | cut -d "," -f1` 
		predicateObject=`echo $event_example | sed s'/[\"{}]//g'  | cut -d ":" -f10 | cut -d "," -f1`
		predicateObject2=`echo $event_example | sed s'/[\"{}]//g' | cut -d ":" -f13 | cut -d "," -f1`
	
		if [ “$predicateObject2” != “null” ]
		then 
		predicateObject2=`echo $event_example | sed s'/[\"{}]//g' | cut -d ":" -f14 | cut -d "," -f1`
		fi
	
		subject_example=`grep "com.bbn.tc.schema.avro.Subject\":{\"uuid\":\"${subject}" $file_example`
		echo $subject_example >> $output_file

		predicate_example=`grep "\":{\"uuid\":\"${predicateObject}" $file_example`
		echo $predicate_example >> $output_file

		if [ “$predicateObject2” != “null” ]
		then
			predicate_example2=`grep "\":{\"uuid\":\"${predicateObject2}" $file_example`
			echo $predicate_example2 >> $output_file
		fi
	done
done


