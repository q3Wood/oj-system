import re
from pydantic import SecretStr
from langchain_openai import ChatOpenAI

from app.config import settings
from app.schemas import AnalyzeRequest, OJCodeAnalysisReport
from app.prompts import build_analysis_messages
from app.rag import retrieve_oj_knowledge


def _clean_json_text(raw_text: str) -> str:
    """剥离大模型偶发输出的 Markdown 代码块包裹，防止 JSON 解析崩溃"""
    text = raw_text.strip()
    match = re.search(r"\{.*\}", text, re.DOTALL)
    return match.group(0) if match else text


async def run_code_analysis(req: AnalyzeRequest, custom_api_key: str | None = None) -> OJCodeAnalysisReport:
    """终极异步流水线：融合 RAG 检索、分层提示词与大模型结构化推理"""

    # 1. 动态确定使用的 Key（支持用户自带 Key 模式）
    actual_key = custom_api_key if custom_api_key else settings.deepseek_api_key
    if not actual_key:
        raise ValueError("未检测到有效的 DeepSeek API Key，请检查配置或传入自定义密钥！")

    # 2. 动态实例化模型（加上 timeout 防止网络异常导致协程永久挂起）
    llm = ChatOpenAI(
        model="deepseek-chat",
        api_key=SecretStr(actual_key),
        base_url=settings.deepseek_base_url,
        temperature=0.2,
        timeout=30.0,
        max_retries=2,
    )

    # 3. 核心 RAG 知识融合
    knowledge_blocks = []

    if req.official_editorial:
        knowledge_blocks.append(f"【本题出题人思路】:\n{req.official_editorial}")

    pitfall_info = retrieve_oj_knowledge(req)
    if pitfall_info:
        knowledge_blocks.append(f"【关联题解与底层避坑指南】:\n{pitfall_info}")

    combined_editorial = "\n\n".join(knowledge_blocks) if knowledge_blocks else None

    # 4. 提示词工程组装
    messages = build_analysis_messages(req, editorial_context=combined_editorial)

    # 5. 大模型异步非阻塞推理 (ainvoke)
    response = await llm.ainvoke(
        messages,
        response_format={"type": "json_object"},
    )

    raw_content = response.content
    raw_text = "".join([str(c) for c in raw_content]) if isinstance(raw_content, list) else str(raw_content)

    # 6. JSON 清洗兜底 + Pydantic 数据安检
    clean_text = _clean_json_text(raw_text)
    report = OJCodeAnalysisReport.model_validate_json(clean_text)
    return report