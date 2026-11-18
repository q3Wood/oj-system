from app.schemas import AnalyzeRequest


def format_user_prompt(req: AnalyzeRequest, editorial_context: str | None = None) -> str:
    """动态拼装用户的单次代码诊断请求输入"""
    code_fence = "```"
    
    lines = [
        "<problem_info>",
        f"- 题目ID: #{req.problem_id}",
        f"- 题目标题: {req.problem_title}",
        "- 题目描述:",
        req.problem_description,
        "</problem_info>",
        "",
        "<submission>",
        f"- 评测状态: {req.judge_status}",
        f"- 编程语言: {req.language}",
        "- 学生源码:",
        f"{code_fence}{req.language}",
        req.user_code,
        code_fence,
        "</submission>",
    ]

    # 1. 如果有编译报错(CE)或运行时崩溃信号(RE)，注入错误日志
    if req.error_message:
        lines.extend([
            "",
            "<error_log>",
            req.error_message,
            "</error_log>",
        ])

    # 2. 如果有真实的未通过测试点(WA)，注入对比数据
    if req.failed_test_case:
        lines.extend([
            "",
            "<failed_test_case>",
            "- 输入数据 (Input):",
            req.failed_test_case.input_data,
            "- 期望输出 (Expected):",
            req.failed_test_case.expected_output,
            "- 学生实际输出 (Actual Output):",
            req.failed_test_case.user_output,
            "</failed_test_case>",
        ])

    # 3. 如果 RAG 检索出了该题的官方题解或避坑笔记，注入作为开卷参考
    if editorial_context:
        lines.extend([
            "",
            "<official_editorial>",
            editorial_context,
            "</official_editorial>",
        ])

    lines.append("")
    lines.append("请根据以上上下文信息，严格输出符合格式的 JSON 诊断评审报告。")
    return "\n".join(lines)