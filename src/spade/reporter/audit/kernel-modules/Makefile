obj-m += netio.o netio_controller.o

all:
	make -C /lib/modules/$(shell uname -r)/build M=$(PWD)/src/spade/reporter/audit/kernel-modules modules

clean:
	make -C /lib/modules/$(shell uname -r)/build M=$(PWD)/src/spade/reporter/audit/kernel-modules clean
