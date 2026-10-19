int main() {
    volatile int x = 0;
    while (1) {
        x++; // 死循环吃满 CPU
    }
    return 0;
}
