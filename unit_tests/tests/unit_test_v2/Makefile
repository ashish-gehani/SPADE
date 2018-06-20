SRCS = $(wildcard *.c)
PROGS = $(patsubst %.c,%,$(SRCS))
CC=gcc
CFLAGS = -lpthread

all: $(PROGS)

%: %.c 
	$(CC)  -o $@ $< $(CFLAGS) 

clean:
	rm $(PROGS)
