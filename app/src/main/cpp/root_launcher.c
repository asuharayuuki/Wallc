#define _GNU_SOURCE
#include <errno.h>
#include <grp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static int fail(const char *stage) {
    fprintf(stderr, "Wallc launcher [%s]: %s\n", stage, strerror(errno));
    return 126;
}

/* Never open Binder here. Establish credentials BEFORE starting ART. */
int main(int argc, char **argv) {
    if (argc != 5 || argv[1][0] != '/') {
        fprintf(stderr, "Wallc launcher: invalid arguments\n"); return 64;
    }
    if (geteuid() != 0) {
        fprintf(stderr, "Wallc launcher: root required, uid=%u euid=%u\n", getuid(), geteuid()); return 77;
    }
    puts("WALLC_ROOT_UID=0"); fflush(stdout);
    umask(077);
    if (setenv("CLASSPATH", argv[1], 1) == -1) return fail("classpath");
    unsetenv("LD_PRELOAD"); unsetenv("LD_LIBRARY_PATH");
    if (setgroups(0, NULL) == -1) return fail("groups");
    if (setresgid(2000, 2000, 2000) == -1) return fail("gid");
    if (setresuid(2000, 2000, 2000) == -1) return fail("uid");
    if (getuid() != 2000 || geteuid() != 2000 || getgid() != 2000) {
        fprintf(stderr, "Wallc launcher: credentials did not change\n"); return 77;
    }
    char *const command[] = { "/system/bin/app_process", "/system/bin", "--nice-name=wallc:wallpaper",
        "org.eu.suisei.wallc.RootWallpaperMain", argv[2], argv[3], argv[4], NULL };
    execv(command[0], command);
    return fail("exec app_process");
}
