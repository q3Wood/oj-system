from langchain_core.tools import tool

@tool
def search_problem_editorial(keyword: str) -> str:
    """当需要获取题目的官方最优解法思路、出题人意图、或者特殊的边界测试点提示时，调用此工具检索题解库。
    参数：
    - keyword: 搜索关键词，例如算法名称（如“二分查找边界”、“单调栈”）或题目核心考点
    """
    # 这里接入你的本地题解知识库（初期可作为知识库检索桩）
    # 真实场景下会去查询 ChromaDB
    knowledge_base = {
        "A+B": "本题极度简单，但需注意输入输出速度，以及是否可能存在超出 32 位整型范围的大数情况。",
        "popcount": "注意：__builtin_popcount 只接受无符号整数，如果输入为负数，按补码解析会导致结果偏差。",
        "DFS": "网格连通块遍历注意递归深度，当网格达到 1000x1000 时，DFS 会导致系统栈溢出，建议改用 BFS 队列实现。"
    }
    
    for k, v in knowledge_base.items():
        if k.lower() in keyword.lower():
            return f"【官方出题人题解备忘】: {v}"
            
    return "【题解检索】: 未找到针对该特定关键词的特殊避坑说明，请遵循标准复杂度实现。"