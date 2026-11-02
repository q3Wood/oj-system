from app.schemas import AnalyzeRequest

def format_user_prompt(req: AnalyzeRequest, editorial_context: str | None = None) -> str:
    """动态拼装用户的单次代码诊断请求输入"""
    prompt = f"""
<problem_info>
- 题目ID: #{req.problem_id}
- 题目标题: {req.problem_title}
- 题目描述:
{req.problem_description}
</problem_info>

<submission>
- 评测状态: {req.judge_status}
- 编程语言: {req.language}
- 学生源码:
```{req.language}
{req.user_code}
```
</submission>
"""

    # 如果有真实的未通过测试点，注入进去
    if req.failed_test_case:
        prompt += f"""
<failed_test_case>
- 输入数据 (Input): {req.failed_test_case.input_data}
- 期望输出 (Expected): {req.failed_test_case.expected_output}
- 学生实际输出 (Actual Output): {req.failed_test_case.user_output}
</failed_test_case>
"""

    # 如果 RAG 检索出了该题的官方题解或避坑笔记，注入进去作为开卷小抄
    if editorial_context:
        prompt += f"""
<official_editorial>
{editorial_context}
</official_editorial>
"""

    prompt += "\n请根据以上上下文信息，严格输出符合格式的 JSON 诊断评审报告。"
    return prompt
