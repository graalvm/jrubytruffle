/*
Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
code is released under a tri EPL/GPL/LGPL license. You can use it,
redistribute it and/or modify it under the terms of the:

Eclipse Public License version 1.0
GNU General Public License version 2
GNU Lesser General Public License version 2.1
*/

/*
Copyright (C) 1993-2013 Yukihiro Matsumoto. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:
1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
SUCH DAMAGE.
*/

/* For clock_gettime(), lstat() and strdup() on Linux */
#define _XOPEN_SOURCE 600
/* For minor()/major() on Linux */
#define _BSD_SOURCE
#define _DEFAULT_SOURCE
/* For flock() on Darwin */
#define _DARWIN_C_SOURCE 1

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <pwd.h>
#include <spawn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <utime.h>

#include <sys/file.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/select.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>

/* For minor()/major() on Linux */
#ifdef __linux__
#include <sys/sysmacros.h>
#endif

/* For minor()/major() on Solaris */
#if defined __sun
#include <sys/mkdev.h>
#endif

struct truffleposix_stat {
  uint64_t atime;
  uint64_t mtime;
  uint64_t ctime;
  uint64_t nlink;
  uint64_t rdev;
  uint64_t blksize;
  uint64_t blocks;
  uint64_t dev;
  uint64_t ino;
  uint64_t size;
  uint64_t mode;
  uint64_t gid;
  uint64_t uid;
};

static void copy_stat(struct stat *stat, struct truffleposix_stat* buffer);

static void init_fd_set(fd_set *set, int nfds, int *fds, int *maxfd) {
  FD_ZERO(set);
  for (int i = 0; i < nfds; i++) {
    int fd = fds[i];
    FD_SET(fd, set);
    if (fd > *maxfd) {
      *maxfd = fd;
    }
  }
}

static void mark_ready_from_set(fd_set *set, int nfds, int *fds) {
  for (int i = 0; i < nfds; i++) {
    int fd = fds[i];
    if (!FD_ISSET(fd, set)) {
      fds[i] = -1;
    }
  }
}

int truffleposix_select(int nread, int *readfds, int nwrite, int *writefds,
                        int nexcept, int *exceptfds, long timeout_us) {
  struct timeval timeout;
  struct timeval *timeout_ptr = NULL;

  if (timeout_us >= 0) {
    timeout.tv_sec = (timeout_us / 1000000);
    timeout.tv_usec = (timeout_us % 1000000);
    timeout_ptr = &timeout;
  }

  int maxfd = 0;
  fd_set readset, writeset, exceptset;
  init_fd_set(&readset, nread, readfds, &maxfd);
  init_fd_set(&writeset, nwrite, writefds, &maxfd);
  init_fd_set(&exceptset, nexcept, exceptfds, &maxfd);

  int ret = select(maxfd+1, &readset, &writeset, &exceptset, timeout_ptr);
  if (ret > 0) {
    mark_ready_from_set(&readset, nread, readfds);
    mark_ready_from_set(&writeset, nwrite, writefds);
    mark_ready_from_set(&exceptset, nexcept, exceptfds);
  }
  return ret;
}

int truffleposix_utimes(const char *filename, long atime_sec, int atime_us,
                        long mtime_sec, int mtime_us) {
  struct timeval timevals[2];
  timevals[0].tv_sec = atime_sec;
  timevals[0].tv_usec = atime_us;
  timevals[1].tv_sec = mtime_sec;
  timevals[1].tv_usec = mtime_us;
  return utimes(filename, timevals);
}

#define timeval2double(timeval) \
  (((double) (timeval).tv_sec) + ((double) (timeval).tv_usec) / 1e6)

int truffleposix_getrusage(double times[4]) {
   struct rusage self, children;
   int r;
   r = getrusage(RUSAGE_SELF, &self);
   if (r == -1) {
     return -1;
   }
   r = getrusage(RUSAGE_CHILDREN, &children);
   if (r == -1) {
     return -1;
   }
   times[0] = timeval2double(self.ru_utime);
   times[1] = timeval2double(self.ru_stime);
   times[2] = timeval2double(children.ru_utime);
   times[3] = timeval2double(children.ru_stime);
   return 0;
}

