int main() {
    volatile int a = 0;
    volatile int b = 100 / a; // 运行期除以 0
    return 0;
}
