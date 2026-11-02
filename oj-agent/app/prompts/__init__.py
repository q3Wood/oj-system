from langchain_core.messages import SystemMessage, HumanMessage, AIMessage
from app.schemas import AnalyzeRequest
from .system_prompt import COACH_SYSTEM_PROMPT
from .few_shots import FEW_SHOT_EXAMPLES
from .templates import format_user_prompt

def build_analysis_messages(req: AnalyzeRequest, editorial_context: str | None = None) -> list:
    """构建用于代码评审的完整消息链（包含系统军规、Few-Shot 范例和当前上下文）"""
    messages = []
    
    # 1. 注入核心系统人设与法典
    messages.append(SystemMessage(content=COACH_SYSTEM_PROMPT))
    
    # 2. 注入 Few-Shot 少样本示范（立规矩）
    for example in FEW_SHOT_EXAMPLES:
        messages.append(HumanMessage(content=example["user"]))
        messages.append(AIMessage(content=example["assistant"]))
        
    # 3. 注入当次提交的动态真实数据
    user_content = format_user_prompt(req, editorial_context=editorial_context)
    messages.append(HumanMessage(content=user_content))
    
    return messages

__all__ = ["build_analysis_messages"]