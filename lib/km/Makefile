obj-m += netio.o netio_controller.o

all:
	make -C /lib/modules/$(shell uname -r)/build M=$(PWD)/lib/km modules

clean:
	make -C /lib/modules/$(shell uname -r)/build M=$(PWD)/lib/km clean