char* truffleposix_get_user_home(const char *name) {
  struct passwd entry;
  struct passwd *result = NULL;
  int ret;

  size_t buffer_size = sysconf(_SC_GETPW_R_SIZE_MAX);
  if (buffer_size <= 0) {
    buffer_size = 16384;
  }

  char *buffer = malloc(buffer_size);
  if (buffer == NULL) {
    return NULL;
  }

retry:
  ret = getpwnam_r(name, &entry, buffer, buffer_size, &result);
  if (result != NULL) {
    char *home = strdup(entry.pw_dir);
    free(buffer);
    return home;
  } else if (ret == ERANGE) {
    buffer_size *= 2;
    free(buffer);
    buffer = malloc(buffer_size);
    if (buffer == NULL) {
      return NULL;
    }
    goto retry;
  } else if (ret != 0) {
    free(buffer);
    errno = ret;
    return NULL;
  } else {
    /* ret == 0 && result == NULL means not found */
    return "";
  }
}

char* truffleposix_readdir(DIR *dirp) {
  errno = 0;
  struct dirent *entry = readdir(dirp);
  if (entry != NULL) {
    return entry->d_name;
  } else if (errno == 0) {
    return "";
  } else {
    return NULL;
  }
}

void truffleposix_rewinddir(DIR *dirp) {
  rewinddir(dirp);
}

int truffleposix_getpriority(int which, id_t who) {
  /* getpriority() can return -1 so errno has to be cleared. */
  errno = 0;
  int r = getpriority(which, who);
  if (r == -1 && errno != 0) {
    /* getpriority() is between -20 and 19 on Linux and -20 and 20 on macOS and Solaris */
    return -100 - errno;
  }
  return r;
}

pid_t truffleposix_waitpid(pid_t pid, int options, int result[3]) {
  int status = 0;
  pid_t r = waitpid(pid, &status, options);
  if (r <= 0) {
    return r;
  }

  int exitcode = -1000, termsig = -1000, stopsig = -1000;
  if (WIFEXITED(status)) {
    exitcode = WEXITSTATUS(status);
  } else if (WIFSIGNALED(status)) {
    termsig = WTERMSIG(status);
  } else if (WIFSTOPPED(status)) {
    stopsig = WSTOPSIG(status);
  }

  result[0] = exitcode;
  result[1] = termsig;
  result[2] = stopsig;
  return r;
}

/* flock() is not available on Solaris */
#if defined __sun
#define LOCK_SH 1
#define LOCK_EX 2
#define LOCK_NB 4
#define LOCK_UN 8

int truffleposix_flock(int fd, int operation) {
  struct flock lock;
  switch (operation & ~LOCK_NB) {
  case LOCK_SH:
    lock.l_type = F_RDLCK;
    break;
  case LOCK_EX:
    lock.l_type = F_WRLCK;
    break;
  case LOCK_UN:
    lock.l_type = F_UNLCK;
    break;
  default:
    errno = EINVAL;
    return -1;
  }
  lock.l_whence = SEEK_SET;
  lock.l_start = 0L;
  lock.l_len = 0L;
  int r = fcntl(fd, (operation & LOCK_NB) ? F_SETLK : F_SETLKW, &lock);
  if (r == -1 && errno == EAGAIN) {
    errno = EWOULDBLOCK;
  }
  return r;
}
#else
int truffleposix_flock(int fd, int operation) {
  return flock(fd, operation);
}
#endif

int truffleposix_stat(const char *path, struct truffleposix_stat *buffer) {
  struct stat native_stat;
  int result = stat(path, &native_stat);
  if (result == 0) {
    copy_stat(&native_stat, buffer);
  }
  return result;
}

