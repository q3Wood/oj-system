from pydantic import BaseModel, Field

# ==================== 1. 入参契约 (Spring Boot ➔ Python) ====================

class FailedTestCase(BaseModel):
    """评测机捕获的真实未通过测试点"""
    input_data: str = Field(description="导致程序失败的测试点输入数据")
    expected_output: str = Field(description="评测机期望的标准正确输出")
    user_output: str = Field(description="学生代码实际打印的错误输出")

class AnalyzeRequest(BaseModel):
    """前端/Java 后端发来的诊断请求体"""
    submission_id: int = Field(description="本次提交记录ID")
    user_id: int = Field(description="提交学生用户ID")
    problem_id: int = Field(description="题目ID")
    problem_title: str = Field(description="题目标题")
    problem_description: str = Field(description="题目Markdown描述")
    language: str = Field(default="cpp", description="代码语言，如 cpp, java, python")
    user_code: str = Field(description="学生提交的源代码")
    judge_status: str = Field(description="评测结果状态，如 Wrong Answer, Compile Error")
    failed_test_case: FailedTestCase | None = Field(default=None, description="未通过测试点详情")
    # 👇【新增字段】：Spring Boot 可直接从 MySQL 传入本题出题人题解（若无则默认为 None）
    official_editorial: str | None = Field(default=None, description="出题人官方题解或解题思路")

# ==================== 2. 出参契约 (Python ➔ 前端力扣卡片) ====================

class MethodAnalysis(BaseModel):
    current_method: str = Field(description="当前代码使用的方法，例如：深度优先搜索 / 模拟")
    suggested_method: str = Field(description="推荐的最优方法，例如：广度优先搜索 / 贪心")
    core_concept: str = Field(description="本题核心考察的算法思想")
    thinking_prompt: str = Field(description="苏格拉底式引导思考题，启发学生自行纠错")

class EfficiencyAnalysis(BaseModel):
    current_time_complexity: str = Field(description="当前代码的时间复杂度，例如：O(n^2)")
    suggested_time_complexity: str = Field(description="最优时间复杂度，例如：O(n log n)")
    optimization_advice: str = Field(description="具体的性能优化建议")

class StyleAnalysis(BaseModel):
    readability: str = Field(description="可读性评级：优秀 / 良好 / 需改进")
    structure: str = Field(description="代码结构评级：优秀 / 良好 / 需改进")
    advice: str = Field(description="代码风格改进建议，如变量命名、避免全局变量等")

class OJCodeAnalysisReport(BaseModel):
    """最终返回的完整力扣风格评审报告"""
    congratulation: str = Field(description="一句话鼓励语或总评")
    method: MethodAnalysis
    efficiency: EfficiencyAnalysis
    style: StyleAnalysis