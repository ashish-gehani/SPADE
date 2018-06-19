#ifndef UBSI_DEFINE
#define UBSI_DEFINE

#define UBSI_MEM_WRITE(addr) { \
  intptr_t value = (intptr_t)addr; \
  uint32_t high = value >> 32; \
  uint32_t low = value; \
  kill(-300, high); \
  kill(-301, low); \
}

#define UBSI_MEM_READ(addr) { \
  intptr_t value = (intptr_t)addr; \
  uint32_t high = value >> 32; \
  uint32_t low = value; \
  kill(-200, high); \
  kill(-201, low); \
}

#define UBSI_LOOP_ENTRY(loopId) { \
  kill(-100, loopId); \
}

#define UBSI_LOOP_EXIT(loopId) { \
  kill(-101, loopId); \
}

#endif