int truffleposix_fstat(int fd, struct truffleposix_stat *buffer) {
  struct stat native_stat;
  int result = fstat(fd, &native_stat);
  if (result == 0) {
    copy_stat(&native_stat, buffer);
  }
  return result;
}

int truffleposix_lstat(const char *path, struct truffleposix_stat *buffer) {
  struct stat native_stat;
  int result = lstat(path, &native_stat);
  if (result == 0) {
    copy_stat(&native_stat, buffer);
  }
  return result;
}

unsigned int truffleposix_major(dev_t dev) {
  return major(dev);
}

unsigned int truffleposix_minor(dev_t dev) {
  return minor(dev);
}

static void copy_stat(struct stat *native_stat, struct truffleposix_stat* buffer) {
  buffer->atime   = native_stat->st_atime;
  buffer->mtime   = native_stat->st_mtime;
  buffer->ctime   = native_stat->st_ctime;
  buffer->nlink   = native_stat->st_nlink;
  buffer->rdev    = native_stat->st_rdev;
  buffer->blksize = native_stat->st_blksize;
  buffer->blocks  = native_stat->st_blocks;
  buffer->dev     = native_stat->st_dev;
  buffer->ino     = native_stat->st_ino;
  buffer->size    = native_stat->st_size;
  buffer->mode    = native_stat->st_mode;
  buffer->gid     = native_stat->st_gid;
  buffer->uid     = native_stat->st_uid;
}

#ifndef NO_CLOCK_GETTIME
int64_t truffleposix_clock_gettime(int clock) {
  struct timespec timespec;
  int ret = clock_gettime((clockid_t) clock, &timespec);
  if (ret != 0) {
    return 0;
  }
  return ((int64_t) timespec.tv_sec * 1000000000) + (int64_t) timespec.tv_nsec;
}
#endif

#define CHECK(call, label) if ((error = call) != 0) { perror(#call); goto label; }

pid_t truffleposix_posix_spawnp(const char *command, char *const argv[], char *const envp[],
                                int nredirects, int* redirects, int pgroup) {
  int ret = -1;
  pid_t pid = -1;
  int error = 0;
  int called_posix_spawn = 0;

  /* We want to use NULL for actions and attrs if there are no special options,
   * as that is more efficient on Linux as it uses vfork() (see the man page).
   * It also avoids the extra _init/_destroy calls in such a case. */
  posix_spawn_file_actions_t *file_actions_ptr = NULL;
  posix_spawn_file_actions_t file_actions;
  if (nredirects > 0) {
    CHECK(posix_spawn_file_actions_init(&file_actions), end);
    file_actions_ptr = &file_actions;
    for (int i = 0; i < nredirects; i += 2) {
      int from = redirects[i];
      int to = redirects[i+1];
      CHECK(posix_spawn_file_actions_adddup2(&file_actions, to, from), cleanup_actions);
    }
  }

  posix_spawnattr_t *attrs_ptr = NULL;
  posix_spawnattr_t attrs;
  if (pgroup >= 0) {
    CHECK(posix_spawnattr_init(&attrs), cleanup_actions);
    attrs_ptr = &attrs;
    CHECK(posix_spawnattr_setflags(&attrs, POSIX_SPAWN_SETPGROUP), cleanup_attrs);
    CHECK(posix_spawnattr_setpgroup(&attrs, pgroup), cleanup_attrs);
  }

  ret = posix_spawnp(&pid, command, file_actions_ptr, attrs_ptr, argv, envp);
  called_posix_spawn = 1;

cleanup_attrs:
  if (attrs_ptr) {
    posix_spawnattr_destroy(attrs_ptr);
    attrs_ptr = NULL;
  }
cleanup_actions:
  if (file_actions_ptr) {
    posix_spawn_file_actions_destroy(file_actions_ptr);
    file_actions_ptr = NULL;
  }
end:
  if (!called_posix_spawn) {
    return -error;
  }
  if (ret == 0) {
    return pid;
  } else {
    return -ret;
  }
}
