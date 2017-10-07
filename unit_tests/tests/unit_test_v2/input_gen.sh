mkdir input
mkdir output
for i in {0..100}
do
		echo hello $i  > "input/data$(printf "%02d" "$i").txt"
done
