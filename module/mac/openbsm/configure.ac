AC_INIT([spadeOpenBSM], [1.0])
AC_CONFIG_AUX_DIR([build-aux])
AM_INIT_AUTOMAKE([-Wall -Werror foreign])

AC_PROG_CC

# Check for libbsm
AC_CHECK_LIB([bsm], [au_open],
    [HAVE_LIBBSM=yes],
    [HAVE_LIBBSM=no])

if test "x$HAVE_LIBBSM" = "xyes"; then
    AC_DEFINE([HAVE_LIBBSM], [1], [Define if libbsm is available])
    LIBBSM_LIBS="-lbsm"
else
    AC_MSG_WARN([libbsm not found; OpenBSM reporter will not be built])
    LIBBSM_LIBS=""
fi

AM_CONDITIONAL([HAVE_LIBBSM], [test "x$HAVE_LIBBSM" = "xyes"])
AC_SUBST([LIBBSM_LIBS])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
