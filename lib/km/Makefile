obj-m += netio.o

all:
	make -C /lib/modules/$(shell uname -r)/build M=$(PWD)/lib/km modules

clean:
	make -C /lib/modules/$(shell uname -r)/build M=$(PWD)/lib/km clean
