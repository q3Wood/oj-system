import os
import chromadb

# 1. 确保持久化目录存在
DB_PATH = "./data/oj_rag_db"
os.makedirs(DB_PATH, exist_ok=True)

# 2. 建立持久化客户端与集合
chroma_client = chromadb.PersistentClient(path=DB_PATH)
knowledge_collection = chroma_client.get_or_create_collection(
    name="oj_knowledge_base"
)

# 3. 初始化种子数据（如果库是空的，自动写入出题人题解和通用踩坑经验）
if knowledge_collection.count() == 0:
    seed_documents = [
        # --- A+B 问题的官方出题人笔记 ---
        (
            "doc_prob_1001",
            "【#1001 A+B 官方题解】: 本题为最基础的算术加法。出题人意图是考察标准的输入输出与基本运算。注意：严禁使用复杂内置函数，直接计算 a + b 即可；数据范围在 32 位整型内，无需高精度。",
            {"type": "editorial", "problem_id": 1001, "title": "A+B问题"}
        ),
        # --- 通用竞赛避坑条款 1: 位运算 ---
        (
            "pitfall_popcount",
            "【C++底层避坑 - 位运算】: GCC 内置函数 __builtin_popcount(x) 用于统计二进制中 1 的个数。入参为无符号整数。若传入负数，负数会被强制转为补码（如 -5 会被视为无符号大整数），导致统计出的 1 数量极多（如 30 个以上），绝不能用来替代加法！",
            {"type": "pitfall", "problem_id": 0, "tag": "bitwise"}
        ),
        # --- 通用竞赛避坑条款 2: 递归与栈溢出 ---
        (
            "pitfall_stack_overflow",
            "【算法底层避坑 - 递归栈溢出】: 在网格搜索（如连通块、走迷宫）中，当网格规模达到 500x500 以上时，深度优先搜索（DFS）的递归深度会突破 Linux 系统默认栈大小（ulimit -s），触发段错误（Segmentation Fault / MLE），推荐改用队列实现广度优先搜索（BFS）。",
            {"type": "pitfall", "problem_id": 0, "tag": "search"}
        ),
        # --- 通用竞赛避坑条款 3: 整数溢出 ---
        (
            "pitfall_overflow",
            "【语言底层避坑 - 整数溢出】: C++ 中两个 32 位整型相乘（如 a * b），即使结果用 long long 接收，计算中间量依然会在 32 位寄存器中溢出。必须显式强制类型转换：(long long)a * b。",
            {"type": "pitfall", "problem_id": 0, "tag": "overflow"}
        )
    ]

    knowledge_collection.add(
        ids=[doc[0] for doc in seed_documents],
        documents=[doc[1] for doc in seed_documents],
        metadatas=[doc[2] for doc in seed_documents]
    )
    print(f" RAG 知识库初始化完成，已持久化存入 {len(seed_documents)} 条权威资料！")