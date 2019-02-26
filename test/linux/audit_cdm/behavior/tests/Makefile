SRCS = $(wildcard *.c)
PROGS = $(patsubst %.c,%,$(SRCS))
CC=gcc
CFLAGS = -lpthread

all: $(PROGS)

%: %.c 
	$(CC)  -o $@.bin $< $(CFLAGS) 

clean:
	rm $(PROGS)
