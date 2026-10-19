#include <iostream>
#include <vector>
#include <cmath>
#include <algorithm>

using namespace std;

const int MAXN = 40005; // 题目中 n 的最大值
const int MAXB = 205;   // 估算的块数 (sqrt(40000) = 200)

// --- 数据结构 ---
int a[MAXN];          // 存放离散化后的数组
int origin_val[MAXN]; // 记录离散化后的值对应的原始种类编号
vector<int> sorted_a; // 用于离散化去重排序的临时容器

int belong[MAXN];     // belong[i] 表示原数组第 i 个元素属于第几块
int L[MAXB], R[MAXB]; // L[i] 和 R[i] 分别表示【第 i 块】的左右边界

int mode[MAXB][MAXB]; // mode[i][j] 记录第 i 块到第 j 块的众数 (离散化后的值)

// 核心优化：sum_cnt[i][x] 记录前 i 个块中，数字 x 出现的总次数
int sum_cnt[MAXB][MAXN]; 
int c[MAXN];          // query时使用的临时桶，只统计散块的元素
int stk[MAXN];        // 栈，用于极速清空临时桶 c
int top_stk = 0;
int tmp_cnt[MAXN];    // build预处理时使用的临时桶

// --- 初始化与预处理 ---
void build(int n) {
    int block_size = sqrt(n);
    int block_count = n / block_size + (n % block_size != 0 ? 1 : 0);

    // 1. 划分块
    for (int i = 1; i <= block_count; i++) {
        L[i] = (i - 1) * block_size + 1;
        R[i] = min(i * block_size, n);
    }
    for (int i = 1; i <= n; i++) {
        belong[i] = (i - 1) / block_size + 1;
    }

    // 2. 预处理 sum_cnt (前缀和思想，彻底干掉二分查找)
    for (int i = 1; i <= block_count; i++) {
        // 先继承前一个块的统计结果
        for (int j = 1; j <= n; j++) {
            sum_cnt[i][j] = sum_cnt[i - 1][j];
        }
        // 再加上当前块的元素
        for (int j = L[i]; j <= R[i]; j++) {
            sum_cnt[i][a[j]]++;
        }
    }

    // 3. 预处理 mode[i][j] (两层循环遍历所有的块区间)
    for (int i = 1; i <= block_count; i++) {
        int current_best = 0; 
        int current_max = 0;  
        
        for (int j = i; j <= block_count; j++) {
            for (int k = L[j]; k <= R[j]; k++) {
                int val = a[k];
                tmp_cnt[val]++;
                if (tmp_cnt[val] > current_max || (tmp_cnt[val] == current_max && origin_val[val] < origin_val[current_best])) {
                    current_max = tmp_cnt[val];
                    current_best = val;
                }
            }
            mode[i][j] = current_best;
        }
        
        // 极速清空法：只清空用过的桶，不要 memset 整个数组！
        for (int j = i; j <= block_count; j++) {
            for (int k = L[j]; k <= R[j]; k++) {
                tmp_cnt[a[k]] = 0;
            }
        }
    }
}

// --- 在线查询 (干掉了二分查找，纯 O(sqrt(N))) ---
int query(int l, int r) {
    int p = belong[l], q = belong[r];
    int best_val = 0, max_cnt = 0;

    // 情况 A：在同一个块或相邻块，直接暴力统计
    if (q - p <= 1) {
        for (int i = l; i <= r; i++) {
            int x = a[i];
            c[x]++;
            stk[++top_stk] = x; // 记录被修改的元素，用于 O(1) 清空
            if (c[x] > max_cnt || (c[x] == max_cnt && origin_val[x] < origin_val[best_val])) {
                max_cnt = c[x];
                best_val = x;
            }
        }
        // 极速清空
        while (top_stk > 0) c[stk[top_stk--]] = 0;
        return origin_val[best_val];
    }

    // 情况 B：跨越了多个块
    best_val = mode[p + 1][q - 1]; // 候选人 1 默认上岗
    
    // 统计左右散块的元素
    for (int i = l; i <= R[p]; i++) {
        c[a[i]]++;
        stk[++top_stk] = a[i];
    }
    for (int i = L[q]; i <= r; i++) {
        c[a[i]]++;
        stk[++top_stk] = a[i];
    }

    // 计算候选人 1 的真实得票数：散块里的票数 + 完整块里的票数(前缀和相减)
    max_cnt = c[best_val] + sum_cnt[q - 1][best_val] - sum_cnt[p][best_val];

    // 候选人 2：散块中出现过的元素参与竞争
    for (int i = 1; i <= top_stk; i++) {
        int x = stk[i];
        // 真实票数 = 散块票数 c[x] + 中间大整块票数
        int val = c[x] + sum_cnt[q - 1][x] - sum_cnt[p][x];
        if (val > max_cnt || (val == max_cnt && origin_val[x] < origin_val[best_val])) {
            max_cnt = val;
            best_val = x;
        }
    }

    // 极速清空栈
    while (top_stk > 0) c[stk[top_stk--]] = 0;

    return origin_val[best_val];
}

int main() {
    // 优化输入输出流，防 TLE
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    int n, m;
    if (!(cin >> n >> m)) return 0;

    // 1. 读入数据并进行离散化
    for (int i = 1; i <= n; i++) {
        cin >> a[i];
        sorted_a.push_back(a[i]);
    }
    
    sort(sorted_a.begin(), sorted_a.end());
    sorted_a.erase(unique(sorted_a.begin(), sorted_a.end()), sorted_a.end());

    for (int i = 1; i <= n; i++) {
        // lower_bound 返回的是迭代器，减去 begin() 得到 0-based 的索引，加 1 变成 1-based
        int discrete_val = lower_bound(sorted_a.begin(), sorted_a.end(), a[i]) - sorted_a.begin() + 1;
        origin_val[discrete_val] = a[i]; // 记录映射关系，方便最后输出答案
        a[i] = discrete_val;             // 将原数组替换为离散化后的较小数值
    }

    // 2. 建块与预处理
    build(n);

    // 3. 处理询问
    int last_ans = 0; // x 初始化为 0
    while (m--) {
        int l0, r0;
        cin >> l0 >> r0;
        
        // 题目给定的解密公式
        int l = ((l0 + last_ans - 1) % n) + 1;
        int r = ((r0 + last_ans - 1) % n) + 1;
        if (l > r) swap(l, r);

        last_ans = query(l, r);
        cout << last_ans << "\n";
    }

    return 0;
}