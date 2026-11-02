from fastapi import FastAPI, Header, HTTPException
from app.schemas import AnalyzeRequest, OJCodeAnalysisReport
from app.agent import run_code_analysis

app = FastAPI(
    title="Core OJ - AI Code Mentor API",
    description="针对 OJ 提交代码的力扣同款智能诊断微服务",
    version="1.0.0"
)

@app.get("/health")
def health_check():
    """供 Docker 和 Spring Boot 探活的健康检查接口"""
    return {"status": "healthy", "service": "oj-agent"}

@app.post("/api/v1/analyze", response_model=OJCodeAnalysisReport)
def analyze_code(
    req: AnalyzeRequest,
    x_custom_api_key: str | None = Header(default=None, alias="X-Custom-Api-Key")
):
    """核心诊断接口：接收代码与报错信息，返回力扣同款结构化分析报告"""
    try:
        # 执行分析（带 BYOK 机制）
        report = run_code_analysis(req, custom_api_key=x_custom_api_key)
        return report
    except Exception as e:
        print(f"❌ 诊断处理异常: {e}")
        raise HTTPException(status_code=500, detail=str(e))