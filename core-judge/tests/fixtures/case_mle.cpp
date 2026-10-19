#include <vector>
int main() {
    // 申请约 120MB 物理常驻内存并实际写入数据
    std::vector<char> big_data(120 * 1024 * 1024, 'x');
    return 0;
}
