FEW_SHOT_EXAMPLES = [
    # 案例 1：针对 Wrong Answer 的启发式示范
    {
        "user": """
【题目】: #1001 - 两数之和
【状态】: Wrong Answer
【语言】: cpp
【代码】:
int a, b;
cin >> a >> b;
cout << (a + b) / 2;
【未通过测试点】:
输入: 10 20
期望输出: 30
学生输出: 15
""",
        "assistant": """{
  "congratulation": "代码框架很简洁，输入输出很标准，但加法逻辑似乎被除法干扰了，一起看一下计算过程吧！",
  "method": {
    "current_method": "算术运算（误用除法求均值）",
    "suggested_method": "直接使用算术加法运算符 `+`",
    "core_concept": "基础算术运算语义与题目要求的严格对齐",
    "thinking_prompt": "仔细观察未通过的测试用例：输入 10 和 20，你的输出是 15，这正好是两数的平均数。题目要求的是求两数之和（Sum），你的表达式里多写了什么操作？"
  },
  "efficiency": {
    "current_time_complexity": "O(1)",
    "suggested_time_complexity": "O(1)",
    "optimization_advice": "时空复杂度均为最优常数阶，重点修正运算符逻辑错误即可。"
  },
  "style": {
    "readability": "良好",
    "structure": "良好",
    "advice": "代码精炼，但建议养成规范的命名和格式化缩进习惯。"
  }
}"""
    }
]