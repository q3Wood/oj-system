from pydantic import SecretStr
from langchain_openai import ChatOpenAI

from app.config import settings
from app.schemas import AnalyzeRequest, OJCodeAnalysisReport
from app.prompts import build_analysis_messages
from app.rag import retrieve_oj_knowledge

def run_code_analysis(req: AnalyzeRequest, custom_api_key: str | None = None) -> OJCodeAnalysisReport:
    """终极流水线：融合 RAG 检索、分层提示词与大模型结构化推理"""

    # 1. 动态确定使用的 Key（支持用户自带 Key 模式）
    actual_key = custom_api_key if custom_api_key else settings.deepseek_api_key
    if not actual_key:
        raise ValueError("未检测到有效的 DeepSeek API Key，请检查配置或传入自定义密钥！")

    # 2. 动态实例化模型
    llm = ChatOpenAI(
        model="deepseek-chat",
        api_key=SecretStr(actual_key),
        base_url=settings.deepseek_base_url
    )

    # ==================== 核心 RAG 知识融合 ====================
    # 结合两方面的资料：
    # ① Spring Boot 直接带来的本题官方题解（若有）
    # ② ChromaDB 针对该代码语义检索出的通用算法/语言底层避坑宝典（若有）
    knowledge_blocks = []
    
    if req.official_editorial:
        knowledge_blocks.append(f"【本题出题人思路】:\n{req.official_editorial}")
        
    pitfall_info = retrieve_oj_knowledge(req.user_code)
    if pitfall_info:
        knowledge_blocks.append(f"【通用算法与底层避坑指南】:\n{pitfall_info}")

    # 合并作为开卷资料，若完全没有则为 None（优雅降级）
    combined_editorial = "\n\n".join(knowledge_blocks) if knowledge_blocks else None

    # ==================== 提示词工程组装 ====================
    # 自动加载 System 人设、Few-Shot 少样本示范和格式法典
    messages = build_analysis_messages(req, editorial_context=combined_editorial)

    # ==================== 大模型结构化推理 ====================
    response = llm.invoke(
        messages,
        response_format={"type": "json_object"}
    )

    raw_content = response.content
    raw_text = "".join([str(c) for c in raw_content]) if isinstance(raw_content, list) else str(raw_content)

    # 4. Pydantic 数据安检与实例化
    report = OJCodeAnalysisReport.model_validate_json(raw_text)
    return report