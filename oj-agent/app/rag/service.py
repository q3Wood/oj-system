from app.schemas import AnalyzeRequest
from app.rag.store import knowledge_collection


def retrieve_oj_knowledge(req: AnalyzeRequest) -> str | None:
    """结合评测状态、报错日志与核心代码段，在竞赛避坑库中精准打捞底层陷阱"""
    retrieved_pieces = []

    try:
        # 1. 若后端未直接传题解，尝试从 ChromaDB 中按 problem_id 精确捞取官方题解
        if not req.official_editorial:
            editorial_res = knowledge_collection.get(
                where={
                    "$and": [
                        {"type": {"$eq": "editorial"}},
                        {"problem_id": {"$eq": req.problem_id}},
                    ]
                }
            )
            ed_docs = editorial_res.get("documents")
            if ed_docs and len(ed_docs) > 0:
                retrieved_pieces.append(ed_docs[0])

        # 2. 构造多特征混合 Query（避开代码开头的 #include 模板，取后 800 字符核心逻辑）
        core_code_snippet = req.user_code[-800:] if len(req.user_code) > 800 else req.user_code
        query_parts = [
            f"评测状态: {req.judge_status}",
            f"题目: {req.problem_title}",
        ]
        if req.error_message:
            query_parts.append(f"报错信息: {req.error_message[:200]}")
        query_parts.append(f"代码片段:\n{core_code_snippet}")
        query_str = "\n".join(query_parts)

        pitfall_results = knowledge_collection.query(
            query_texts=[query_str],
            n_results=1,
            where={"type": "pitfall"},
        )

        docs = pitfall_results.get("documents")
        distances = pitfall_results.get("distances")

        # 距离阈值过滤：只有距离 < 1.35（语义确实相关）才注入，防止无关避坑指南误导大模型
        if docs and len(docs) > 0 and len(docs[0]) > 0:
            best_doc = docs[0][0]
            best_dist = distances[0][0] if (distances and len(distances) > 0 and len(distances[0]) > 0) else 0.0
            if best_dist < 1.35:
                retrieved_pieces.append(best_doc)

    except Exception as e:
        print(f"⚠️ 避坑条款检索异常: {e}")

    if not retrieved_pieces:
        return None

    return "\n\n".join(retrieved_pieces)